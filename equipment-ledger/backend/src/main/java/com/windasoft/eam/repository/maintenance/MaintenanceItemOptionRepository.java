package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceItemOption;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 仅持久化eam_maintenance_item_option；替换范围及跨表事务由保养Service确定。 */
@Repository
public class MaintenanceItemOptionRepository {
  private final MaintenanceItemOptionMapper mapper;

  public MaintenanceItemOptionRepository(MaintenanceItemOptionMapper mapper) {
    this.mapper = mapper;
  }

  public List<MaintenanceItemOption> list(long tenant, Collection<Long> standards) {
    if (standards.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<MaintenanceItemOption>()
            .eq(MaintenanceItemOption::getTenantId, tenant)
            .in(MaintenanceItemOption::getStandardId, standards)
            .orderByAsc(MaintenanceItemOption::getStandardId, MaintenanceItemOption::getSortNo));
  }

  public int clear(long tenant, long standard) {
    return mapper.delete(
        new LambdaQueryWrapper<MaintenanceItemOption>()
            .eq(MaintenanceItemOption::getTenantId, tenant)
            .eq(MaintenanceItemOption::getStandardId, standard));
  }

  /** 调用方的完整保存事务必须覆盖清理和批量插入；空明细不生成无条件SQL。 */
  public void insertAll(List<MaintenanceItemOption> rows) {
    if (!rows.isEmpty()) {
      mapper.insert(rows);
    }
  }
}
