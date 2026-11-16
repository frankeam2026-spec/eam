package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceStandardEquipment;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 仅持久化eam_maintenance_standard_equipment；替换范围及跨表事务由保养Service确定。 */
@Repository
public class MaintenanceStandardEquipmentRepository {
  private final MaintenanceStandardEquipmentMapper mapper;

  public MaintenanceStandardEquipmentRepository(MaintenanceStandardEquipmentMapper mapper) {
    this.mapper = mapper;
  }

  public List<MaintenanceStandardEquipment> list(long tenant, Collection<Long> standards) {
    if (standards.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<MaintenanceStandardEquipment>()
            .eq(MaintenanceStandardEquipment::getTenantId, tenant)
            .in(MaintenanceStandardEquipment::getStandardId, standards)
            .orderByAsc(
                MaintenanceStandardEquipment::getStandardId,
                MaintenanceStandardEquipment::getSortNo));
  }

  public int clear(long tenant, long standard) {
    return mapper.delete(
        new LambdaQueryWrapper<MaintenanceStandardEquipment>()
            .eq(MaintenanceStandardEquipment::getTenantId, tenant)
            .eq(MaintenanceStandardEquipment::getStandardId, standard));
  }

  /** 调用方的完整保存事务必须覆盖清理和批量插入；空明细不生成无条件SQL。 */
  public void insertAll(List<MaintenanceStandardEquipment> rows) {
    if (!rows.isEmpty()) {
      mapper.insert(rows);
    }
  }
}
