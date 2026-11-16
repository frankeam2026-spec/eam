package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceItemFile;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 仅持久化eam_maintenance_item_file；替换范围及跨表事务由保养Service确定。 */
@Repository
public class MaintenanceItemFileRepository {
  private final MaintenanceItemFileMapper mapper;

  public MaintenanceItemFileRepository(MaintenanceItemFileMapper mapper) {
    this.mapper = mapper;
  }

  public List<MaintenanceItemFile> list(long tenant, Collection<Long> standards) {
    if (standards.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<MaintenanceItemFile>()
            .eq(MaintenanceItemFile::getTenantId, tenant)
            .in(MaintenanceItemFile::getStandardId, standards)
            .orderByAsc(MaintenanceItemFile::getStandardId, MaintenanceItemFile::getSortNo));
  }

  public int clear(long tenant, long standard) {
    return mapper.delete(
        new LambdaQueryWrapper<MaintenanceItemFile>()
            .eq(MaintenanceItemFile::getTenantId, tenant)
            .eq(MaintenanceItemFile::getStandardId, standard));
  }

  /** 调用方的完整保存事务必须覆盖清理和批量插入；空明细不生成无条件SQL。 */
  public void insertAll(List<MaintenanceItemFile> rows) {
    if (!rows.isEmpty()) {
      mapper.insert(rows);
    }
  }
}
