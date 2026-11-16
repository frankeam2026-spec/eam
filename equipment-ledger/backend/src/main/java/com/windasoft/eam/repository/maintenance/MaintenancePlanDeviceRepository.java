package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.maintenance.MaintenancePlanDevice;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 设备安排主体及游标数据访问，不写人员和项目，也不决定周期或任务状态。 */
@Repository
public class MaintenancePlanDeviceRepository {
  private final MaintenancePlanDeviceMapper mapper;

  public MaintenancePlanDeviceRepository(MaintenancePlanDeviceMapper mapper) {
    this.mapper = mapper;
  }

  public MaintenancePlanDevice find(long tenant, long plan, long id, boolean lock) {
    return mapper.selectOne(
        new LambdaQueryWrapper<MaintenancePlanDevice>()
            .eq(MaintenancePlanDevice::getTenantId, tenant)
            .eq(MaintenancePlanDevice::getPlanId, plan)
            .eq(MaintenancePlanDevice::getId, id)
            .last(lock ? "FOR UPDATE" : ""));
  }

  public List<MaintenancePlanDevice> list(long tenant, long plan) {
    return mapper.selectList(
        new LambdaQueryWrapper<MaintenancePlanDevice>()
            .eq(MaintenancePlanDevice::getTenantId, tenant)
            .eq(MaintenancePlanDevice::getPlanId, plan)
            .orderByAsc(MaintenancePlanDevice::getId));
  }

  public int insert(MaintenancePlanDevice row) {
    return mapper.insert(row);
  }

  /** 编辑已锁定安排，只修改允许编辑的列；equipmentId/planId由创建确定，不能借更新换设备。 */
  public int update(MaintenancePlanDevice row) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<MaintenancePlanDevice>()
            .eq(MaintenancePlanDevice::getTenantId, row.getTenantId())
            .eq(MaintenancePlanDevice::getPlanId, row.getPlanId())
            .eq(MaintenancePlanDevice::getId, row.getId())
            .set(MaintenancePlanDevice::getTypeId, row.getTypeId())
            .set(MaintenancePlanDevice::getLegacyStandardId, row.getLegacyStandardId())
            .set(MaintenancePlanDevice::getNextDate, row.getNextDate())
            .set(MaintenancePlanDevice::getAnchorDate, row.getAnchorDate())
            .set(MaintenancePlanDevice::getCycleIndex, row.getCycleIndex())
            .set(MaintenancePlanDevice::getEnabled, row.getEnabled())
            .set(MaintenancePlanDevice::getPeriodCount, row.getPeriodCount())
            .set(MaintenancePlanDevice::getPeriodUnit, row.getPeriodUnit())
            .set(MaintenancePlanDevice::getValidDays, row.getValidDays())
            .set(MaintenancePlanDevice::getValidDuration, row.getValidDuration())
            .set(MaintenancePlanDevice::getValidUnit, row.getValidUnit())
            .set(MaintenancePlanDevice::getRemark, row.getRemark()));
  }

  public int disable(long tenant, long plan, Long id) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<MaintenancePlanDevice>()
            .eq(MaintenancePlanDevice::getTenantId, tenant)
            .eq(MaintenancePlanDevice::getPlanId, plan)
            .eq(id != null, MaintenancePlanDevice::getId, id)
            .set(MaintenancePlanDevice::getEnabled, false));
  }

  public int deleteForPlan(long tenant, long plan) {
    return mapper.delete(
        new LambdaQueryWrapper<MaintenancePlanDevice>()
            .eq(MaintenancePlanDevice::getTenantId, tenant)
            .eq(MaintenancePlanDevice::getPlanId, plan));
  }

  /** 原事务已按计划、安排、任务顺序加锁；这里只推进游标，不触发新的任务或通知。 */
  public int advance(long tenant, long id, LocalDate next, int index) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<MaintenancePlanDevice>()
            .eq(MaintenancePlanDevice::getTenantId, tenant)
            .eq(MaintenancePlanDevice::getId, id)
            .set(MaintenancePlanDevice::getNextDate, next)
            .set(MaintenancePlanDevice::getCycleIndex, index));
  }

  public int reset(long tenant, long id, LocalDate next) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<MaintenancePlanDevice>()
            .eq(MaintenancePlanDevice::getTenantId, tenant)
            .eq(MaintenancePlanDevice::getId, id)
            .set(MaintenancePlanDevice::getNextDate, next)
            .set(MaintenancePlanDevice::getAnchorDate, next)
            .set(MaintenancePlanDevice::getCycleIndex, 0));
  }
}
