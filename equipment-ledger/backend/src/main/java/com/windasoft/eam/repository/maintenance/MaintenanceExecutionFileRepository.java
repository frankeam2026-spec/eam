package com.windasoft.eam.repository.maintenance;

import com.windasoft.eam.entity.maintenance.MaintenanceExecutionFile;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 仅维护eam_maintenance_execution_file；关联替换顺序和事务由保养Service决定。 */
@Repository
public class MaintenanceExecutionFileRepository {
  private final MaintenanceExecutionFileMapper mapper;

  public MaintenanceExecutionFileRepository(MaintenanceExecutionFileMapper mapper) {
    this.mapper = mapper;
  }

  public void insertAll(List<MaintenanceExecutionFile> rows) {
    if (rows.size() == 1) {
      mapper.insert(rows.getFirst());
    } else if (!rows.isEmpty()) {
      mapper.insert(rows);
    }
  }
}
