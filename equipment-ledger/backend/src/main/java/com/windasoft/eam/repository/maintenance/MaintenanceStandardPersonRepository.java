package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceStandardPerson;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 仅持久化eam_maintenance_standard_person；替换范围及跨表事务由保养Service确定。 */
@Repository
public class MaintenanceStandardPersonRepository {
  private final MaintenanceStandardPersonMapper mapper;

  public MaintenanceStandardPersonRepository(MaintenanceStandardPersonMapper mapper) {
    this.mapper = mapper;
  }

  public List<MaintenanceStandardPerson> list(long tenant, Collection<Long> standards) {
    if (standards.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<MaintenanceStandardPerson>()
            .eq(MaintenanceStandardPerson::getTenantId, tenant)
            .in(MaintenanceStandardPerson::getStandardId, standards)
            .orderByAsc(
                MaintenanceStandardPerson::getStandardId, MaintenanceStandardPerson::getSortNo));
  }

  public int clear(long tenant, long standard) {
    return mapper.delete(
        new LambdaQueryWrapper<MaintenanceStandardPerson>()
            .eq(MaintenanceStandardPerson::getTenantId, tenant)
            .eq(MaintenanceStandardPerson::getStandardId, standard));
  }

  /** 调用方的完整保存事务必须覆盖清理和批量插入；空明细不生成无条件SQL。 */
  public void insertAll(List<MaintenanceStandardPerson> rows) {
    if (!rows.isEmpty()) {
      mapper.insert(rows);
    }
  }
}
