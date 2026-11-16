package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.maintenance.MaintenancePlanPerson;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 仅持久化eam_maintenance_plan_person；替换范围及跨表事务由保养Service确定。 */
@Repository
public class MaintenancePlanPersonRepository {
  private final MaintenancePlanPersonMapper mapper;

  public MaintenancePlanPersonRepository(MaintenancePlanPersonMapper mapper) {
    this.mapper = mapper;
  }

  public List<MaintenancePlanPerson> list(long tenant, Collection<Long> plans) {
    if (plans.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<MaintenancePlanPerson>()
            .eq(MaintenancePlanPerson::getTenantId, tenant)
            .in(MaintenancePlanPerson::getPlanId, plans)
            .orderByAsc(MaintenancePlanPerson::getPlanId, MaintenancePlanPerson::getSortNo));
  }

  public int clear(long tenant, long plan) {
    return mapper.delete(
        new LambdaQueryWrapper<MaintenancePlanPerson>()
            .eq(MaintenancePlanPerson::getTenantId, tenant)
            .eq(MaintenancePlanPerson::getPlanId, plan));
  }

  /** 调用方的完整保存事务必须覆盖清理和批量插入；空明细不生成无条件SQL。 */
  public void insertAll(List<MaintenancePlanPerson> rows) {
    if (rows.size() == 1) {
      mapper.insert(rows.getFirst());
    } else if (!rows.isEmpty()) {
      mapper.insert(rows);
    }
  }
}
