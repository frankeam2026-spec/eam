package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceDevicePerson;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 仅持久化eam_maintenance_device_person；替换范围及跨表事务由保养Service确定。 */
@Repository
public class MaintenanceDevicePersonRepository {
  private final MaintenanceDevicePersonMapper mapper;

  public MaintenanceDevicePersonRepository(MaintenanceDevicePersonMapper mapper) {
    this.mapper = mapper;
  }

  public List<MaintenanceDevicePerson> list(long tenant, Collection<Long> devices) {
    if (devices.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<MaintenanceDevicePerson>()
            .eq(MaintenanceDevicePerson::getTenantId, tenant)
            .in(MaintenanceDevicePerson::getDeviceId, devices)
            .orderByAsc(MaintenanceDevicePerson::getDeviceId, MaintenanceDevicePerson::getSortNo));
  }

  public int clear(long tenant, long device) {
    return mapper.delete(
        new LambdaQueryWrapper<MaintenanceDevicePerson>()
            .eq(MaintenanceDevicePerson::getTenantId, tenant)
            .eq(MaintenanceDevicePerson::getDeviceId, device));
  }

  /** 调用方的完整保存事务必须覆盖清理和批量插入；空明细不生成无条件SQL。 */
  public void insertAll(List<MaintenanceDevicePerson> rows) {
    if (rows.size() == 1) {
      mapper.insert(rows.getFirst());
    } else if (!rows.isEmpty()) {
      mapper.insert(rows);
    }
  }
}
