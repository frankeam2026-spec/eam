package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceFile;
import org.springframework.stereotype.Repository;

/** 只维护保养附件元数据；文件读写及登记失败后的补偿由 Service 协调。 */
@Repository
public class MaintenanceFileRepository {
  private final MaintenanceFileMapper mapper;

  public MaintenanceFileRepository(MaintenanceFileMapper mapper) {
    this.mapper = mapper;
  }

  /** UUID 不替代租户隔离；必须先命中当前租户的登记记录，才能继续核实下载权限。 */
  public MaintenanceFile find(long tenant, String id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<MaintenanceFile>()
            .eq(MaintenanceFile::getTenantId, tenant)
            .eq(MaintenanceFile::getId, id));
  }

  public void insert(MaintenanceFile file) {
    mapper.insert(file);
  }
}
