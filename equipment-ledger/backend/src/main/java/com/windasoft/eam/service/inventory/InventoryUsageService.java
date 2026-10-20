package com.windasoft.eam.service.inventory;

import static com.windasoft.eam.service.inventory.InventoryService.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.inventory.InventoryMoney;
import com.windasoft.eam.repository.inventory.InventoryUsageRepository;
import com.windasoft.eam.service.inspection.InspectionService;
import com.windasoft.eam.service.maintenance.MaintenanceService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryUsageService {
  private final InventoryUsageRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final InventoryService masters;
  private final InventoryMutex mutex;
  private final MaintenanceService maintenance;
  private final InspectionService inspection;
  private final ObjectMapper json;

  public InventoryUsageService(
      InventoryUsageRepository repository,
      AuthContext auth,
      PermissionService permissions,
      InventoryService masters,
      InventoryMutex mutex,
      MaintenanceService maintenance,
      InspectionService inspection,
      ObjectMapper json) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.masters = masters;
    this.mutex = mutex;
    this.maintenance = maintenance;
    this.inspection = inspection;
    this.json = json;
  }

  private Map<String, Object> task(String kind, long task, boolean write) {
    if (write) {
      require(
          Set.of("维修", "保养", "点巡检").contains(kind),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0018"));
      String table =
          kind.equals("维修")
              ? "eam_repair_order"
              : kind.equals("保养") ? "v_maintenance_task" : "v_inspection_task";
      repository.lockTask(auth.tenantId(), task, table);
    }
    if (kind.equals("维修")) {
      var order = repository.findRepair(task);
      require(
          order != null && order.getTenantId() == auth.tenantId() && order.getDeleted() == 0,
          com.windasoft.eam.common.MessageContext.text("server.repair.message0012"));
      permissions.checkRepair(order, "查看");
      if (write)
        require(
            Objects.equals(order.getAssigneeUserId(), auth.userId())
                && Set.of("处理中", "挂起").contains(order.getStatus()),
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0244"));
      var eq = repository.findEquipmentByCode(auth.tenantId(), order.getEquipmentCode());
      require(
          eq.size() == 1,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0245"));
      return Map.of("equipment", eq, "code", order.getOrderNo());
    }
    require(
        Set.of("保养", "点巡检").contains(kind),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0018"));
    var view = kind.equals("保养") ? maintenance.task(task) : inspection.task(task);
    if (write)
      require(
          Boolean.TRUE.equals(view.get("canExecute"))
              && Objects.equals(text(view, "ownerId"), Long.toString(auth.userId()))
              && Set.of("执行中", "挂起").contains(text(view, "status")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0246"));
    if (kind.equals("点巡检"))
      return Map.of(
          "equipment", view.getOrDefault("equipment", List.of()), "code", view.get("code"));
    var raw = repository.findMaintenanceEquipment(auth.tenantId(), task);
    var eq = repository.findEquipment(auth.tenantId(), raw.get("equipment_id"));
    return Map.of("equipment", eq, "code", view.get("code"));
  }

  public Object list(String kind, long taskId) {
    var source = task(kind, taskId, false);
    var output = new LinkedHashMap<String, Object>(source);
    output.put(
        "requests",
        repository.listTaskRequests(auth.tenantId(), kind, taskId).stream()
            .map(masters::view)
            .toList());
    output.put(
        "allocations",
        repository.listTaskAllocations(auth.tenantId(), kind, taskId).stream()
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
            : repository.listEquipmentUsage(auth.tenantId(), equipmentIds).stream()
                .map(this::usageView)
                .toList());
    output.put(
        "usage",
        repository.listTaskUsage(auth.tenantId(), kind, taskId).stream()
            .map(this::usageView)
            .toList());
    return output;
  }

  private Map<String, Object> usageView(Map<String, Object> row) {
    var value = masters.view(row);
    value.remove("costAmount");
    value.remove("currency");
    return value;
  }

  @Transactional
  public Object use(String kind, long taskId, Map<String, Object> body) {
    mutex.enter();
    var source = task(kind, taskId, true);
    long allocation = id(body.get("allocationId"));
    var found = repository.loadAllocation(auth.tenantId(), allocation);
    require(
        found.size() == 1,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0247"));
    var a = found.getFirst();
    require(
        text(a, "task_kind").equals(kind) && id(a.get("task_id")) == taskId,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0248"));
    var qty = InventoryFlowService.decimal(body.get("quantity"), false);

    boolean install = text(a, "usage_type").equals("可安装");
    var equipment = (List<Map<String, Object>>) source.get("equipment");
    String requested = Objects.toString(body.get("equipmentId"), "");
    long eq = requested.isBlank() ? 0 : id(requested);
    String attribution = text(body, "attribution");
    if (eq == 0 && equipment.size() == 1) eq = id(equipment.getFirst().get("id"));
    if (eq > 0) {
      final long selectedEquipment = eq;
      require(
          equipment.stream().anyMatch(e -> id(e.get("id")) == selectedEquipment),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0249"));
      attribution = "equipment";
    } else {
      require(
          !install, com.windasoft.eam.common.MessageContext.text("server.inventory.message0250"));
      require(
          attribution.equals("shared"),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0251"));
    }
    String key = text(body, "confirmationKey");
    require(
        key.matches("[a-zA-Z0-9-]{16,64}"),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0066"));
    var previous = repository.findUsageByKey(auth.tenantId(), key);
    if (!previous.isEmpty()) {
      require(
          id(previous.getFirst().get("allocation_id")) == allocation
              && ((BigDecimal) previous.getFirst().get("quantity")).compareTo(qty) == 0,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0252"));
      require(
          Objects.equals(
                  Objects.toString(previous.getFirst().get("equipment_id"), "0"), Long.toString(eq))
              && text(previous.getFirst(), "attribution").equals(attribution),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0253"));
      return list(kind, taskId);
    }
    var available =
        ((BigDecimal) a.get("quantity"))
            .subtract((BigDecimal) a.get("used_quantity"))
            .subtract((BigDecimal) a.get("returned_quantity"));
    require(
        qty.compareTo(available) <= 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0254"));
    BigDecimal remainingCost =
        ((BigDecimal) a.get("amount"))
            .subtract((BigDecimal) a.get("returned_amount"))
            .subtract(repository.usedAmount(auth.tenantId(), allocation));
    require(
        remainingCost.signum() >= 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0255"));
    BigDecimal cost =
        com.windasoft.eam.common.inventory.UsageCost.portion(
            (BigDecimal) a.get("quantity"),
            (BigDecimal) a.get("amount"),
            qty,
            available,
            remainingCost);
    long usage = IdWorker.getId();
    repository.addUsedQuantity(qty, auth.tenantId(), allocation);
    repository.insertUsage(
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
    repository.analyticsFacts(
        auth.tenantId(),
        usage,
        auth.userId(),
        eq > 0 ? eq : null,
        attribution,
        cost,
        a.get("currency"),
        a.get("company_id"),
        a.get("department_id"));
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
    var found = repository.lockUsage(auth.tenantId(), usage);
    require(
        found.size() == 1,
        com.windasoft.eam.common.MessageContext.text("server.repair.message0023"));
    var u = found.getFirst();
    long eq = id(u.get("equipment_id"));
    require(
        ((List<?>) source.get("equipment"))
            .stream().anyMatch(e -> id(((Map<?, ?>) e).get("id")) == eq),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0256"));
    if (text(u, "status").equals("已换下")) return list(kind, taskId);
    require(
        !text(body, "destination").isBlank() && text(body, "destination").length() <= 500,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0257"));
    repository.removeUsage(text(body, "destination"), auth.tenantId(), usage);
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
    repository.insertHistory(
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
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.inventory.message0028");
    }
    if (kind.equals("维修"))
      repository.insertRepairEvent(
          IdWorker.getId(),
          auth.tenantId(),
          task,
          action,
          auth.userId(),
          auth.user().getDisplayName(),
          payload);
    else {
      String table = kind.equals("保养") ? "eam_maintenance_event" : "eam_inspection_event";
      repository.insertTaskEvent(
          IdWorker.getId(),
          auth.tenantId(),
          task,
          auth.userId(),
          auth.user().getDisplayName(),
          action,
          payload,
          table);
    }
  }
}
