package com.windasoft.eam.inventory;

import static com.windasoft.eam.inventory.InventoryService.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.equipment.NumberCounterMapper;
import com.windasoft.eam.inspection.InspectionService;
import com.windasoft.eam.maintenance.MaintenanceService;
import com.windasoft.eam.repair.*;
import com.windasoft.eam.system.*;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequisitionService {
  @org.springframework.beans.factory.annotation.Autowired private InventoryMutex mutex;

  @org.springframework.beans.factory.annotation.Autowired
  @org.springframework.context.annotation.Lazy
  private OutboundService outbounds;

  private final JdbcTemplate db;
  private final InventoryService masters;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final NumberCounterMapper counter;
  private final RepairOrderMapper repairs;
  private final MaintenanceService maintenance;
  private final InspectionService inspection;
  private final ObjectMapper json;

  public RequisitionService(
      JdbcTemplate db,
      InventoryService masters,
      AuthContext auth,
      PermissionService permissions,
      NumberCounterMapper counter,
      RepairOrderMapper repairs,
      MaintenanceService maintenance,
      InspectionService inspection,
      ObjectMapper json) {
    this.db = db;
    this.masters = masters;
    this.auth = auth;
    this.permissions = permissions;
    this.counter = counter;
    this.repairs = repairs;
    this.maintenance = maintenance;
    this.inspection = inspection;
    this.json = json;
  }

  private boolean active(String status) {
    return !Set.of("待验收", "待审核", "已完成", "已验收", "已审核", "已作废", "过期作废", "已取消", "作废", "完成", "已关闭")
        .contains(status);
  }

  private record Task(long company, String code, String status) {}

  private Task task(String kind, long taskId, boolean checkAccess, boolean lock) {
    if (kind.equals("维修")) {
      // Lock the source task before creating or reserving against it.
      var found =
          db.queryForList(
              "SELECT id,department_org_id,order_no,status FROM eam_repair_order WHERE tenant_id=?"
                  + " AND id=? AND deleted=0"
                  + (lock ? " FOR UPDATE" : ""),
              auth.tenantId(),
              taskId);
      require(found.size() == 1, "维修工单不存在");
      var raw = found.getFirst();
      if (checkAccess) permissions.checkRepair(repairs.selectById(taskId), "查看");
      Long company = permissions.companyOfOrganization(id(raw.get("department_org_id")));
      require(company != null, "工单所属公司尚未明确");
      return new Task(company, text(raw, "order_no"), text(raw, "status"));
    }
    require(Set.of("保养", "点巡检").contains(kind), "任务类型无效");
    String table = kind.equals("保养") ? "eam_maintenance_task" : "eam_inspection_task";
    var found =
        db.queryForList(
            "SELECT * FROM "
                + table
                + " WHERE tenant_id=? AND id=?"
                + (kind.equals("点巡检") ? " AND deleted=0" : "")
                + (lock ? " FOR UPDATE" : ""),
            auth.tenantId(),
            taskId);
    require(found.size() == 1, "任务不存在");
    if (checkAccess) {
      if (kind.equals("保养")) maintenance.task(taskId);
      else inspection.task(taskId);
    }
    var raw = found.getFirst();
    require(raw.get("company_id") != null, "任务所属公司尚未明确");
    return new Task(id(raw.get("company_id")), text(raw, "code"), text(raw, "status"));
  }

  private Map<String, Object> row(long requestId, boolean lock, String action) {
    var rows =
        db.queryForList(
            "SELECT * FROM inv_requisition WHERE tenant_id=? AND id=? AND deleted=0"
                + (lock ? " FOR UPDATE" : ""),
            auth.tenantId(),
            requestId);
    require(!rows.isEmpty(), "领料申请不存在");
    var raw = rows.getFirst();
    if (!(id(raw.get("applicant_id")) == auth.userId()
        && Set.of("查看", "新增", "编辑").contains(action))) {
      if (Set.of("出库", "预留", "释放预留", "确认出库", "关闭剩余需求").contains(action))
        permissions.checkInventoryWarehouse(
            "inventoryOutbound",
            id(raw.get("warehouse_id")),
            action.equals("出库") ? "确认出库" : action);
      else if (action.equals("查看")
          && permissions.inventoryWarehouseAllowed(
              "inventoryOutbound", id(raw.get("warehouse_id")), "查看")) {
      } else
        permissions.checkInventoryWarehouse(
            "inventoryRequisition", id(raw.get("warehouse_id")), action);
    }
    return raw;
  }

  private void notifyWarehouse(long requestId) {
    var r = row(requestId, false, "新增");
    var warehouse = masters.row("warehouses", id(r.get("warehouse_id")), false);

    var recipients =
        db
            .queryForList(
                "SELECT id FROM eam_sys_user WHERE tenant_id=? AND status=1 AND deleted=0",
                auth.tenantId())
            .stream()
            .map(v -> id(v.get("id")))
            .filter(
                user ->
                    permissions.inventoryWarehouseUser(
                            user, "inventoryOutbound", id(warehouse.get("id")), "确认出库")
                        && permissions.inventoryWarehouseUser(
                            user, "inventoryOutbound", id(warehouse.get("id")), "查看"))
            .toList();

    for (long recipient : recipients)
      db.update(
          "INSERT INTO inv_inbox(id,tenant_id,user_id,document_id,route_kind,kind,title,is_todo)"
              + " VALUES(?,?,?,?,'requisitions','待出库',?,1)",
          IdWorker.getId(),
          auth.tenantId(),
          recipient,
          requestId,
          text(r, "code") + " · " + text(r, "task_code"));
  }

  private void closeWarehouseTodos(long requestId) {
    db.update(
        "UPDATE inv_inbox SET done=1 WHERE tenant_id=? AND document_id=? AND"
            + " route_kind='requisitions' AND is_todo=1",
        auth.tenantId(),
        requestId);
  }

  void taskEvent(Map<String, Object> request, String action) {
    String kind = text(request, "task_kind");
    long taskId = id(request.get("task_id"));
    if (kind.equals("维修"))
      db.update(
          "INSERT INTO"
              + " eam_repair_event(id,tenant_id,order_id,action,actor_id,actor_name,details_json,created_at)"
              + " VALUES(?,?,?,?,?,?,JSON_OBJECT(),NOW())",
          IdWorker.getId(),
          auth.tenantId(),
          taskId,
          action,
          auth.userId(),
          auth.user().getDisplayName());
    else {
      String table = kind.equals("保养") ? "eam_maintenance_event" : "eam_inspection_event";
      db.update(
          "INSERT INTO "
              + table
              + "(id,tenant_id,task_id,actor_id,actor_name,action,details)"
              + " VALUES(?,?,?,?,?,?,JSON_OBJECT())",
          IdWorker.getId(),
          auth.tenantId(),
          taskId,
          auth.userId(),
          auth.user().getDisplayName(),
          action);
    }
  }

  private void event(long requestId, String action) {
    db.update(
        "INSERT INTO"
            + " inv_document_event(id,tenant_id,document_kind,document_id,action,actor_id,actor_name)"
            + " VALUES(?,?,'领料',?,?,?,?)",
        IdWorker.getId(),
        auth.tenantId(),
        requestId,
        action,
        auth.userId(),
        auth.user().getDisplayName());
  }

  private String number() {
    String month =
        LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMM"));
    String code;
    do {
      counter.nextMonthlyNumber("inv-LL-" + auth.tenantId(), month);
      code = "LL-" + month + "-" + String.format("%04d", counter.currentValue());
    } while (db.queryForObject(
            "SELECT COUNT(*) FROM inv_requisition WHERE tenant_id=? AND code=?",
            Integer.class,
            auth.tenantId(),
            code)
        > 0);
    return code;
  }

  public Map<String, Object> detail(long requestId) {
    var rendered = masters.view(row(requestId, false, "查看"));
    rendered.put(
        "warehouseName",
        masters.row("warehouses", id(rendered.get("warehouseId")), false).get("name"));
    rendered.put(
        "lines",
        db
            .queryForList(
                "SELECT l.*,m.code material_code,m.name material_name,m.unit,COALESCE((SELECT"
                    + " SUM(r.remaining_quantity) FROM inv_reservation r WHERE"
                    + " r.tenant_id=l.tenant_id AND r.requisition_line_id=l.id),0)"
                    + " reserved_quantity FROM inv_requisition_line l JOIN inv_material m ON"
                    + " m.tenant_id=l.tenant_id AND m.id=l.material_id WHERE l.tenant_id=? AND"
                    + " l.requisition_id=? ORDER BY l.line_no",
                auth.tenantId(),
                requestId)
            .stream()
            .map(masters::view)
            .toList());
    rendered.put(
        "reservations",
        db
            .queryForList(
                "SELECT r.*,m.name material_name,b.name bin_name,pc.code batch_code FROM"
                    + " inv_reservation r JOIN inv_balance s ON s.tenant_id=r.tenant_id AND"
                    + " s.id=r.balance_id JOIN inv_material m ON m.tenant_id=s.tenant_id AND"
                    + " m.id=s.material_id JOIN inv_bin b ON b.tenant_id=s.tenant_id AND"
                    + " b.id=s.bin_id LEFT JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND"
                    + " pc.id=s.batch_id WHERE r.tenant_id=? AND r.requisition_id=? AND"
                    + " r.remaining_quantity>0 ORDER BY r.created_at,r.id",
                auth.tenantId(),
                requestId)
            .stream()
            .map(masters::view)
            .toList());
    rendered.put(
        "issues",
        db
            .queryForList(
                "SELECT i.*,g.code group_code FROM inv_issue i LEFT JOIN inv_issue_group g ON"
                    + " g.tenant_id=i.tenant_id AND g.id=i.group_id WHERE i.tenant_id=? AND"
                    + " (i.requisition_id=? OR EXISTS(SELECT 1 FROM inv_issue_allocation a JOIN"
                    + " inv_requisition_line l ON l.tenant_id=a.tenant_id AND"
                    + " l.id=a.requisition_line_id WHERE a.tenant_id=i.tenant_id AND"
                    + " a.issue_id=i.id AND l.requisition_id="
                    + requestId
                    + ")) ORDER BY i.created_at,i.id",
                auth.tenantId(),
                requestId)
            .stream()
            .map(
                issue -> {
                  var v = masters.view(issue);
                  v.put(
                      "allocations",
                      db
                          .queryForList(
                              "SELECT a.*,m.name material_name,b.name bin_name,pc.code batch_code"
                                  + " FROM inv_issue_allocation a JOIN inv_balance s ON"
                                  + " s.tenant_id=a.tenant_id AND s.id=a.balance_id JOIN"
                                  + " inv_material m ON m.tenant_id=s.tenant_id AND"
                                  + " m.id=s.material_id JOIN inv_bin b ON b.tenant_id=s.tenant_id"
                                  + " AND b.id=s.bin_id LEFT JOIN inv_batch pc ON"
                                  + " pc.tenant_id=s.tenant_id AND pc.id=s.batch_id WHERE"
                                  + " a.tenant_id=? AND a.issue_id=? AND a.requisition_line_id IN"
                                  + " (SELECT id FROM inv_requisition_line WHERE tenant_id="
                                  + auth.tenantId()
                                  + " AND requisition_id="
                                  + requestId
                                  + ") ORDER BY a.id",
                              auth.tenantId(),
                              issue.get("id"))
                          .stream()
                          .map(masters::view)
                          .toList());
                  @SuppressWarnings("unchecked")
                  var visibleAllocations = (List<Map<String, Object>>) v.get("allocations");
                  v.put(
                      "totalAmount",
                      visibleAllocations.stream()
                          .map(a -> new BigDecimal(text(a, "amount")))
                          .reduce(BigDecimal.ZERO, BigDecimal::add)
                          .toPlainString());
                  return v;
                })
            .toList());
    rendered.put(
        "events",
        db
            .queryForList(
                "SELECT"
                    + " action,actor_name,created_at,JSON_UNQUOTE(JSON_EXTRACT(details_json,'$.reason'))"
                    + " reason FROM inv_document_event WHERE tenant_id=? AND document_kind='领料' AND"
                    + " document_id=? ORDER BY created_at,id",
                auth.tenantId(),
                requestId)
            .stream()
            .map(masters::view)
            .toList());
    if (!permissions.inventoryWarehouseAllowed(
            "inventoryOutbound", id(rendered.get("warehouseId")), "查看金额")
        && !permissions.inventoryWarehouseAllowed(
            "inventoryRequisition", id(rendered.get("warehouseId")), "查看金额"))
      InventoryMoney.hide(rendered);
    return rendered;
  }

  private List<Map<String, Object>> matching(String q, String filterJson) {
    require(q.length() <= 200 && filterJson.length() <= 4000, "查询条件过长");
    Map<String, String> filters;
    try {
      filters =
          json.readValue(
              filterJson,
              new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("筛选条件无效");
    }
    require(
        filters != null
            && Set.of(
                    "code",
                    "warehouseName",
                    "taskKind",
                    "taskCode",
                    "applicantName",
                    "status",
                    "createdAt",
                    "pendingOut",
                    "reservationStatus",
                    "remainingQuantity")
                .containsAll(filters.keySet()),
        "筛选字段无效");
    var result = new ArrayList<Map<String, Object>>();
    for (var raw :
        db.queryForList(
            "SELECT r.*,DATE_FORMAT(r.created_at,'%Y-%m-%d %H:%i:%s') fmt_created_at,w.name"
                + " warehouse_name FROM inv_requisition r JOIN inv_warehouse w ON"
                + " w.tenant_id=r.tenant_id AND w.id=r.warehouse_id WHERE r.tenant_id=? AND"
                + " r.deleted=0 ORDER BY r.created_at DESC,r.id DESC",
            auth.tenantId())) {
      try {
        if (id(raw.get("applicant_id")) != auth.userId()) row(id(raw.get("id")), false, "查看");
      } catch (ResponseStatusException e) {
        if (e.getStatusCode().value() == 403) continue;
        throw e;
      }
      if ("1".equals(filters.get("pendingOut"))
          && !Set.of("待预留", "部分预留", "已预留", "部分发料").contains(text(raw, "status"))) continue;
      var v = masters.view(raw);
      v.put("status", businessStatus(text(raw, "status")));
      if (Set.of("待预留", "部分预留", "已预留", "部分发料").contains(text(raw, "status"))
          && db.queryForObject(
                      "SELECT COALESCE(SUM(issued_quantity),0) FROM inv_requisition_line WHERE"
                          + " tenant_id=? AND requisition_id=?",
                      BigDecimal.class,
                      auth.tenantId(),
                      raw.get("id"))
                  .signum()
              > 0) v.put("status", "部分出库");
      v.put("reservationStatus", reservationStatus(id(raw.get("id"))));
      v.put(
          "remainingQuantity",
          db.queryForObject(
              "SELECT COALESCE(SUM(quantity-issued_quantity),0) FROM inv_requisition_line WHERE"
                  + " tenant_id=? AND requisition_id=?",
              BigDecimal.class,
              auth.tenantId(),
              raw.get("id")));
      if (!(text(v, "code")
              + " "
              + text(v, "taskCode")
              + " "
              + text(v, "applicantName")
              + " "
              + text(v, "warehouseName"))
          .toLowerCase(Locale.ROOT)
          .contains(q.trim().toLowerCase(Locale.ROOT))) continue;
      if (filters.entrySet().stream()
          .allMatch(
              c ->
                  (c.getKey().equals("pendingOut") ? "1" : text(v, c.getKey()))
                      .replace('T', ' ')
                      .contains(Objects.toString(c.getValue(), "").trim()))) result.add(v);
    }
    return result;
  }

  public Object list(String q, String filters, int page, int size) {
    require(page >= 1 && size >= 1 && size <= 100, "分页参数无效");
    var rows = matching(q, filters);
    int from = (int) Math.min(((long) page - 1) * size, rows.size());
    return Map.of(
        "data",
        rows.subList(from, Math.min(from + size, rows.size())),
        "page",
        Map.of("total", rows.size()));
  }

  public Object export(String q, String filters) {
    permissions.require("inventoryRequisition", "导出");
    return matching(q, filters).stream()
        .filter(
            v ->
                permissions.inventoryWarehouseAllowed(
                    "inventoryRequisition", id(v.get("warehouseId")), "导出"))
        .toList();
  }

  private void checkTaskApplicant(String kind, long taskId) {
    if (kind.equals("维修")) {
      var order = repairs.selectById(taskId);
      require(
          order != null
              && order.getTenantId() == auth.tenantId()
              && order.getDeleted() == 0
              && Objects.equals(order.getAssigneeUserId(), auth.userId())
              && Set.of("处理中", "挂起").contains(order.getStatus()),
          "仅当前维修人可以发起领料申请");
      permissions.checkRepair(order, "查看");
    } else {
      var view = kind.equals("保养") ? maintenance.task(taskId) : inspection.task(taskId);
      require(
          Boolean.TRUE.equals(view.get("canExecute"))
              && Set.of("执行中", "挂起").contains(text(view, "status")),
          "仅当前任务执行人可以申请领料");
    }
  }

  public Object choices() {
    @SuppressWarnings("unchecked")
    var result = new LinkedHashMap<String, Object>();
    if (permissions.menuActions().entrySet().stream()
        .anyMatch(e -> e.getKey().startsWith("inventory") && e.getValue().contains("查看")))
      result.putAll((Map<String, Object>) masters.stockChoices());
    else {
      result.put(
          "materials",
          db
              .queryForList(
                  "SELECT id,code,name,model,unit,category,usage_type,batch_enabled,expiry_enabled"
                      + " FROM inv_material WHERE tenant_id=? AND enabled=1 ORDER BY code",
                  auth.tenantId())
              .stream()
              .map(masters::view)
              .toList());
      result.put(
          "warehouses",
          db
              .queryForList(
                  "SELECT id,name,company_id FROM inv_warehouse WHERE tenant_id=? AND enabled=1"
                      + " ORDER BY name",
                  auth.tenantId())
              .stream()
              .map(masters::view)
              .toList());
      result.put("companies", List.of());
      result.put("users", List.of());
      result.put("bins", List.of());
    }
    var tasks = new ArrayList<Map<String, Object>>();
    for (var r :
        repairs.selectList(
            new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getTenantId, auth.tenantId())
                .eq(RepairOrder::getDeleted, 0))) {
      if (!active(r.getStatus())) continue;
      try {
        permissions.checkRepair(r, "查看");
        var t = task("维修", r.getId(), false, false);
        tasks.add(
            Map.of(
                "id",
                r.getId().toString(),
                "kind",
                "维修",
                "code",
                r.getOrderNo(),
                "companyId",
                Long.toString(t.company()),
                "name",
                r.getEquipmentName()));
      } catch (ResponseStatusException e) {
        if (e.getStatusCode().value() != 403) throw e;
      } catch (IllegalArgumentException unavailable) {
        /* Tasks without a known company cannot receive warehouse material. */
      }
    }
    for (String kind : List.of("保养", "点巡检"))
      for (var r :
          kind.equals("保养")
              ? (permissions.allowed("maintenanceTask", "查看")
                  ? maintenance.tasks()
                  : List.<Map<String, Object>>of())
              : ((permissions.allowed("inspectionDailyTask", "查看")
                      || permissions.allowed("inspectionPeriodicTask", "查看"))
                  ? inspection.tasks()
                  : List.<Map<String, Object>>of())) {
        if (!active(text(r, "status"))) continue;
        Task t;
        try {
          t = task(kind, id(r.get("id")), false, false);
        } catch (IllegalArgumentException unavailable) {
          continue;
        }
        tasks.add(
            Map.of(
                "id",
                r.get("id"),
                "kind",
                kind,
                "code",
                t.code(),
                "companyId",
                Long.toString(t.company()),
                "name",
                text(r, "equipmentName")));
      }
    if (!permissions.allowed("inventoryRequisition", "新增"))
      result.put(
          "warehouses",
          db
              .queryForList(
                  "SELECT id,name,company_id FROM inv_warehouse WHERE tenant_id=? AND enabled=1"
                      + " ORDER BY name",
                  auth.tenantId())
              .stream()
              .map(masters::view)
              .toList());
    @SuppressWarnings("unchecked")
    var warehouses = (List<Map<String, Object>>) result.get("warehouses");
    tasks.removeIf(
        t ->
            warehouses.stream()
                .noneMatch(w -> Objects.equals(text(w, "companyId"), text(t, "companyId"))));
    if (!permissions.allowed("inventoryRequisition", "新增"))
      tasks.removeIf(
          t -> {
            try {
              checkTaskApplicant(text(t, "kind"), id(t.get("id")));
              return false;
            } catch (RuntimeException denied) {
              return true;
            }
          });
    if (!permissions.allowed("inventoryRequisition", "新增")
        || !permissions.allowed("inventoryRequisition", "查看"))
      result.put(
          "warehouses",
          warehouses.stream()
              .filter(
                  w ->
                      tasks.stream()
                          .anyMatch(t -> text(t, "companyId").equals(text(w, "companyId"))))
              .toList());
    result.put("tasks", tasks);
    return result;
  }

  private BigDecimal issueQuantity(Object value) {
    try {
      var qty = new BigDecimal(Objects.toString(value, ""));
      require(
          qty.signum() >= 0 && qty.scale() <= 6 && qty.precision() - qty.scale() <= 14,
          "发料数量必须非负，最多6位小数");
      return qty;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("发料数量格式无效");
    }
  }

  public Map<String, Object> previewIssue(long requestId, Map<String, Object> body) {
    return outbounds.legacyPreview(requestId, body);
  }

  @Transactional
  public Map<String, Object> confirmIssue(long requestId, Map<String, Object> body) {
    outbounds.legacyConfirm(requestId, body);
    return detail(requestId);
  }

  @Transactional
  public Map<String, Object> save(Long requestId, Map<String, Object> body) {
    mutex.enter();
    var previous = requestId == null ? null : row(requestId, true, "新增");
    if (previous != null) {
      require(id(previous.get("applicant_id")) == auth.userId(), "仅申请人可以修改草稿");
      require(text(previous, "status").equals("草稿"), "已提交申请不能修改");
      VersionGuard.checkPayload((Number) previous.get("row_version"), body);
    }
    var t = task(text(body, "taskKind"), id(body.get("taskId")), true, true);
    require(active(t.status()), "已结束任务不能申请领料");
    var warehouse = masters.row("warehouses", id(body.get("warehouseId")), true);
    if (!permissions.allowed("inventoryRequisition", "新增"))
      checkTaskApplicant(text(body, "taskKind"), id(body.get("taskId")));
    else permissions.checkInventoryWarehouse("inventoryRequisition", id(warehouse.get("id")), "新增");
    require(
        flag(warehouse.get("enabled")) && id(warehouse.get("company_id")) == t.company(),
        "请选择任务所属公司的有效仓库");
    require(text(body, "remark").length() <= 1000, "备注过长");
    require(body.get("lines") instanceof List<?>, "请添加领料明细");
    var input = (List<?>) body.get("lines");
    require(!input.isEmpty() && input.size() <= 500, "领料明细须为1至500行");
    var orderedMaterials = new TreeSet<Long>();
    for (Object item : input) {
      require(item instanceof Map<?, ?>, "明细格式无效");
      orderedMaterials.add(id(((Map<?, ?>) item).get("materialId")));
    }
    for (long materialId : orderedMaterials) masters.row("materials", materialId, true);
    long target = requestId == null ? IdWorker.getId() : requestId;
    if (previous == null)
      db.update(
          "INSERT INTO"
              + " inv_requisition(id,tenant_id,code,company_id,warehouse_id,task_kind,task_id,task_code,applicant_id,applicant_name,remark)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
          target,
          auth.tenantId(),
          number(),
          t.company(),
          warehouse.get("id"),
          text(body, "taskKind"),
          id(body.get("taskId")),
          t.code(),
          auth.userId(),
          auth.user().getDisplayName(),
          text(body, "remark"));
    else {
      db.update(
          "UPDATE inv_requisition SET"
              + " company_id=?,warehouse_id=?,task_kind=?,task_id=?,task_code=?,remark=?,row_version=row_version+1"
              + " WHERE tenant_id=? AND id=?",
          t.company(),
          warehouse.get("id"),
          text(body, "taskKind"),
          id(body.get("taskId")),
          t.code(),
          text(body, "remark"),
          auth.tenantId(),
          target);
      db.update(
          "DELETE FROM inv_requisition_line WHERE tenant_id=? AND requisition_id=?",
          auth.tenantId(),
          target);
    }
    var materials = new HashSet<Long>();
    int lineNo = 0;
    for (Object item : input) {
      require(item instanceof Map<?, ?>, "明细格式无效");
      @SuppressWarnings("unchecked")
      var line = (Map<String, Object>) item;
      long materialId = id(line.get("materialId"));
      require(materials.add(materialId), "同一物料请合并到一行");
      var material = masters.row("materials", materialId, true);
      require(flag(material.get("enabled")), "物料已停用");
      BigDecimal qty;
      try {
        qty = new BigDecimal(text(line, "quantity"));
      } catch (Exception e) {
        throw new IllegalArgumentException("领料数量格式无效");
      }
      require(
          qty.signum() > 0 && qty.scale() <= 6 && qty.precision() - qty.scale() <= 14,
          "数量须大于零，最多6位小数");
      db.update(
          "INSERT INTO"
              + " inv_requisition_line(id,tenant_id,requisition_id,line_no,material_id,quantity)"
              + " VALUES(?,?,?,?,?,?)",
          IdWorker.getId(),
          auth.tenantId(),
          target,
          ++lineNo,
          materialId,
          qty);
    }
    event(target, previous == null ? "创建草稿" : "修改草稿");
    return detail(target);
  }

  public Object mergedPreview(Map<String, Object> body) {
    return outbounds.legacyMergedPreview(body);
  }

  public Object mergedConfirm(Map<String, Object> body) {
    return outbounds.legacyMergedConfirm(body);
  }

  @Transactional
  public Map<String, Object> submit(long requestId, Map<String, Object> body) {
    mutex.enter();
    var raw = row(requestId, true, "新增");
    require(id(raw.get("applicant_id")) == auth.userId(), "仅申请人可以提交");
    if (!text(raw, "status").equals("草稿")) {
      require(Set.of("待预留", "部分预留", "已预留", "部分发料", "已完成").contains(text(raw, "status")), "申请不能提交");
      return detail(requestId);
    }
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    var t = task(text(raw, "task_kind"), id(raw.get("task_id")), true, true);
    require(active(t.status()), "任务已结束");
    db.update(
        "UPDATE inv_requisition SET status=IF(EXISTS(SELECT 1 FROM inv_requisition_line l WHERE"
            + " l.tenant_id=inv_requisition.tenant_id AND l.requisition_id=inv_requisition.id AND"
            + " l.issued_quantity>0),'部分发料','待预留'),row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        auth.tenantId(),
        requestId);
    event(requestId, "提交申请");
    taskEvent(raw, "领料申请 · " + text(raw, "code"));
    notifyWarehouse(requestId);
    return detail(requestId);
  }

  public Object stockPool(long requestId) {
    var raw = row(requestId, false, "出库");
    return db
        .queryForList(
            "SELECT s.id balance_id,l.id line_id,m.name material_name,b.name bin_name,pc.code"
                + " batch_code,pc.expiry_date,s.quantity-s.reserved_quantity available_quantity"
                + " FROM inv_requisition_line l JOIN inv_material m ON m.tenant_id=l.tenant_id AND"
                + " m.id=l.material_id AND m.enabled=1 JOIN inv_balance s ON"
                + " s.tenant_id=l.tenant_id AND s.material_id=l.material_id JOIN inv_bin b ON"
                + " b.tenant_id=s.tenant_id AND b.id=s.bin_id AND b.enabled=1 LEFT JOIN inv_batch"
                + " pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id WHERE l.tenant_id=? AND"
                + " l.requisition_id=? AND s.warehouse_id=? AND s.stock_status='可用' AND"
                + " s.quantity>s.reserved_quantity AND (pc.expiry_date IS NULL OR"
                + " pc.expiry_date>=?) AND (m.expiry_enabled=0 OR pc.expiry_date IS NOT NULL) ORDER"
                + " BY m.code,b.code,pc.code",
            auth.tenantId(),
            requestId,
            raw.get("warehouse_id"),
            LocalDate.now(ZoneId.of("Asia/Shanghai")))
        .stream()
        .map(masters::view)
        .toList();
  }

  @Transactional
  public Map<String, Object> reserve(long requestId, Map<String, Object> body) {
    mutex.enter();
    var raw = row(requestId, true, "预留");
    require(Set.of("待预留", "部分预留", "已预留", "部分发料").contains(text(raw, "status")), "当前状态不能预留");
    if (text(raw, "status").equals("已预留")) return detail(requestId);
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    var t = task(text(raw, "task_kind"), id(raw.get("task_id")), false, true);
    require(active(t.status()), "任务已结束，请释放预留");
    var warehouse = masters.row("warehouses", id(raw.get("warehouse_id")), true);
    require(flag(warehouse.get("enabled")), "仓库已停用");
    var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    boolean all = true, changed = false;
    List<Map<String, Object>> selections = new ArrayList<>();
    if (body.get("selections") instanceof List<?> rawSelections) {
      require(!rawSelections.isEmpty() && rawSelections.size() <= 500, "请选择库位和批次");
      Set<String> seen = new HashSet<>();
      for (Object value : rawSelections) {
        require(value instanceof Map<?, ?>, "预留明细无效");
        var selection = (Map<String, Object>) value;
        require(
            seen.add(text(selection, "lineId") + ":" + text(selection, "balanceId")), "重复的库位批次");
        require(
            db.queryForObject(
                    "SELECT COUNT(*) FROM inv_requisition_line WHERE tenant_id=? AND"
                        + " requisition_id=? AND id=?",
                    Integer.class,
                    auth.tenantId(),
                    requestId,
                    id(selection.get("lineId")))
                == 1,
            "领料明细不存在");
        selections.add(selection);
      }
    }
    for (var line :
        db.queryForList(
            "SELECT * FROM inv_requisition_line WHERE tenant_id=? AND requisition_id=? ORDER BY"
                + " material_id FOR UPDATE",
            auth.tenantId(),
            requestId)) {
      var material = masters.row("materials", id(line.get("material_id")), true);
      require(flag(material.get("enabled")), "物料已停用");
      BigDecimal reserved =
          db.queryForObject(
              "SELECT COALESCE(SUM(remaining_quantity),0) FROM inv_reservation WHERE tenant_id=?"
                  + " AND requisition_line_id=?",
              BigDecimal.class,
              auth.tenantId(),
              line.get("id"));
      BigDecimal need =
          ((BigDecimal) line.get("quantity"))
              .subtract((BigDecimal) line.get("issued_quantity"))
              .subtract(reserved);
      if (need.signum() <= 0) continue;
      var balances =
          db.queryForList(
              "SELECT s.*,pc.expiry_date,COALESCE((SELECT MIN(c.origin_received_at) FROM"
                  + " inv_cost_layer c WHERE c.tenant_id=s.tenant_id AND c.balance_id=s.id AND"
                  + " c.remaining_quantity>0),'9999-12-31') first_received_at FROM inv_balance s"
                  + " JOIN inv_bin b ON b.tenant_id=s.tenant_id AND b.id=s.bin_id LEFT JOIN"
                  + " inv_batch pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id WHERE"
                  + " s.tenant_id=? AND s.warehouse_id=? AND s.material_id=? AND"
                  + " s.stock_status='可用' AND s.quantity>s.reserved_quantity AND b.enabled=1 AND"
                  + " (pc.expiry_date IS NULL OR pc.expiry_date>=?) AND (?=0 OR pc.expiry_date IS"
                  + " NOT NULL) ORDER BY s.id FOR UPDATE",
              auth.tenantId(),
              warehouse.get("id"),
              material.get("id"),
              today,
              material.get("expiry_enabled"));
      String rule =
          text(material, "allocation_rule").isEmpty()
              ? text(warehouse, "allocation_rule")
              : text(material, "allocation_rule");
      Comparator<Map<String, Object>> comparator =
          Comparator.comparing(r -> Objects.toString(r.get("first_received_at")));
      if (rule.equals("FEFO") && flag(material.get("expiry_enabled")))
        comparator =
            Comparator.<Map<String, Object>, String>comparing(
                    r -> Objects.toString(r.get("expiry_date"), "9999-12-31"))
                .thenComparing(comparator);
      balances.sort(comparator.thenComparingLong(r -> id(r.get("id"))));
      var manual =
          selections.stream().filter(v -> id(v.get("lineId")) == id(line.get("id"))).toList();
      if (!selections.isEmpty()) {
        for (var selection : manual)
          require(
              balances.stream().anyMatch(v -> id(v.get("id")) == id(selection.get("balanceId"))),
              "所选批次不可用或库存已变化");
        balances.removeIf(
            v -> manual.stream().noneMatch(x -> id(x.get("balanceId")) == id(v.get("id"))));
      }
      for (var balance : balances) {
        if (need.signum() == 0) break;
        BigDecimal
            available =
                ((BigDecimal) balance.get("quantity"))
                    .subtract((BigDecimal) balance.get("reserved_quantity")),
            qty =
                selections.isEmpty()
                    ? available.min(need)
                    : InventoryFlowService.decimal(
                        manual.stream()
                            .filter(v -> id(v.get("balanceId")) == id(balance.get("id")))
                            .findFirst()
                            .orElseThrow()
                            .get("quantity"),
                        false);
        require(qty.compareTo(available) <= 0 && qty.compareTo(need) <= 0, "预留数量超过可用库存或待领数量");
        if (qty.signum() == 0) continue;
        db.update(
            "UPDATE inv_balance SET reserved_quantity=reserved_quantity+?,row_version=row_version+1"
                + " WHERE tenant_id=? AND id=?",
            qty,
            auth.tenantId(),
            balance.get("id"));
        db.update(
            "INSERT INTO"
                + " inv_reservation(id,tenant_id,requisition_id,requisition_line_id,balance_id,quantity,remaining_quantity)"
                + " VALUES(?,?,?,?,?,?,?)",
            IdWorker.getId(),
            auth.tenantId(),
            requestId,
            line.get("id"),
            balance.get("id"),
            qty,
            qty);
        need = need.subtract(qty);
        changed = true;
      }
      if (need.signum() > 0) all = false;
    }
    if (changed) {
      db.update(
          "UPDATE inv_requisition SET status=?,row_version=row_version+1 WHERE tenant_id=? AND"
              + " id=?",
          all ? "已预留" : "部分预留",
          auth.tenantId(),
          requestId);
      event(requestId, all ? "完成预留" : "部分预留");
    }
    return detail(requestId);
  }

  @Transactional
  public Map<String, Object> release(long requestId, Map<String, Object> body) {
    mutex.enter();
    var raw = row(requestId, true, "释放预留");
    require(Set.of("待预留", "部分预留", "已预留", "部分发料").contains(text(raw, "status")), "当前状态不能释放预留");
    var rows =
        db.queryForList(
            "SELECT * FROM inv_reservation WHERE tenant_id=? AND requisition_id=? AND"
                + " remaining_quantity>0 ORDER BY balance_id,id FOR UPDATE",
            auth.tenantId(),
            requestId);
    if (rows.isEmpty()) return detail(requestId);
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    releaseRows(rows);
    db.update(
        "UPDATE inv_requisition SET status=IF(EXISTS(SELECT 1 FROM inv_requisition_line l WHERE"
            + " l.tenant_id=inv_requisition.tenant_id AND l.requisition_id=inv_requisition.id AND"
            + " l.issued_quantity>0),'部分发料','待预留'),row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        auth.tenantId(),
        requestId);
    event(requestId, "释放预留");
    return detail(requestId);
  }

  private void releaseRows(List<Map<String, Object>> rows) {
    for (var reservation : rows) {
      db.queryForMap(
          "SELECT id FROM inv_balance WHERE tenant_id=? AND id=? FOR UPDATE",
          auth.tenantId(),
          reservation.get("balance_id"));
      db.update(
          "UPDATE inv_balance SET reserved_quantity=reserved_quantity-?,row_version=row_version+1"
              + " WHERE tenant_id=? AND id=?",
          reservation.get("remaining_quantity"),
          auth.tenantId(),
          reservation.get("balance_id"));
      db.update(
          "UPDATE inv_reservation SET remaining_quantity=0 WHERE tenant_id=? AND id=?",
          auth.tenantId(),
          reservation.get("id"));
    }
  }

  @Transactional
  public Map<String, Object> cancel(long requestId, Map<String, Object> body) {
    mutex.enter();
    var raw = row(requestId, true, "查看");
    if (id(raw.get("applicant_id")) != auth.userId())
      permissions.checkInventoryWarehouse(
          "inventoryOutbound", id(raw.get("warehouse_id")), "关闭剩余需求");
    if (text(raw, "status").equals("已取消")) return detail(requestId);
    require(Set.of("草稿", "待预留", "部分预留", "已预留").contains(text(raw, "status")), "当前申请不能取消");
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    require(
        db.queryForObject(
                    "SELECT COALESCE(SUM(issued_quantity),0) FROM inv_requisition_line WHERE"
                        + " tenant_id=? AND requisition_id=?",
                    BigDecimal.class,
                    auth.tenantId(),
                    requestId)
                .signum()
            == 0,
        "已发料申请须先办理退料");
    var reservations =
        db.queryForList(
            "SELECT * FROM inv_reservation WHERE tenant_id=? AND requisition_id=? AND"
                + " remaining_quantity>0 ORDER BY balance_id,id FOR UPDATE",
            auth.tenantId(),
            requestId);
    releaseRows(reservations);
    db.update(
        "UPDATE inv_requisition SET status='已取消',row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        auth.tenantId(),
        requestId);
    closeWarehouseTodos(requestId);
    event(requestId, "取消申请");
    return detail(requestId);
  }

  @Transactional
  public void delete(long recordId, Map<String, Object> body) {
    mutex.enter();
    var raw = row(recordId, true, "删除");
    require(text(raw, "status").equals("草稿"), "只能删除草稿");
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    db.update(
        "UPDATE inv_requisition SET deleted=1,row_version=row_version+1 WHERE tenant_id=? AND id=?",
        auth.tenantId(),
        recordId);
    event(recordId, "删除草稿");
  }

  static String businessStatus(String status) {
    return switch (status) {
      case "待预留", "部分预留", "已预留" -> "待出库";
      case "部分发料" -> "部分出库";
      default -> status;
    };
  }

  String reservationStatus(long requestId) {
    var quantities =
        db.queryForMap(
            "SELECT COALESCE(SUM(quantity-issued_quantity),0) remaining FROM inv_requisition_line"
                + " WHERE tenant_id=? AND requisition_id=?",
            auth.tenantId(),
            requestId);
    var reserved =
        db.queryForObject(
            "SELECT COALESCE(SUM(remaining_quantity),0) FROM inv_reservation WHERE tenant_id=? AND"
                + " requisition_id=?",
            BigDecimal.class,
            auth.tenantId(),
            requestId);
    return reserved.signum() == 0
        ? "未预留"
        : reserved.compareTo((BigDecimal) quantities.get("remaining")) >= 0 ? "已预留" : "部分预留";
  }

  Map<String, Object> outboundRequest(long requestId, boolean lock, String action) {
    var r = row(requestId, lock, action);
    require(Set.of("待预留", "部分预留", "已预留", "部分发料").contains(text(r, "status")), "申请已结束或尚未提交");
    var t = task(text(r, "task_kind"), id(r.get("task_id")), false, lock);
    require(active(t.status()), "任务已结束，不能出库");
    return r;
  }

  String validateOutboundTask(String kind, long taskId, long company, boolean access) {
    var t = task(kind, taskId, access, false);
    require(t.company() == company, "任务与仓库不属于同一公司");
    require(active(t.status()), "任务已结束，不能出库");
    return t.code();
  }

  Long taskDepartment(String kind, long taskId) {
    String table =
        kind.equals("维修")
            ? "eam_repair_order"
            : kind.equals("保养") ? "eam_maintenance_task" : "eam_inspection_task";
    String field = kind.equals("维修") ? "department_org_id" : "department_id";
    return db.queryForObject(
        "SELECT " + field + " FROM " + table + " WHERE tenant_id=? AND id=?",
        Long.class,
        auth.tenantId(),
        taskId);
  }

  List<Map<String, Object>> outboundTaskChoices() {
    var output = new ArrayList<Map<String, Object>>();
    for (var r :
        repairs.selectList(
            new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getTenantId, auth.tenantId())
                .eq(RepairOrder::getDeleted, 0))) {
      if (!active(r.getStatus())) continue;
      try {
        permissions.checkRepair(r, "查看");
        var t = task("维修", r.getId(), false, false);
        output.add(
            Map.of(
                "id",
                r.getId().toString(),
                "kind",
                "维修",
                "code",
                t.code(),
                "companyId",
                Long.toString(t.company()),
                "name",
                r.getEquipmentName()));
      } catch (ResponseStatusException e) {
        if (e.getStatusCode().value() != 403) throw e;
      } catch (IllegalArgumentException unavailable) {
      }
    }
    for (String kind : List.of("保养", "点巡检")) {
      var rows =
          kind.equals("保养")
              ? (permissions.allowed("maintenanceTask", "查看")
                  ? maintenance.tasks()
                  : List.<Map<String, Object>>of())
              : ((permissions.allowed("inspectionDailyTask", "查看")
                      || permissions.allowed("inspectionPeriodicTask", "查看"))
                  ? inspection.tasks()
                  : List.<Map<String, Object>>of());
      for (var r : rows)
        if (active(text(r, "status")) && id(r.get("companyId")) != 0)
          output.add(
              Map.of(
                  "id",
                  text(r, "id"),
                  "kind",
                  kind,
                  "code",
                  text(r, "code"),
                  "companyId",
                  text(r, "companyId"),
                  "name",
                  text(r, "name")));
    }
    return output;
  }

  void outboundCompleted(long requestId, String code) {
    var r = row(requestId, true, "确认出库");
    boolean complete =
        db.queryForObject(
                "SELECT COUNT(*) FROM inv_requisition_line WHERE tenant_id=? AND requisition_id=?"
                    + " AND issued_quantity<quantity",
                Integer.class,
                auth.tenantId(),
                requestId)
            == 0;
    db.update(
        "UPDATE inv_requisition SET status=?,row_version=row_version+1 WHERE tenant_id=? AND id=?",
        complete ? "已完成" : "部分发料",
        auth.tenantId(),
        requestId);
    event(requestId, "确认出库 · " + code);
    taskEvent(r, "出库完成 · " + code);
    if (complete) closeWarehouseTodos(requestId);
    db.update(
        "INSERT INTO inv_inbox(id,tenant_id,user_id,document_id,route_kind,kind,title,is_todo)"
            + " VALUES(?,?,?,?,'requisitions','出库完成',?,0)",
        IdWorker.getId(),
        auth.tenantId(),
        r.get("applicant_id"),
        requestId,
        code + " · " + text(r, "task_code"));
  }

  @Transactional
  public Object closeRemaining(long requestId, Map<String, Object> body) {
    mutex.enter();
    var r = row(requestId, true, "查看");
    if (id(r.get("applicant_id")) != auth.userId())
      permissions.checkInventoryWarehouse("inventoryOutbound", id(r.get("warehouse_id")), "关闭剩余需求");
    require(Set.of("待预留", "部分预留", "已预留", "部分发料").contains(text(r, "status")), "申请已经结束");
    VersionGuard.checkPayload((Number) r.get("row_version"), body);
    require(
        !text(body, "reason").isBlank() && text(body, "reason").length() <= 1000,
        "请填写关闭原因（最多1000字）");
    releaseRows(
        db.queryForList(
            "SELECT * FROM inv_reservation WHERE tenant_id=? AND requisition_id=? AND"
                + " remaining_quantity>0 ORDER BY balance_id,id FOR UPDATE",
            auth.tenantId(),
            requestId));
    db.update(
        "UPDATE inv_requisition SET status='已关闭',row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        auth.tenantId(),
        requestId);
    closeWarehouseTodos(requestId);
    db.update(
        "INSERT INTO"
            + " inv_document_event(id,tenant_id,document_kind,document_id,action,actor_id,actor_name,details_json)"
            + " VALUES(?,?,'领料',?,'关闭剩余需求',?,?,JSON_OBJECT('reason',?))",
        IdWorker.getId(),
        auth.tenantId(),
        requestId,
        auth.userId(),
        auth.user().getDisplayName(),
        text(body, "reason"));
    if (id(r.get("applicant_id")) != auth.userId())
      db.update(
          "INSERT INTO inv_inbox(id,tenant_id,user_id,document_id,route_kind,kind,title,is_todo)"
              + " VALUES(?,?,?,?,'requisitions','需求已关闭',?,0)",
          IdWorker.getId(),
          auth.tenantId(),
          r.get("applicant_id"),
          requestId,
          text(r, "code") + " · 剩余需求已关闭");
    taskEvent(r, "关闭领料剩余需求 · " + text(r, "code"));
    return detail(requestId);
  }

  public List<Map<String, Object>> pendingRows(String q, String filters, String action) {
    return matching(q, filters).stream()
        .filter(
            r ->
                Set.of("待出库", "部分出库").contains(text(r, "status"))
                    && permissions.inventoryWarehouseAllowed(
                        "inventoryOutbound", id(r.get("warehouseId")), action))
        .toList();
  }
}
