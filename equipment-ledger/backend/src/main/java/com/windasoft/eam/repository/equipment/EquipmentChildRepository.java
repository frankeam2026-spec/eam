package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.equipment.EquipmentChild;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentChildRepository {
  private final JdbcTemplate db;
  private final EquipmentChildMapper mapper;

  public EquipmentChildRepository(EquipmentChildMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
  }

  public int countRelations(long tenant, long id) {
    return Math.toIntExact(
        mapper.selectCount(
            new LambdaQueryWrapper<EquipmentChild>()
                .eq(EquipmentChild::getTenantId, tenant)
                .and(
                    q ->
                        q.eq(EquipmentChild::getParentId, id)
                            .or()
                            .eq(EquipmentChild::getChildId, id))));
  }

  public List<EquipmentChild> parents(long tenant, long child) {
    return mapper.selectList(
        new LambdaQueryWrapper<EquipmentChild>()
            .eq(EquipmentChild::getTenantId, tenant)
            .eq(EquipmentChild::getChildId, child));
  }

  public void clear(long tenant, long parent) {
    mapper.delete(
        new LambdaQueryWrapper<EquipmentChild>()
            .eq(EquipmentChild::getTenantId, tenant)
            .eq(EquipmentChild::getParentId, parent));
  }

  public void add(EquipmentChild relation) {
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
        "SELECT c.parent_id __equipment_id,CAST(e.id AS CHAR) equipmentId,e.equipment_code"
            + " code,e.name,e.model,e.brand FROM eam_equipment_child c JOIN eam_equipment e"
            + " ON e.tenant_id=c.tenant_id AND e.id=c.child_id WHERE c.tenant_id=? AND"
            + " c.parent_id IN ("
            + marks
            + ") AND e.deleted=0 ORDER BY c.parent_id,c.sort_no,e.id",
        args.toArray());
  }
}
