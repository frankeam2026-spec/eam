package com.windasoft.eam.service.inventory;

import static com.windasoft.eam.service.inventory.InventoryService.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.common.inventory.InventoryMoney;
import com.windasoft.eam.repository.inventory.RequisitionRepository;
import com.windasoft.eam.service.inspection.InspectionService;
import com.windasoft.eam.service.maintenance.MaintenanceService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequisitionService {
  @org.springframework.beans.factory.annotation.Autowired private InventoryListService listQueries;
  @org.springframework.beans.factory.annotation.Autowired private InventoryMutex mutex;

  @org.springframework.beans.factory.annotation.Autowired
  @org.springframework.context.annotation.Lazy
  private OutboundService outbounds;

  private final RequisitionRepository repository;
  private final InventoryService masters;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final MaintenanceService maintenance;
  private final InspectionService inspection;
  private final ObjectMapper json;

  public RequisitionService(
      RequisitionRepository repository,
      InventoryService masters,
      AuthContext auth,
      PermissionService permissions,
      MaintenanceService maintenance,
      InspectionService inspection,
      ObjectMapper json) {
    this.repository = repository;
    this.masters = masters;
    this.auth = auth;
    this.permissions = permissions;
    this.maintenance = maintenance;
    this.inspection = inspection;
    this.json = json;
  }

  private boolean active(String status) {
    return !Set.of("待验收", "待审核", "已完成", "已验收", "已审核", "已作废", "已免检", "过期作废", "已取消", "作废", "完成", "已关闭")
        .contains(status);
  }

  private record Task(long company, String code, String status) {}

  private Task task(String kind, long taskId, boolean checkAccess, boolean lock) {
    if (kind.equals("维修")) {
      // Lock the source task before creating or reserving against it.
      var found = repository.loadRepairTask(auth.tenantId(), taskId, lock);
      require(
          found.size() == 1,
          com.windasoft.eam.common.MessageContext.text("server.repair.message0046"));
      var raw = found.getFirst();
      if (checkAccess) permissions.checkRepair(repository.findRepair(taskId), "查看");
      Long company = permissions.companyOfOrganization(id(raw.get("department_org_id")));
      if (company == null) {
        var devices = repository.findEquipmentCompany(auth.tenantId(), text(raw, "equipment_code"));
        if (devices.size() == 1 && devices.getFirst().get("company_org_id") != null)
          company = id(devices.getFirst().get("company_org_id"));
      }
      require(
          company != null,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0210"));
      return new Task(company, text(raw, "order_no"), text(raw, "status"));
    }
    require(
        Set.of("保养", "点巡检").contains(kind),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0018"));
    String table = kind.equals("保养") ? "v_maintenance_task" : "v_inspection_task";
    var found = repository.loadTask(auth.tenantId(), taskId, table, kind, lock);
    require(
        found.size() == 1,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0211"));
    if (checkAccess) {
      if (kind.equals("保养")) maintenance.task(taskId);
      else inspection.task(taskId);
    }
    var raw = found.getFirst();
    require(
        raw.get("company_id") != null,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0212"));
    return new Task(id(raw.get("company_id")), text(raw, "code"), text(raw, "status"));
  }

  private Map<String, Object> row(long requestId, boolean lock, String action) {
    var rows = repository.loadRequest(auth.tenantId(), requestId, lock);
    require(
        !rows.isEmpty(),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0213"));
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
        repository.listActiveUsers(auth.tenantId()).stream()
            .map(v -> id(v.get("id")))
            .filter(
                user ->
                    permissions.inventoryWarehouseUser(
                            user, "inventoryOutbound", id(warehouse.get("id")), "确认出库")
                        && permissions.inventoryWarehouseUser(
                            user, "inventoryOutbound", id(warehouse.get("id")), "查看"))
            .toList();

    for (long recipient : recipients)
      repository.insertWarehouseTodo(
          IdWorker.getId(),
          auth.tenantId(),
          recipient,
          requestId,
          text(r, "code") + " · " + text(r, "task_code"));
  }

  private void closeWarehouseTodos(long requestId) {
    repository.completeWarehouseTodos(auth.tenantId(), requestId);
  }

  void taskEvent(Map<String, Object> request, String action) {
    String kind = text(request, "task_kind");
    long taskId = id(request.get("task_id"));
    if (kind.equals("维修"))
      repository.insertRepairEvent(
          IdWorker.getId(),
          auth.tenantId(),
          taskId,
          action,
          auth.userId(),
          auth.user().getDisplayName());
    else {
      String table = kind.equals("保养") ? "eam_maintenance_event" : "eam_inspection_event";
      repository.insertTaskEvent(
          IdWorker.getId(),
          auth.tenantId(),
          taskId,
          auth.userId(),
          auth.user().getDisplayName(),
          action,
          table);
    }
  }

  private void event(long requestId, String action) {
    repository.insertEvent(
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
      repository.nextMonthlyNumber("inv-LL-" + auth.tenantId(), month);
      code = "LL-" + month + "-" + String.format("%04d", repository.currentNumber());
    } while (repository.countCode(auth.tenantId(), code) > 0);
    return code;
  }

  public Map<String, Object> detail(long requestId) {
    var rendered = masters.view(row(requestId, false, "查看"));
    rendered.put(
        "warehouseName",
        masters.row("warehouses", id(rendered.get("warehouseId")), false).get("name"));
    rendered.put(
        "lines",
        repository.listLines(auth.tenantId(), requestId).stream().map(masters::view).toList());
    rendered.put(
        "reservations",
        repository.listReservations(auth.tenantId(), requestId).stream()
            .map(masters::view)
            .toList());
    rendered.put(
        "issues",
        repository.listIssues(auth.tenantId(), requestId).stream()
            .map(
                issue -> {
                  var v = masters.view(issue);
                  v.put(
                      "allocations",
                      repository
                          .listAllocations(auth.tenantId(), issue.get("id"), requestId)
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
        repository.listEvents(auth.tenantId(), requestId).stream().map(masters::view).toList());
    if (!permissions.inventoryWarehouseAllowed(
            "inventoryOutbound", id(rendered.get("warehouseId")), "查看金额")
        && !permissions.inventoryWarehouseAllowed(
            "inventoryRequisition", id(rendered.get("warehouseId")), "查看金额"))
      InventoryMoney.hide(rendered);
    return rendered;
  }

  public Object list(String q, String filters, int page, int size) {
    return listQueries.requisitions(q, filters, "[]", page, size, false, false);
  }

  public Object export(String q, String filters) {
    return listQueries.exportRequisitions(q, filters, "[]", false);
  }

  private void checkTaskApplicant(String kind, long taskId) {
    if (kind.equals("维修")) {
      var order = repository.findRepair(taskId);
      require(
          order != null
              && order.getTenantId() == auth.tenantId()
              && order.getDeleted() == 0
              && Objects.equals(order.getAssigneeUserId(), auth.userId())
              && Set.of("处理中", "挂起").contains(order.getStatus()),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0214"));
      permissions.checkRepair(order, "查看");
    } else {
      var view = kind.equals("保养") ? maintenance.task(taskId) : inspection.task(taskId);
      require(
          Boolean.TRUE.equals(view.get("canExecute"))
              && Set.of("执行中", "挂起").contains(text(view, "status")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0215"));
    }
  }

  @org.springframework.beans.factory.annotation.Autowired
  private InventorySelectorService selectors;

  public Object choices(boolean paged, String taskKind, Long taskId) {
    if (!paged) return choices();
    var selected =
        taskId == null || taskKind.isBlank()
            ? List.<Map<String, Object>>of()
            : selectors
                .tasks(
                    "requisition",
                    com.windasoft.eam.dto.common.ListQuery.from(Map.of("size", "1")),
                    null,
                    taskKind,
                    taskId)
                .data();
    boolean create = permissions.allowed("inventoryRequisition", "新增");
    var warehouses =
        repository.listApplicantWarehouseChoices(auth.tenantId()).stream()
            .filter(
                w ->
                    !create
                        || permissions.inventoryWarehouseAllowed(
                            "inventoryRequisition", id(w.get("id")), "新增"))
            .map(masters::view)
            .filter(
                w ->
                    selected.stream()
                        .anyMatch(t -> text(t, "companyId").equals(text(w, "companyId"))))
            .toList();
    return Map.of(
        "warehouses",
        warehouses,
        "tasks",
        selected,
        "materials",
        List.of(),
        "users",
        List.of(),
        "bins",
        List.of(),
        "companies",
        List.of());
  }

  public Object choices() {
    @SuppressWarnings("unchecked")
    var result = new LinkedHashMap<String, Object>();
    result.put("materials", List.of());
    result.put("companies", List.of());
    result.put("users", List.of());
    result.put("bins", List.of());
    result.put(
        "warehouses",
        repository.listWarehouseChoices(auth.tenantId()).stream().map(masters::view).toList());
    var tasks = new ArrayList<Map<String, Object>>();
    for (var r : repository.listRepairs(auth.tenantId())) {
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
    boolean canCreate = permissions.allowed("inventoryRequisition", "新增");
    if (!canCreate)
      tasks.removeIf(
          t -> {
            try {
              checkTaskApplicant(text(t, "kind"), id(t.get("id")));
              return false;
            } catch (ResponseStatusException | IllegalArgumentException denied) {
              return true;
            }
          });
    // Applying for material is independent of inventory-query/outbound view permissions.
    // Use the same create scope (or personal task access) enforced by save().
    var warehouses =
        repository.listApplicantWarehouseChoices(auth.tenantId()).stream()
            .filter(
                w ->
                    !canCreate
                        || permissions.inventoryWarehouseAllowed(
                            "inventoryRequisition", id(w.get("id")), "新增"))
            .map(masters::view)
            .filter(
                w ->
                    tasks.stream().anyMatch(t -> text(t, "companyId").equals(text(w, "companyId"))))
            .toList();
    result.put("warehouses", warehouses);
    result.put("tasks", tasks);
    return result;
  }

  public Object materials(
      String q, int page, int size, String taskKind, long taskId, long warehouseId) {
    require(
        q.length() <= 200 && page >= 1 && size >= 1 && size <= 100,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0032"));
    var t = task(taskKind, taskId, true, false);
    require(
        active(t.status()),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0216"));
    var warehouse = masters.row("warehouses", warehouseId, false);
    if (!permissions.allowed("inventoryRequisition", "新增")) checkTaskApplicant(taskKind, taskId);
    else permissions.checkInventoryWarehouse("inventoryRequisition", warehouseId, "新增");
    require(
        flag(warehouse.get("enabled")) && id(warehouse.get("company_id")) == t.company(),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0217"));
    String match = "%" + q.trim() + "%";
    Long total = repository.countMaterials(auth.tenantId(), match);
    var data =
        repository.listMaterials(auth.tenantId(), match, page, size).stream()
            .map(masters::view)
            .toList();
    return Map.of("data", data, "page", Map.of("total", total));
  }

  private BigDecimal issueQuantity(Object value) {
    try {
      var qty = new BigDecimal(Objects.toString(value, ""));
      require(
          qty.signum() >= 0 && qty.scale() <= 6 && qty.precision() - qty.scale() <= 14,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0218"));
      return qty;
    } catch (NumberFormatException e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.inventory.message0026");
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
      require(
          id(previous.get("applicant_id")) == auth.userId(),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0219"));
      require(
          text(previous, "status").equals("草稿"),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0220"));
      VersionGuard.checkPayload((Number) previous.get("row_version"), body);
    }
    var t = task(text(body, "taskKind"), id(body.get("taskId")), true, true);
    require(
        active(t.status()),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0221"));
    var warehouse = masters.row("warehouses", id(body.get("warehouseId")), true);
    if (!permissions.allowed("inventoryRequisition", "新增"))
      checkTaskApplicant(text(body, "taskKind"), id(body.get("taskId")));
    else permissions.checkInventoryWarehouse("inventoryRequisition", id(warehouse.get("id")), "新增");
    require(
        flag(warehouse.get("enabled")) && id(warehouse.get("company_id")) == t.company(),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0217"));
    require(
        text(body, "remark").length() <= 1000,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0091"));
    require(
        body.get("lines") instanceof List<?>,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0222"));
    var input = (List<?>) body.get("lines");
    require(
        !input.isEmpty() && input.size() <= 500,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0223"));
    var orderedMaterials = new TreeSet<Long>();
    for (Object item : input) {
      require(item instanceof Map<?, ?>, "明细格式无效");
      orderedMaterials.add(id(((Map<?, ?>) item).get("materialId")));
    }
    for (long materialId : orderedMaterials) masters.row("materials", materialId, true);
    long target = requestId == null ? IdWorker.getId() : requestId;
    if (previous == null)
      repository.insertRequest(
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
      repository.updateRequest(
          t.company(),
          warehouse.get("id"),
          text(body, "taskKind"),
          id(body.get("taskId")),
          t.code(),
          text(body, "remark"),
          auth.tenantId(),
          target);
      repository.deleteLines(auth.tenantId(), target);
    }
    var materials = new HashSet<Long>();
    int lineNo = 0;
    for (Object item : input) {
      require(item instanceof Map<?, ?>, "明细格式无效");
      @SuppressWarnings("unchecked")
      var line = (Map<String, Object>) item;
      long materialId = id(line.get("materialId"));
      require(
          materials.add(materialId),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0224"));
      var material = masters.row("materials", materialId, true);
      require(
          flag(material.get("enabled")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0057"));
      BigDecimal qty;
      try {
        qty = new BigDecimal(text(line, "quantity"));
      } catch (Exception e) {
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.inventory.message0027");
      }
      require(
          qty.signum() > 0 && qty.scale() <= 6 && qty.precision() - qty.scale() <= 14,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0225"));
      repository.insertLine(IdWorker.getId(), auth.tenantId(), target, ++lineNo, materialId, qty);
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
    require(
        id(raw.get("applicant_id")) == auth.userId(),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0226"));
    if (!text(raw, "status").equals("草稿")) {
      require(
          Set.of("待预留", "部分预留", "已预留", "部分发料", "已完成").contains(text(raw, "status")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0227"));
      return detail(requestId);
    }
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    var t = task(text(raw, "task_kind"), id(raw.get("task_id")), true, true);
    require(
        active(t.status()),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0228"));
    repository.submitRequest(auth.tenantId(), requestId);
    event(requestId, "提交申请");
    taskEvent(raw, "领料申请 · " + text(raw, "code"));
    notifyWarehouse(requestId);
    return detail(requestId);
  }

  public Object stockPool(long requestId) {
    var raw = row(requestId, false, "出库");
    return repository
        .listReservableStock(
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
    require(
        Set.of("待预留", "部分预留", "已预留", "部分发料").contains(text(raw, "status")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0229"));
    if (text(raw, "status").equals("已预留")) return detail(requestId);
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    var t = task(text(raw, "task_kind"), id(raw.get("task_id")), false, true);
    require(
        active(t.status()),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0230"));
    var warehouse = masters.row("warehouses", id(raw.get("warehouse_id")), true);
    require(
        flag(warehouse.get("enabled")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0035"));
    var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    boolean all = true, changed = false;
    List<Map<String, Object>> selections = new ArrayList<>();
    if (body.get("selections") instanceof List<?> rawSelections) {
      require(
          !rawSelections.isEmpty() && rawSelections.size() <= 500,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0231"));
      Set<String> seen = new HashSet<>();
      for (Object value : rawSelections) {
        require(value instanceof Map<?, ?>, "预留明细无效");
        var selection = (Map<String, Object>) value;
        require(
            seen.add(text(selection, "lineId") + ":" + text(selection, "balanceId")),
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0232"));
        require(
            repository.countLine(auth.tenantId(), requestId, id(selection.get("lineId"))) == 1,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0233"));
        selections.add(selection);
      }
    }
    for (var line : repository.lockLines(auth.tenantId(), requestId)) {
      var material = masters.row("materials", id(line.get("material_id")), true);
      require(
          flag(material.get("enabled")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0057"));
      BigDecimal reserved = repository.sumLineReservations(auth.tenantId(), line.get("id"));
      BigDecimal need =
          ((BigDecimal) line.get("quantity"))
              .subtract((BigDecimal) line.get("issued_quantity"))
              .subtract(reserved);
      if (need.signum() <= 0) continue;
      var balances =
          repository.lockAvailableStock(
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
              com.windasoft.eam.common.MessageContext.text("server.inventory.message0234"));
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
        require(
            qty.compareTo(available) <= 0 && qty.compareTo(need) <= 0,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0235"));
        if (qty.signum() == 0) continue;
        repository.addReservedQuantity(qty, auth.tenantId(), balance.get("id"));
        repository.insertReservation(
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
      repository.updateReservationStatus(all ? "已预留" : "部分预留", auth.tenantId(), requestId);
      event(requestId, all ? "完成预留" : "部分预留");
    }
    return detail(requestId);
  }

  @Transactional
  public Map<String, Object> release(long requestId, Map<String, Object> body) {
    mutex.enter();
    var raw = row(requestId, true, "释放预留");
    require(
        Set.of("待预留", "部分预留", "已预留", "部分发料").contains(text(raw, "status")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0236"));
    var rows = repository.lockReservations(auth.tenantId(), requestId);
    if (rows.isEmpty()) return detail(requestId);
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    releaseRows(rows);
    repository.resetStatus(auth.tenantId(), requestId);
    event(requestId, "释放预留");
    return detail(requestId);
  }

  private void releaseRows(List<Map<String, Object>> rows) {
    for (var reservation : rows) {
      repository.lockBalance(auth.tenantId(), reservation.get("balance_id"));
      repository.releaseBalance(
          reservation.get("remaining_quantity"), auth.tenantId(), reservation.get("balance_id"));
      repository.releaseReservation(auth.tenantId(), reservation.get("id"));
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
    require(
        Set.of("草稿", "待预留", "部分预留", "已预留").contains(text(raw, "status")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0237"));
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    require(
        repository.sumIssuedForCancel(auth.tenantId(), requestId).signum() == 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0238"));
    var reservations = repository.lockReservationsForCancel(auth.tenantId(), requestId);
    releaseRows(reservations);
    repository.cancelRequest(auth.tenantId(), requestId);
    closeWarehouseTodos(requestId);
    event(requestId, "取消申请");
    return detail(requestId);
  }

  @Transactional
  public void delete(long recordId, Map<String, Object> body) {
    mutex.enter();
    var raw = row(recordId, true, "删除");
    require(
        text(raw, "status").equals("草稿"),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0182"));
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    repository.deleteRequest(auth.tenantId(), recordId);
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
    var quantities = repository.remainingSummary(auth.tenantId(), requestId);
    var reserved = repository.sumReservations(auth.tenantId(), requestId);
    return reserved.signum() == 0
        ? "未预留"
        : reserved.compareTo((BigDecimal) quantities.get("remaining")) >= 0 ? "已预留" : "部分预留";
  }

  Map<String, Object> outboundRequest(long requestId, boolean lock, String action) {
    var r = row(requestId, lock, action);
    require(
        Set.of("待预留", "部分预留", "已预留", "部分发料").contains(text(r, "status")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0239"));
    var t = task(text(r, "task_kind"), id(r.get("task_id")), false, lock);
    require(
        active(t.status()),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0240"));
    return r;
  }

  String validateOutboundTask(String kind, long taskId, long company, boolean access) {
    var t = task(kind, taskId, access, false);
    require(
        t.company() == company,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0241"));
    require(
        active(t.status()),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0240"));
    return t.code();
  }

  Long taskDepartment(String kind, long taskId) {
    String table =
        kind.equals("维修")
            ? "eam_repair_order"
            : kind.equals("保养") ? "v_maintenance_task" : "v_inspection_task";
    String field = kind.equals("维修") ? "department_org_id" : "department_id";
    return repository.findTaskDepartment(auth.tenantId(), taskId, field, table);
  }

  List<Map<String, Object>> outboundTaskChoices() {
    var output = new ArrayList<Map<String, Object>>();
    for (var r : repository.listRepairs(auth.tenantId())) {
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
    boolean complete = repository.countUnissuedLines(auth.tenantId(), requestId) == 0;
    repository.updateIssueProgress(complete ? "已完成" : "部分发料", auth.tenantId(), requestId);
    event(requestId, "确认出库 · " + code);
    taskEvent(r, "出库完成 · " + code);
    if (complete) closeWarehouseTodos(requestId);
    repository.insertApplicantNotice(
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
    require(
        Set.of("待预留", "部分预留", "已预留", "部分发料").contains(text(r, "status")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0242"));
    VersionGuard.checkPayload((Number) r.get("row_version"), body);
    require(
        !text(body, "reason").isBlank() && text(body, "reason").length() <= 1000,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0243"));
    releaseRows(repository.lockReservationsForClose(auth.tenantId(), requestId));
    repository.closeRequest(auth.tenantId(), requestId);
    closeWarehouseTodos(requestId);
    repository.insertCloseEvent(
        IdWorker.getId(),
        auth.tenantId(),
        requestId,
        auth.userId(),
        auth.user().getDisplayName(),
        text(body, "reason"));
    if (id(r.get("applicant_id")) != auth.userId())
      repository.insertCloseNotice(
          IdWorker.getId(),
          auth.tenantId(),
          r.get("applicant_id"),
          requestId,
          text(r, "code") + " · 剩余需求已关闭");
    taskEvent(r, "关闭领料剩余需求 · " + text(r, "code"));
    return detail(requestId);
  }
}
