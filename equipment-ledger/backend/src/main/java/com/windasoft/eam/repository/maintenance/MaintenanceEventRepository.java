package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceEvent;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 保养事件表的主仓储；事件产生时机和审计详情由业务 Service 决定。 */
@Repository
public class MaintenanceEventRepository {
  private final MaintenanceEventMapper mapper;

  public MaintenanceEventRepository(MaintenanceEventMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(MaintenanceEvent event) {
    mapper.insert(event);
  }

  public List<MaintenanceEvent> list(long tenant, long task) {
    return mapper.selectList(
        new LambdaQueryWrapper<MaintenanceEvent>()
            .eq(MaintenanceEvent::getTenantId, tenant)
            .eq(MaintenanceEvent::getTaskId, task)
            .orderByAsc(MaintenanceEvent::getCreatedAt, MaintenanceEvent::getId));
  }
}
