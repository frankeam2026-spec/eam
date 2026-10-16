package com.windasoft.eam.repository.inventory;

import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.repair.RepairOrderMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryUsageRepository {
  private final JdbcTemplate db;
  private final RepairOrderMapper repairs;

  public RepairOrder findRepair(long id) {
    return repairs.selectById(id);
  }

  public List<RepairOrder> listRepairs(long tenant) {
    return repairs.selectList(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RepairOrder>()
            .eq(RepairOrder::getTenantId, tenant)
            .eq(RepairOrder::getDeleted, 0));
  }

  public InventoryUsageRepository(JdbcTemplate db, RepairOrderMapper repairs) {
    this.db = db;
    this.repairs = repairs;
  }

  public List<Map<String, Object>> lockTask(long tenantId, Object task, String table) {
    if (!(Set.of("eam_repair_order", "eam_maintenance_task", "eam_inspection_task")
        .contains(table))) throw new IllegalArgumentException("未知任务关联类型");
    return db.queryForList(
        "SELECT id FROM " + table + " WHERE tenant_id=? AND id=? FOR UPDATE", tenantId, task);
  }

  public List<Map<String, Object>> findEquipmentByCode(long tenantId, Object value) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id,name FROM eam_equipment WHERE tenant_id=? AND"
            + " equipment_code=? AND deleted=0",
        tenantId,
        value);
  }

  public Map<String, Object> findMaintenanceEquipment(long tenantId, Object task) {
    return db.queryForMap(
        "SELECT equipment_id FROM eam_maintenance_task WHERE tenant_id=? AND id=?", tenantId, task);
  }

  public List<Map<String, Object>> findEquipment(long tenantId, Object equipmentId) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id,name FROM eam_equipment WHERE tenant_id=? AND id=?",
        tenantId,
        equipmentId);
  }

  public List<Map<String, Object>> listTaskRequests(long tenantId, Object kind, Object taskId) {
    return db.queryForList(
        "SELECT CAST(r.id AS CHAR) id,r.code,r.status,w.name warehouse_name FROM"
            + " inv_requisition r JOIN inv_warehouse w ON w.tenant_id=r.tenant_id AND"
            + " w.id=r.warehouse_id WHERE r.tenant_id=? AND r.task_kind=? AND r.task_id=?"
            + " AND r.deleted=0 ORDER BY r.created_at DESC",
        tenantId,
        kind,
        taskId);
  }

  public List<Map<String, Object>> listTaskAllocations(long tenantId, Object kind, Object taskId) {
    return db.queryForList(
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
        tenantId,
        kind,
        taskId);
  }

  public List<Map<String, Object>> listEquipmentUsage(long tenant, List<Long> equipmentIds) {
    if (equipmentIds.isEmpty()) return List.of();
    return db.queryForList(
        "SELECT u.*,m.name,m.code,m.model FROM inv_usage u JOIN inv_material m ON"
            + " m.tenant_id=u.tenant_id AND m.id=u.material_id WHERE u.tenant_id=? AND"
            + " u.equipment_id IN ("
            + String.join(",", Collections.nCopies(equipmentIds.size(), "?"))
            + ") AND u.status='在用' ORDER BY u.created_at DESC",
        java.util.stream.Stream.concat(java.util.stream.Stream.of(tenant), equipmentIds.stream())
            .toArray());
  }

  public List<Map<String, Object>> listTaskUsage(long tenantId, Object kind, Object taskId) {
    return db.queryForList(
        "SELECT u.*,m.name material_name FROM inv_usage u JOIN inv_material m ON"
            + " m.tenant_id=u.tenant_id AND m.id=u.material_id JOIN inv_issue_allocation a"
            + " ON a.tenant_id=u.tenant_id AND a.id=u.allocation_id LEFT JOIN"
            + " inv_outbound_line ol ON ol.tenant_id=a.tenant_id AND"
            + " ol.id=a.outbound_line_id LEFT JOIN inv_requisition r ON"
            + " r.tenant_id=u.tenant_id AND r.id=u.requisition_id WHERE u.tenant_id=? AND"
            + " COALESCE(ol.task_kind,r.task_kind)=? AND COALESCE(ol.task_id,r.task_id)=?"
            + " ORDER BY u.created_at,u.id",
        tenantId,
        kind,
        taskId);
  }

  public List<Map<String, Object>> loadAllocation(long tenantId, Object allocation) {
    return db.queryForList(
        "SELECT a.*,(SELECT c.currency FROM inv_cost_layer c WHERE c.tenant_id=a.tenant_id AND"
            + " c.id=a.cost_layer_id) currency,i.company_id,COALESCE(ol.department_id,CASE"
            + " r.task_kind WHEN '维修' THEN (SELECT t.department_org_id FROM eam_repair_order t"
            + " WHERE t.tenant_id=r.tenant_id AND t.id=r.task_id) WHEN '保养' THEN (SELECT"
            + " t.department_id FROM eam_maintenance_task t WHERE t.tenant_id=r.tenant_id AND"
            + " t.id=r.task_id) WHEN '点巡检' THEN (SELECT t.department_id FROM eam_inspection_task t"
            + " WHERE t.tenant_id=r.tenant_id AND t.id=r.task_id) END)"
            + " department_id,s.material_id,m.usage_type,m.name"
            + " material_name,rl.requisition_id,COALESCE(ol.task_kind,r.task_kind)"
            + " task_kind,COALESCE(ol.task_id,r.task_id) task_id FROM inv_issue_allocation a JOIN"
            + " inv_issue i ON i.tenant_id=a.tenant_id AND i.id=a.issue_id JOIN inv_balance s ON"
            + " s.tenant_id=a.tenant_id AND s.id=a.balance_id JOIN inv_material m ON"
            + " m.tenant_id=s.tenant_id AND m.id=s.material_id LEFT JOIN inv_outbound_line ol ON"
            + " ol.tenant_id=a.tenant_id AND ol.id=a.outbound_line_id LEFT JOIN"
            + " inv_requisition_line rl ON rl.tenant_id=a.tenant_id AND rl.id=a.requisition_line_id"
            + " LEFT JOIN inv_requisition r ON r.tenant_id=rl.tenant_id AND r.id=rl.requisition_id"
            + " WHERE a.tenant_id=? AND a.id=? AND i.reversed=0 FOR UPDATE",
        tenantId,
        allocation);
  }

  public List<Map<String, Object>> findUsageByKey(long tenantId, Object key) {
    return db.queryForList(
        "SELECT allocation_id,quantity,equipment_id,attribution FROM inv_usage WHERE tenant_id=?"
            + " AND confirmation_key=?",
        tenantId,
        key);
  }

  public java.math.BigDecimal usedAmount(long tenant, Object allocation) {
    Integer missing =
        db.queryForObject(
            "SELECT COUNT(*) FROM inv_usage WHERE tenant_id=? AND allocation_id=? AND cost_amount"
                + " IS NULL",
            Integer.class,
            tenant,
            allocation);
    if (missing != null && missing > 0) throw new IllegalArgumentException("历史使用成本缺失，请先核对原出库分配");
    return db.queryForObject(
        "SELECT COALESCE(SUM(cost_amount),0) FROM inv_usage WHERE tenant_id=? AND allocation_id=?",
        java.math.BigDecimal.class,
        tenant,
        allocation);
  }

  public void analyticsFacts(
      long tenant,
      long usage,
      long user,
      Object equipment,
      String attribution,
      java.math.BigDecimal cost,
      Object currency,
      Object company,
      Object department) {
    db.update(
        "UPDATE inv_usage u LEFT JOIN eam_equipment e ON e.tenant_id=u.tenant_id AND e.id=? SET"
            + " u.actor_id=?,u.equipment_id=?,u.attribution=?,u.cost_amount=?,u.currency=?,u.company_id_snapshot=?,u.department_id_snapshot=?,u.equipment_code_snapshot=e.equipment_code,u.equipment_name_snapshot=e.name"
            + " WHERE u.tenant_id=? AND u.id=?",
        equipment,
        user,
        equipment,
        attribution,
        cost,
        currency,
        company,
        department,
        tenant,
        usage);
  }

  public int addUsedQuantity(Object qty, long tenantId, Object allocation) {
    return db.update(
        "UPDATE inv_issue_allocation SET used_quantity=used_quantity+? WHERE tenant_id=? AND id=?",
        qty,
        tenantId,
        allocation);
  }

  public int insertUsage(
      Object usage,
      long tenantId,
      Object allocation,
      Object requisitionId,
      Object value,
      Object materialId,
      Object qty,
      Object value8,
      Object value9,
      Object value10,
      Object key) {
    return db.update(
        "INSERT INTO"
            + " inv_usage(id,tenant_id,allocation_id,requisition_id,equipment_id,material_id,quantity,kind,status,actor_name,confirmation_key)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        usage,
        tenantId,
        allocation,
        requisitionId,
        value,
        materialId,
        qty,
        value8,
        value9,
        value10,
        key);
  }

  public List<Map<String, Object>> lockUsage(long tenantId, Object usage) {
    return db.queryForList(
        "SELECT u.*,m.name material_name FROM inv_usage u JOIN inv_material m ON"
            + " m.tenant_id=u.tenant_id AND m.id=u.material_id WHERE u.tenant_id=? AND u.id=?"
            + " FOR UPDATE",
        tenantId,
        usage);
  }

  public int removeUsage(Object value, long tenantId, Object usage) {
    return db.update(
        "UPDATE inv_usage SET status='已换下',destination=?,removed_at=CURRENT_TIMESTAMP(3) WHERE"
            + " tenant_id=? AND id=?",
        value,
        tenantId,
        usage);
  }

  public int insertHistory(
      long recordId,
      long tenantId,
      Object eq,
      Object code,
      Object description,
      Object value,
      Object state,
      Object result) {
    return db.update(
        "INSERT INTO"
            + " eam_equipment_history(id,tenant_id,equipment_id,record_type,record_no,occurred_at,description,operator_name,status,result)"
            + " VALUES(?,?,?,'SPARE_CHANGE',?,NOW(),?,?,?,?)",
        recordId,
        tenantId,
        eq,
        code,
        description,
        value,
        state,
        result);
  }

  public int insertRepairEvent(
      long recordId,
      long tenantId,
      Object task,
      Object action,
      long userId,
      Object value,
      Object payload) {
    return db.update(
        "INSERT INTO"
            + " eam_repair_event(id,tenant_id,order_id,action,actor_id,actor_name,details_json,created_at)"
            + " VALUES(?,?,?,?,?,?,?,NOW())",
        recordId,
        tenantId,
        task,
        action,
        userId,
        value,
        payload);
  }

  public int insertTaskEvent(
      long recordId,
      long tenantId,
      Object task,
      long userId,
      Object value,
      Object action,
      Object payload,
      String table) {
    if (!(Set.of("eam_maintenance_event", "eam_inspection_event").contains(table)))
      throw new IllegalArgumentException("未知任务关联类型");
    return db.update(
        "INSERT INTO "
            + table
            + "(id,tenant_id,task_id,actor_id,actor_name,action,details) VALUES(?,?,?,?,?,?,?)",
        recordId,
        tenantId,
        task,
        userId,
        value,
        action,
        payload);
  }
}
