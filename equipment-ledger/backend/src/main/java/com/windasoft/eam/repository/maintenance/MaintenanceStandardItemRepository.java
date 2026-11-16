package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceStandardItem;
import org.springframework.stereotype.Repository;

/** 标准项目自身数据访问；引用是否合法由保养业务服务决定。 */
@Repository
public class MaintenanceStandardItemRepository {
  private final MaintenanceStandardItemMapper mapper;

  public MaintenanceStandardItemRepository(MaintenanceStandardItemMapper mapper) {
    this.mapper = mapper;
  }

  public long countByType(long tenant, long type) {
    return mapper.selectCount(
        new LambdaQueryWrapper<MaintenanceStandardItem>()
            .eq(MaintenanceStandardItem::getTenantId, tenant)
            .eq(MaintenanceStandardItem::getTypeId, type));
  }
}
