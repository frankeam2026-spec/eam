package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.windasoft.eam.dto.equipment.EquipmentBomMaterialQuery;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.inventory.Material;
import com.windasoft.eam.repository.inventory.MaterialRepository;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentRelationsRepository {
  private final JdbcTemplate db;
  private final EquipmentMapper equipment;
  private final EquipmentMediaRepository media;
  private final EquipmentQueryRepository queries;
  private final MaterialRepository materials;

  public EquipmentRelationsRepository(
      JdbcTemplate db,
      EquipmentMapper equipment,
      EquipmentMediaRepository media,
      EquipmentQueryRepository queries,
      MaterialRepository materials) {
    this.db = db;
    this.equipment = equipment;
    this.media = media;
    this.queries = queries;
    this.materials = materials;
  }

  public Equipment find(long tenant, long id) {
    return media.hydrate(
        equipment.selectOne(
            new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getTenantId, tenant)
                .eq(Equipment::getId, id)));
  }

  public int relationshipCount(long tenant, long id) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_equipment_child WHERE tenant_id=? AND (parent_id=? OR"
            + " child_id=?)",
        Integer.class,
        tenant,
        id,
        id);
  }

  public List<Map<String, Object>> parents(long tenant, Long id) {
    return db.queryForList(
        "SELECT parent_id FROM eam_equipment_child WHERE tenant_id=? AND child_id=?", tenant, id);
  }

  public void clearBom(long tenant, long id) {
    db.update("DELETE FROM eam_equipment_bom WHERE tenant_id=? AND equipment_id=?", tenant, id);
  }

  public void clearLegacy(long tenant, long id) {
    db.update(
        "DELETE FROM eam_equipment_relation_legacy WHERE tenant_id=? AND equipment_id=?",
        tenant,
        id);
  }

  public void removeLegacy(long tenant, long id, String kind, Object index) {
    db.update(
        "DELETE FROM eam_equipment_relation_legacy WHERE tenant_id=? AND equipment_id=? AND kind=?"
            + " AND source_index=?",
        tenant,
        id,
        kind,
        index);
  }

  public List<Map<String, Object>> legacy(long tenant, long id, String kind) {
    return db.queryForList(
        "SELECT source_index,snapshot_json FROM eam_equipment_relation_legacy WHERE tenant_id=? AND"
            + " equipment_id=? AND kind=?",
        tenant,
        id,
        kind);
  }

  public List<Map<String, Object>> lockMaterial(long tenant, long id) {
    return db.queryForList(
        "SELECT id,code,name,model,unit,enabled FROM inv_material WHERE tenant_id=? AND id=? FOR"
            + " UPDATE",
        tenant,
        id);
  }

  public int bomCount(long tenant, long id, long material) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_equipment_bom WHERE tenant_id=? AND equipment_id=? AND"
            + " material_id=?",
        Integer.class,
        tenant,
        id,
        material);
  }

  public void clearChildren(long tenant, long id) {
    db.update("DELETE FROM eam_equipment_child WHERE tenant_id=? AND parent_id=?", tenant, id);
  }

  public void addChild(long tenant, long parent, long child, int sort) {
    db.update("INSERT INTO eam_equipment_child VALUES(?,?,?,?)", tenant, parent, child, sort);
  }

  public void addBom(long tenant, long parent, Map<String, Object> m, int sort) {
    db.update(
        "INSERT INTO eam_equipment_bom VALUES(?,?,?,?,?,?,?,?,?)",
        tenant,
        parent,
        m.get("id"),
        m.get("quantity"),
        m.get("code"),
        m.get("name"),
        m.get("model"),
        m.get("unit"),
        sort);
  }

  public List<Map<String, Object>> children(long tenant, long id) {
    return queries.children(tenant, id);
  }

  public List<Map<String, Object>> bom(long tenant, long id) {
    return queries.bom(tenant, id);
  }

  public Page<Equipment> candidates(
      long tenant,
      Long company,
      Long parent,
      Set<Long> excluded,
      String keyword,
      int page,
      int size,
      Consumer<LambdaQueryWrapper<Equipment>> scope) {
    var q =
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .eq(Equipment::getCompanyOrgId, company)
            .and(
                !keyword.isBlank(),
                w ->
                    w.like(Equipment::getEquipmentCode, keyword)
                        .or()
                        .like(Equipment::getName, keyword)
                        .or()
                        .like(Equipment::getModel, keyword));
    scope.accept(q);
    if (!excluded.isEmpty()) q.notIn(Equipment::getId, excluded);
    if (parent == null)
      q.apply("id NOT IN (SELECT child_id FROM eam_equipment_child WHERE tenant_id={0})", tenant);
    else
      q.apply(
          "id NOT IN (SELECT child_id FROM eam_equipment_child WHERE tenant_id={0} AND"
              + " parent_id<>{1})",
          tenant, parent);
    q.orderByAsc(Equipment::getEquipmentCode, Equipment::getId);
    return equipment.selectPage(new Page<>(Math.max(1, page), Math.max(1, Math.min(100, size))), q);
  }

  public long materialTotal(long tenant, EquipmentBomMaterialQuery query) {
    return materials.countEnabledCandidates(tenant, query.keyword());
  }

  public List<Material> materials(long tenant, EquipmentBomMaterialQuery query) {
    return materials.enabledCandidates(tenant, query.keyword(), query.page(), query.size());
  }
}
