package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceStandardCategory;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 仅持久化eam_maintenance_standard_category；替换范围及跨表事务由保养Service确定。 */
@Repository
public class MaintenanceStandardCategoryRepository {
  private final MaintenanceStandardCategoryMapper mapper;

  public MaintenanceStandardCategoryRepository(MaintenanceStandardCategoryMapper mapper) {
    this.mapper = mapper;
  }

  public List<MaintenanceStandardCategory> list(long tenant, Collection<Long> standards) {
    if (standards.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<MaintenanceStandardCategory>()
            .eq(MaintenanceStandardCategory::getTenantId, tenant)
            .in(MaintenanceStandardCategory::getStandardId, standards)
            .orderByAsc(
                MaintenanceStandardCategory::getStandardId,
                MaintenanceStandardCategory::getSortNo));
  }

  public int clear(long tenant, long standard) {
    return mapper.delete(
        new LambdaQueryWrapper<MaintenanceStandardCategory>()
            .eq(MaintenanceStandardCategory::getTenantId, tenant)
            .eq(MaintenanceStandardCategory::getStandardId, standard));
  }

  /** 调用方的完整保存事务必须覆盖清理和批量插入；空明细不生成无条件SQL。 */
  public void insertAll(List<MaintenanceStandardCategory> rows) {
    if (!rows.isEmpty()) {
      mapper.insert(rows);
    }
  }
}
