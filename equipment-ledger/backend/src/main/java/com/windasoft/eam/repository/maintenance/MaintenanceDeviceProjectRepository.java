package com.windasoft.eam.repository.maintenance;

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
}
