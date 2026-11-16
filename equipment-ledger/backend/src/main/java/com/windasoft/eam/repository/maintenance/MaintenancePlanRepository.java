package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.maintenance.MaintenancePlan;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 计划主体数据访问；周期、验收规则及关联替换和授权属于业务Service。 */
@Repository
public class MaintenancePlanRepository {
  private final MaintenancePlanMapper mapper;

  public MaintenancePlanRepository(MaintenancePlanMapper mapper) {
    this.mapper = mapper;
  }

  public MaintenancePlan find(long tenant, long id, boolean lock) {
    return mapper.selectOne(
        new LambdaQueryWrapper<MaintenancePlan>()
            .eq(MaintenancePlan::getTenantId, tenant)
            .eq(MaintenancePlan::getId, id)
            .last(lock ? "FOR UPDATE" : ""));
  }

  public List<MaintenancePlan> list(long tenant, Collection<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<MaintenancePlan>()
            .eq(MaintenancePlan::getTenantId, tenant)
            .in(MaintenancePlan::getId, ids));
  }

  public int insert(MaintenancePlan row) {
    return mapper.insert(row);
  }

  /** 主体已由保存用例加锁，版本与更新时间沿用原数据库递增语义。 */
  public int update(MaintenancePlan row) {
    return mapper.update(
        row,
        new LambdaUpdateWrapper<MaintenancePlan>()
            .eq(MaintenancePlan::getTenantId, row.getTenantId())
            .eq(MaintenancePlan::getId, row.getId())
            .setSql("row_version=row_version+1,updated_at=CURRENT_TIMESTAMP"));
  }

  public int delete(long tenant, long id) {
    return mapper.delete(
        new LambdaQueryWrapper<MaintenancePlan>()
            .eq(MaintenancePlan::getTenantId, tenant)
            .eq(MaintenancePlan::getId, id));
  }
}
