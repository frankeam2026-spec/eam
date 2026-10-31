package com.windasoft.eam.repository.equipment;

import com.windasoft.eam.entity.equipment.EquipmentOwner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 设备负责人关联表的数据访问。跨对象校验、事务及更新顺序由业务 Service 负责。 */
@Repository
public class EquipmentOwnerRepository {
  private final JdbcTemplate db;

  public EquipmentOwnerRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<EquipmentOwner> list(long tenant, long equipment) {
    return db.query(
        "SELECT tenant_id,equipment_id,user_id,owner_name,sort_no"
            + " FROM eam_equipment_owner WHERE tenant_id=? AND equipment_id=?"
            + " ORDER BY sort_no,user_id",
        EquipmentOwnerRepository::mapRow,
        tenant,
        equipment);
  }

  /** 分页关联只读取一次或按 500 个设备 ID 分批读取，保持负责人排序。 */
  public List<EquipmentOwner> listByEquipmentIds(long tenant, List<Long> equipmentIds) {
    if (equipmentIds.isEmpty()) return List.of();
    var rows = new ArrayList<EquipmentOwner>();
    for (int start = 0; start < equipmentIds.size(); start += 500) {
      var batch = equipmentIds.subList(start, Math.min(start + 500, equipmentIds.size()));
      String marks = String.join(",", Collections.nCopies(batch.size(), "?"));
      var args = new ArrayList<Object>();
      args.add(tenant);
      args.addAll(batch);
      rows.addAll(
          db.query(
              "SELECT tenant_id,equipment_id,user_id,owner_name,sort_no"
                  + " FROM eam_equipment_owner WHERE tenant_id=? AND equipment_id IN ("
                  + marks + ") ORDER BY equipment_id,sort_no,user_id",
              EquipmentOwnerRepository::mapRow,
              args.toArray()));
    }
    return rows;
  }

  private static EquipmentOwner mapRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new EquipmentOwner(
        rs.getLong("tenant_id"),
        rs.getLong("equipment_id"),
        rs.getLong("user_id"),
        rs.getString("owner_name"),
        rs.getInt("sort_no"));
  }

  public void clear(long tenant, long equipment) {
    db.update(
        "DELETE FROM eam_equipment_owner WHERE tenant_id=? AND equipment_id=?",
        tenant,
        equipment);
  }

  public void add(EquipmentOwner owner) {
    db.update(
        "INSERT INTO eam_equipment_owner(tenant_id,equipment_id,user_id,owner_name,sort_no)"
            + " VALUES(?,?,?,?,?)",
        owner.getTenantId(),
        owner.getEquipmentId(),
        owner.getUserId(),
        owner.getOwnerName(),
        owner.getSortNo());
  }
}
