package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceTaskPerson;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 仅维护eam_maintenance_task_person；关联替换顺序和事务由保养Service决定。 */
@Repository
public class MaintenanceTaskPersonRepository {
  private final MaintenanceTaskPersonMapper mapper;

  public MaintenanceTaskPersonRepository(MaintenanceTaskPersonMapper mapper) {
    this.mapper = mapper;
  }

  public int clear(long tenant, long task, Collection<String> roles) {
    if (roles.isEmpty()) {
      return 0;
    }
    return mapper.delete(
        new LambdaQueryWrapper<MaintenanceTaskPerson>()
            .eq(MaintenanceTaskPerson::getTenantId, tenant)
            .eq(MaintenanceTaskPerson::getTaskId, task)
            .in(MaintenanceTaskPerson::getRoleCode, roles));
  }

  public void insertAll(List<MaintenanceTaskPerson> rows) {
    if (rows.size() == 1) {
      mapper.insert(rows.getFirst());
    } else if (!rows.isEmpty()) {
      mapper.insert(rows);
    }
  }
}
