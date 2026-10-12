package com.windasoft.eam.inventory;

import static com.windasoft.eam.inventory.InventoryService.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.inspection.InspectionService;
import com.windasoft.eam.maintenance.MaintenanceService;
import com.windasoft.eam.repair.*;
import com.windasoft.eam.system.*;
import java.math.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryUsageService {
  private final JdbcTemplate db;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final InventoryService masters;
  private final InventoryMutex mutex;
  private final RepairOrderMapper repairs;
  private final MaintenanceService maintenance;
  private final InspectionService inspection;
  private final ObjectMapper json;

  public InventoryUsageService(
      JdbcTemplate db,
      AuthContext auth,
      PermissionService permissions,
      InventoryService masters,
      InventoryMutex mutex,
      RepairOrderMapper repairs,
      MaintenanceService maintenance,
      InspectionService inspection,
      ObjectMapper json) {
    this.db = db;
    this.auth = auth;
    this.permissions = permissions;
    this.masters = masters;
    this.mutex = mutex;
    this.repairs = repairs;
    this.maintenance = maintenance;
    this.inspection = inspection;
    this.json = json;
  }

  private Map<String, Object> task(String kind, long task, boolean write) {
    if (write) {
      require(Set.of("维修", "保养", "点巡检").contains(kind), "任务类型无效");
      String table =
          kind.equals("维修")
              ? "eam_repair_order"
              : kind.equals("保养") ? "eam_maintenance_task" : "eam_inspection_task";
      db.queryForList(
          "SELECT id FROM " + table + " WHERE tenant_id=? AND id=? FOR UPDATE",
          auth.tenantId(),
          task);
    }
    if (kind.equals("维修")) {
      var order = repairs.selectById(task);
      require(
          order != null && order.getTenantId() == auth.tenantId() && order.getDeleted() == 0,
          "工单不存在");
      permissions.checkRepair(order, "查看");
      if (write)
        require(
            Objects.equals(order.getAssigneeUserId(), auth.userId())
                && Set.of("处理中", "挂起").contains(order.getStatus()),
            "仅当前工单负责人可以登记用料");
      var eq =
          db.queryForList(
              "SELECT CAST(id AS CHAR) id,name FROM eam_equipment WHERE tenant_id=? AND"
                  + " equipment_code=? AND deleted=0",
              auth.tenantId(),
              order.getEquipmentCode());
      require(eq.size() == 1, "任务设备不存在");
      return Map.of("equipment", eq, "code", order.getOrderNo());
    }
    require(Set.of("保养", "点巡检").contains(kind), "任务类型无效");
    var view = kind.equals("保养") ? maintenance.task(task) : inspection.task(task);
    if (write)
      require(
          Boolean.TRUE.equals(view.get("canExecute"))
              && Objects.equals(text(view, "ownerId"), Long.toString(auth.userId()))
              && Set.of("执行中", "挂起").contains(text(view, "status")),
          "请先开始执行任务，仅执行人可以登记用料");
    if (kind.equals("点巡检"))
      return Map.of(
          "equipment", view.getOrDefault("equipment", List.of()), "code", view.get("code"));
    var raw =
        db.queryForMap(
            "SELECT equipment_id FROM eam_maintenance_task WHERE tenant_id=? AND id=?",
            auth.tenantId(),
            task);
    var eq =
        db.queryForList(
            "SELECT CAST(id AS CHAR) id,name FROM eam_equipment WHERE tenant_id=? AND id=?",
            auth.tenantId(),
            raw.get("equipment_id"));
    return Map.of("equipment", eq, "code", view.get("code"));
  }

  public Object list(String kind, long taskId) {
    var source = task(kind, taskId, false);
    var output = new LinkedHashMap<String, Object>(source);
    output.put(
        "requests",
        db
            .queryForList(
                "SELECT CAST(r.id AS CHAR) id,r.code,r.status,w.name warehouse_name FROM"
                    + " inv_requisition r JOIN inv_warehouse w ON w.tenant_id=r.tenant_id AND"
                    + " w.id=r.warehouse_id WHERE r.tenant_id=? AND r.task_kind=? AND r.task_id=?"
                    + " AND r.deleted=0 ORDER BY r.created_at DESC",
                auth.tenantId(),
                kind,
                taskId)
            .stream()
            .map(masters::view)
            .toList());
    output.put(
        "allocations",
        db
            .queryForList(
                "SELECT a.*,m.name material_name,m.usage_type,m.unit,i.code"
                    + " issue_code,COALESCE(ol.task_code,r.task_code) task_code FROM"
                    + " inv_issue_allocation a JOIN inv_issue i ON i.tenant_id=a.tenant_id AND"
                    + " i.id=a.issue_id JOIN inv_balance s ON s.tenant_id=a.tenant_id AND"
                    + " s.id=a.balance_id JOIN inv_material m ON m.tenant_id=s.tenant_id AND"
                    + " m.id=s.material_id LEFT JOIN inv_outbound_line ol ON"
                    + " ol.tenant_id=a.tenant_id AND ol.id=a.outbound_line_id LEFT JOIN"
                    + " inv_requisition_line rl ON rl.tenant_id=a.tenant_id AND"
                    + " rl.id=a.requisition_line_id LEFT JOIN inv_requisition r ON"
                    + " r.tenant_id=rl.tenant_id AND r.id=rl.requisition_id WHERE a.tenant_id=? AND"
                    + " COALESCE(ol.task_kind,r.task_kind)=? AND COALESCE(ol.task_id,r.task_id)=?"
                    + " AND i.reversed=0 ORDER BY i.created_at,a.id",
                auth.tenantId(),
                kind,
                taskId)
            .stream()
            .map(
                r -> {
                  var v = masters.view(r);
                  InventoryMoney.hide(v);
                  return v;
                })
            .toList());
    var equipmentIds = new ArrayList<Long>();
    for (Object eq : (List<?>) source.get("equipment"))
      equipmentIds.add(id(((Map<?, ?>) eq).get("id")));
    output.put(
        "installed",
        equipmentIds.isEmpty()
            ? List.of()
            : db
                .queryForList(
                    "SELECT u.*,m.name,m.code,m.model FROM inv_usage u JOIN inv_material m ON"
                        + " m.tenant_id=u.tenant_id AND m.id=u.material_id WHERE u.tenant_id=? AND"
                        + " u.equipment_id IN ("
                        + String.join(",", Collections.nCopies(equipmentIds.size(), "?"))
                        + ") AND u.status='在用' ORDER BY u.created_at DESC",
                    java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(auth.tenantId()), equipmentIds.stream())
                        .toArray())
                .stream()
                .map(masters::view)
                .toList());
    output.put(
        "usage",
        db
            .queryForList(
                "SELECT u.*,m.name material_name FROM inv_usage u JOIN inv_material m ON"
                    + " m.tenant_id=u.tenant_id AND m.id=u.material_id JOIN inv_issue_allocation a"
                    + " ON a.tenant_id=u.tenant_id AND a.id=u.allocation_id LEFT JOIN"
                    + " inv_outbound_line ol ON ol.tenant_id=a.tenant_id AND"
                    + " ol.id=a.outbound_line_id LEFT JOIN inv_requisition r ON"
                    + " r.tenant_id=u.tenant_id AND r.id=u.requisition_id WHERE u.tenant_id=? AND"
                    + " COALESCE(ol.task_kind,r.task_kind)=? AND COALESCE(ol.task_id,r.task_id)=?"
                    + " ORDER BY u.created_at,u.id",
                auth.tenantId(),
                kind,
                taskId)
            .stream()
            .map(masters::view)
            .toList());
    return output;
  }

  @Transactional
  public Object use(String kind, long taskId, Map<String, Object> body) {
    mutex.enter();
    var source = task(kind, taskId, true);
    long allocation = id(body.get("allocationId"));
    var found =
        db.queryForList(
            "SELECT a.*,s.material_id,m.usage_type,m.name"
                + " material_name,rl.requisition_id,COALESCE(ol.task_kind,r.task_kind)"
                + " task_kind,COALESCE(ol.task_id,r.task_id) task_id FROM inv_issue_allocation a"
                + " JOIN inv_issue i ON i.tenant_id=a.tenant_id AND i.id=a.issue_id JOIN"
                + " inv_balance s ON s.tenant_id=a.tenant_id AND s.id=a.balance_id JOIN"
                + " inv_material m ON m.tenant_id=s.tenant_id AND m.id=s.material_id LEFT JOIN"
                + " inv_outbound_line ol ON ol.tenant_id=a.tenant_id AND ol.id=a.outbound_line_id"
                + " LEFT JOIN inv_requisition_line rl ON rl.tenant_id=a.tenant_id AND"
                + " rl.id=a.requisition_line_id LEFT JOIN inv_requisition r ON"
                + " r.tenant_id=rl.tenant_id AND r.id=rl.requisition_id WHERE a.tenant_id=? AND"
                + " a.id=? AND i.reversed=0 FOR UPDATE",
            auth.tenantId(),
            allocation);
    require(found.size() == 1, "发料明细不存在");
    var a = found.getFirst();
    require(text(a, "task_kind").equals(kind) && id(a.get("task_id")) == taskId, "发料明细不属于当前任务");
    var qty = InventoryFlowService.decimal(body.get("quantity"), false);

    boolean install = text(a, "usage_type").equals("可安装");
    long eq = id(body.get("equipmentId"));
    if (install)
      require(
          ((List<?>) source.get("equipment"))
              .stream().anyMatch(e -> id(((Map<?, ?>) e).get("id")) == eq),
          "请选择当前任务关联设备");
    String key = text(body, "confirmationKey");
    require(key.matches("[a-zA-Z0-9-]{16,64}"), "确认标识无效");
    var previous =
        db.queryForList(
            "SELECT allocation_id,quantity FROM inv_usage WHERE tenant_id=? AND confirmation_key=?",
            auth.tenantId(),
            key);
    if (!previous.isEmpty()) {
      require(
          id(previous.getFirst().get("allocation_id")) == allocation
              && ((BigDecimal) previous.getFirst().get("quantity")).compareTo(qty) == 0,
          "不能复用其他用料请求的确认标识");
      return list(kind, taskId);
    }
    var available =
        ((BigDecimal) a.get("quantity"))
            .subtract((BigDecimal) a.get("used_quantity"))
            .subtract((BigDecimal) a.get("returned_quantity"));
    require(qty.compareTo(available) <= 0, "数量超过已发未用数量");
    long usage = IdWorker.getId();
    db.update(
        "UPDATE inv_issue_allocation SET used_quantity=used_quantity+? WHERE tenant_id=? AND id=?",
        qty,
        auth.tenantId(),
        allocation);
    db.update(
        "INSERT INTO"
            + " inv_usage(id,tenant_id,allocation_id,requisition_id,equipment_id,material_id,quantity,kind,status,actor_name,confirmation_key)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        usage,
        auth.tenantId(),
        allocation,
        a.get("requisition_id"),
        install ? eq : null,
        a.get("material_id"),
        qty,
        install ? "安装" : "使用",
        install ? "在用" : "已使用",
        auth.user().getDisplayName(),
        key);
    if (install)
      history(
          eq,
          Objects.toString(source.get("code")),
          "换上 " + a.get("material_name") + " × " + qty,
          "在用",
          "已安装");
    event(
        kind,
        taskId,
        install ? "换上备件" : "使用物料",
        Map.of(
            "name",
            a.get("material_name"),
            "quantity",
            qty.toPlainString(),
            "allocationId",
            Long.toString(allocation)));
    return list(kind, taskId);
  }

  @Transactional
  public Object remove(String kind, long taskId, Map<String, Object> body) {
    mutex.enter();
    var source = task(kind, taskId, true);
    long usage = id(body.get("usageId"));
    var found =
        db.queryForList(
            "SELECT u.*,m.name material_name FROM inv_usage u JOIN inv_material m ON"
                + " m.tenant_id=u.tenant_id AND m.id=u.material_id WHERE u.tenant_id=? AND u.id=?"
                + " FOR UPDATE",
            auth.tenantId(),
            usage);
    require(found.size() == 1, "在用备件不存在");
    var u = found.getFirst();
    long eq = id(u.get("equipment_id"));
    require(
        ((List<?>) source.get("equipment"))
            .stream().anyMatch(e -> id(((Map<?, ?>) e).get("id")) == eq),
        "备件不属于当前任务设备");
    if (text(u, "status").equals("已换下")) return list(kind, taskId);
    require(
        !text(body, "destination").isBlank() && text(body, "destination").length() <= 500,
        "请填写换下去向");
    db.update(
        "UPDATE inv_usage SET status='已换下',destination=?,removed_at=CURRENT_TIMESTAMP(3) WHERE"
            + " tenant_id=? AND id=?",
        text(body, "destination"),
        auth.tenantId(),
        usage);
    history(
        eq,
        Objects.toString(source.get("code")),
        "换下 " + u.get("material_name") + " × " + u.get("quantity"),
        "已换下",
        text(body, "destination"));
    event(
        kind,
        taskId,
        "换下备件",
        Map.of(
            "name",
            u.get("material_name"),
            "quantity",
            Objects.toString(u.get("quantity")),
            "reason",
            text(body, "destination")));
    return list(kind, taskId);
  }

  private void history(long eq, String code, String description, String state, String result) {
    db.update(
        "INSERT INTO"
            + " eam_equipment_history(id,tenant_id,equipment_id,record_type,record_no,occurred_at,description,operator_name,status,result)"
            + " VALUES(?,?,?,'SPARE_CHANGE',?,NOW(),?,?,?,?)",
        IdWorker.getId(),
        auth.tenantId(),
        eq,
        code,
        description,
        auth.user().getDisplayName(),
        state,
        result);
  }

  private void event(String kind, long task, String action, Object details) {
    String payload;
    try {
      payload = json.writeValueAsString(details);
    } catch (Exception e) {
      throw new IllegalArgumentException("操作记录无效");
    }
    if (kind.equals("维修"))
      db.update(
          "INSERT INTO"
              + " eam_repair_event(id,tenant_id,order_id,action,actor_id,actor_name,details_json,created_at)"
              + " VALUES(?,?,?,?,?,?,?,NOW())",
          IdWorker.getId(),
          auth.tenantId(),
          task,
          action,
          auth.userId(),
          auth.user().getDisplayName(),
          payload);
    else {
      String table = kind.equals("保养") ? "eam_maintenance_event" : "eam_inspection_event";
      db.update(
          "INSERT INTO "
              + table
              + "(id,tenant_id,task_id,actor_id,actor_name,action,details) VALUES(?,?,?,?,?,?,?)",
          IdWorker.getId(),
          auth.tenantId(),
          task,
          auth.userId(),
          auth.user().getDisplayName(),
          action,
          payload);
    }
  }
}
