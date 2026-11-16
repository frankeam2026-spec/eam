package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceDeviceProject;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 安排项目关系的数据访问，不决定标准项目能否删除。 */
@Repository
public class MaintenanceDeviceProjectRepository {
  private final MaintenanceDeviceProjectMapper mapper;

  public MaintenanceDeviceProjectRepository(MaintenanceDeviceProjectMapper mapper) {
    this.mapper = mapper;
  }

  public long activeReferences(long tenant, long standard, String item) {
    return mapper.activeReferences(tenant, standard, item);
  }

  public int clear(long tenant, long device) {
    return mapper.delete(
        new LambdaQueryWrapper<MaintenanceDeviceProject>()
            .eq(MaintenanceDeviceProject::getTenantId, tenant)
            .eq(MaintenanceDeviceProject::getDeviceId, device));
  }

  public void insertAll(List<MaintenanceDeviceProject> rows) {
    if (rows.size() == 1) {
      mapper.insert(rows.getFirst());
    } else if (!rows.isEmpty()) {
      mapper.insert(rows);
    }
  }
}
