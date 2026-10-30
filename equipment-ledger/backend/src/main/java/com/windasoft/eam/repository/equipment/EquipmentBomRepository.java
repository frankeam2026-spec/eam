package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.equipment.EquipmentBom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentBomRepository {
  private final JdbcTemplate db;
  private final EquipmentBomMapper mapper;

  public EquipmentBomRepository(EquipmentBomMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
  }

  public int count(long tenant, long equipment, long material) {
    return Math.toIntExact(
        mapper.selectCount(
            new LambdaQueryWrapper<EquipmentBom>()
                .eq(EquipmentBom::getTenantId, tenant)
                .eq(EquipmentBom::getEquipmentId, equipment)
                .eq(EquipmentBom::getMaterialId, material)));
  }

  public void clear(long tenant, long equipment) {
    mapper.delete(
        new LambdaQueryWrapper<EquipmentBom>()
            .eq(EquipmentBom::getTenantId, tenant)
            .eq(EquipmentBom::getEquipmentId, equipment));
  }

  public void add(EquipmentBom relation) {
    mapper.insert(relation);
  }

  /** 关联展示查询集中于所属Repository，单条与批量调用共用。 */
  public List<Map<String, Object>> viewRows(long tenant, List<Long> ids) {
    if (ids.isEmpty()) return List.of();
    String marks = String.join(",", Collections.nCopies(ids.size(), "?"));
    var args = new ArrayList<Object>();
    args.add(tenant);
    args.addAll(ids);
    return db.queryForList(
        "SELECT b.equipment_id __equipment_id,CAST(b.material_id AS CHAR)"
            + " materialId,COALESCE(m.code,b.code) code,COALESCE(m.name,b.name)"
            + " name,COALESCE(m.model,b.model) specification,COALESCE(m.unit,b.unit)"
            + " unit,CAST(b.quantity AS CHAR) quantity FROM eam_equipment_bom b LEFT JOIN"
            + " inv_material m ON m.tenant_id=b.tenant_id AND m.id=b.material_id WHERE"
            + " b.tenant_id=? AND b.equipment_id IN ("
            + marks
            + ") ORDER BY b.equipment_id,b.sort_no,b.material_id",
        args.toArray());
  }
}
