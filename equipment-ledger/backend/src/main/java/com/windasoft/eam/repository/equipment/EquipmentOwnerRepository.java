package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.equipment.EquipmentOwner;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 设备负责人关联表的数据访问。跨对象校验、事务及更新顺序由业务 Service 负责。 */
@Repository
public class EquipmentOwnerRepository {
  private final EquipmentOwnerMapper mapper;

  public EquipmentOwnerRepository(EquipmentOwnerMapper mapper) {
    this.mapper = mapper;
  }

  public List<EquipmentOwner> list(long tenant, long equipment) {
    return mapper.selectList(
        new LambdaQueryWrapper<EquipmentOwner>()
            .eq(EquipmentOwner::getTenantId, tenant)
            .eq(EquipmentOwner::getEquipmentId, equipment)
            .orderByAsc(EquipmentOwner::getSortNo, EquipmentOwner::getUserId));
  }

  /** 分页关联批量读取，按设备、负责人顺序排序，避免逐设备查询。 */
  public List<EquipmentOwner> listByEquipmentIds(long tenant, List<Long> equipmentIds) {
    if (equipmentIds.isEmpty()) return List.of();
    var rows = new ArrayList<EquipmentOwner>();
    for (int start = 0; start < equipmentIds.size(); start += 500) {
      var batch = equipmentIds.subList(start, Math.min(start + 500, equipmentIds.size()));
      rows.addAll(
          mapper.selectList(
              new LambdaQueryWrapper<EquipmentOwner>()
                  .eq(EquipmentOwner::getTenantId, tenant)
                  .in(EquipmentOwner::getEquipmentId, batch)
                  .orderByAsc(
                      EquipmentOwner::getEquipmentId,
                      EquipmentOwner::getSortNo,
                      EquipmentOwner::getUserId)));
    }
    return rows;
  }

  public void clear(long tenant, long equipment) {
    mapper.delete(
        new LambdaQueryWrapper<EquipmentOwner>()
            .eq(EquipmentOwner::getTenantId, tenant)
            .eq(EquipmentOwner::getEquipmentId, equipment));
  }

  public void add(EquipmentOwner owner) {
    mapper.insert(owner);
  }
}
