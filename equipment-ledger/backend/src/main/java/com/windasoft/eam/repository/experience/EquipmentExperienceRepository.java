package com.windasoft.eam.repository.experience;

import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.equipment.EquipmentMapper;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.repair.RepairOrderMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentExperienceRepository {
  private final JdbcTemplate db;
  private final EquipmentMapper equipment;
  private final RepairOrderMapper repairs;
  private final EquipmentQueryRepository equipmentQueries;

  public EquipmentExperienceRepository(
      JdbcTemplate db,
      EquipmentMapper equipment,
      RepairOrderMapper repairs,
      EquipmentQueryRepository equipmentQueries) {
    this.db = db;
    this.equipment = equipment;
    this.repairs = repairs;
    this.equipmentQueries = equipmentQueries;
  }

  public Equipment findEquipment(long id) {
    return equipment.selectById(id);
  }

  public List<RepairOrder> findRepairRows(long tenant, String code) {
    return repairs.selectList(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RepairOrder>()
            .eq(RepairOrder::getTenantId, tenant)
            .eq(RepairOrder::getEquipmentCode, code)
            .eq(RepairOrder::getDeleted, 0));
  }

  public List<Map<String, Object>> listMaintenanceIds(long tenantId, Object eid) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id FROM v_maintenance_task WHERE tenant_id=? AND"
            + " equipment_id=?",
        tenantId,
        eid);
  }

  public List<Map<String, Object>> listInstalled(long tenantId, Object id) {
    return equipmentQueries.installedSpares(tenantId, Long.parseLong(id.toString()));
  }

  public Map<String, String> userNames(long tenant, Set<String> ids) {
    if (ids.isEmpty()) return Map.of();
    var args = new ArrayList<Object>();
    args.add(tenant);
    args.addAll(ids);
    var out = new HashMap<String, String>();
    db.queryForList(
            "SELECT CAST(id AS CHAR) id,display_name FROM eam_sys_user WHERE tenant_id=? AND id IN"
                + " ("
                + String.join(",", Collections.nCopies(ids.size(), "?"))
                + ")",
            args.toArray())
        .forEach(r -> out.put(r.get("id").toString(), Objects.toString(r.get("display_name"), "")));
    return out;
  }

  public List<Map<String, Object>> listAnomalyEvents(long tenantId, Object id) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id,action,actor_name actor,created_at time FROM"
            + " eam_experience_event WHERE tenant_id=? AND object_kind='anomaly' AND"
            + " object_id=?",
        tenantId,
        id);
  }

  public List<Map<String, Object>> listSpareEvents(long tenantId, Object id) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id,record_no code,description action,operator_name"
            + " actor,occurred_at time,'spare' kind FROM eam_equipment_history WHERE"
            + " tenant_id=? AND equipment_id=? AND record_type='SPARE_CHANGE'",
        tenantId,
        id);
  }

  public List<Map<String, Object>> listTaskEvents(
      long tenantId, Object task, String table, String key) {
    if (!Map.of(
            "eam_repair_event",
            "order_id",
            "eam_maintenance_event",
            "task_id",
            "eam_inspection_event",
            "task_id")
        .getOrDefault(table, "")
        .equals(key))
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.experience.message0002");
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id,action,actor_name actor,created_at time FROM "
            + table
            + " WHERE tenant_id=? AND "
            + key
            + "=?",
        tenantId,
        task);
  }
}
