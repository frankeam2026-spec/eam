package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceItemSpare;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 仅持久化eam_maintenance_item_spare；替换范围及跨表事务由保养Service确定。 */
@Repository
public class MaintenanceItemSpareRepository {
  private final MaintenanceItemSpareMapper mapper;

  public MaintenanceItemSpareRepository(MaintenanceItemSpareMapper mapper) {
    this.mapper = mapper;
  }

  public List<MaintenanceItemSpare> list(long tenant, Collection<Long> standards) {
    if (standards.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<MaintenanceItemSpare>()
            .eq(MaintenanceItemSpare::getTenantId, tenant)
            .in(MaintenanceItemSpare::getStandardId, standards)
            .orderByAsc(MaintenanceItemSpare::getStandardId, MaintenanceItemSpare::getSortNo));
  }

  public int clear(long tenant, long standard) {
    return mapper.delete(
        new LambdaQueryWrapper<MaintenanceItemSpare>()
            .eq(MaintenanceItemSpare::getTenantId, tenant)
            .eq(MaintenanceItemSpare::getStandardId, standard));
  }

  /** 调用方的完整保存事务必须覆盖清理和批量插入；空明细不生成无条件SQL。 */
  public void insertAll(List<MaintenanceItemSpare> rows) {
    if (!rows.isEmpty()) {
      mapper.insert(rows);
    }
  }
}
