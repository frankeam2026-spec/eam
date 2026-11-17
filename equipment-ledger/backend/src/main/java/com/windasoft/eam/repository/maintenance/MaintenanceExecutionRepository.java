package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceExecution;
import org.springframework.stereotype.Repository;

/** 仅维护eam_maintenance_execution；关联替换顺序和事务由保养Service决定。 */
@Repository
public class MaintenanceExecutionRepository {
  private final MaintenanceExecutionMapper mapper;

  public MaintenanceExecutionRepository(MaintenanceExecutionMapper mapper) {
    this.mapper = mapper;
  }

  /** 删除主体由数据库级联清理项目、图片和阶段附件；协同人由业务事务单独协调。 */
  public int delete(long tenant, long task) {
    return mapper.delete(
        new LambdaQueryWrapper<MaintenanceExecution>()
            .eq(MaintenanceExecution::getTenantId, tenant)
            .eq(MaintenanceExecution::getTaskId, task));
  }

  public int insert(MaintenanceExecution row) {
    return mapper.insert(row);
  }
}
