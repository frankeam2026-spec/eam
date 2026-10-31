package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.windasoft.eam.dto.equipment.EquipmentBomMaterialQuery;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.equipment.EquipmentBom;
import com.windasoft.eam.entity.equipment.EquipmentChild;
import com.windasoft.eam.entity.equipment.EquipmentRelationLegacy;
import com.windasoft.eam.entity.inventory.Material;
import com.windasoft.eam.repository.inventory.MaterialRepository;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentRelationsRepository {
  private final EquipmentChildRepository children;
  private final EquipmentBomRepository bom;
  private final EquipmentRelationLegacyRepository legacy;

  private final EquipmentMapper equipment;
  private final EquipmentMediaRepository media;
  private final EquipmentQueryRepository queries;

  public EquipmentRelationsRepository(
      EquipmentMapper equipment,
      EquipmentMediaRepository media,
      EquipmentQueryRepository queries,
      EquipmentChildRepository children,
      EquipmentBomRepository bom,
      EquipmentRelationLegacyRepository legacy) {
    this.children = children;
    this.bom = bom;
    this.legacy = legacy;
    this.equipment = equipment;
    this.media = media;
    this.queries = queries;
  }

  public Equipment find(long tenant, long id) {
    return media.hydrate(
        equipment.selectOne(
            new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getTenantId, tenant)
                .eq(Equipment::getId, id)));
  }

  public int relationshipCount(long tenant, long id) {
    return children.countRelations(tenant, id);
  }

  public List<EquipmentChild> parents(long tenant, Long id) {
    return children.parents(tenant, id);
  }

  public void clearChildren(long tenant, long id) {
    children.clear(tenant, id);
  }

  public void addChild(long tenant, long parent, long child, int sort) {
    children.add(new EquipmentChild(tenant, parent, child, sort));
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

}
