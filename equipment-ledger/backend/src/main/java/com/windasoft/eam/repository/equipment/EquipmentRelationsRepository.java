package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.windasoft.eam.entity.equipment.Equipment;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.stereotype.Repository;

/** 设备层级候选项专用只读查询，关联关系的写入归属 EquipmentChildRepository。 */
@Repository
public class EquipmentRelationsRepository {
  private final EquipmentMapper equipment;

  public EquipmentRelationsRepository(EquipmentMapper equipment) {
    this.equipment = equipment;
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
