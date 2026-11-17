package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** eam_maintenance_execution_image的真实复合键记录；不得按taskId单列执行通用ById操作。 */
@Data
@TableName("eam_maintenance_execution_image")
public class MaintenanceExecutionImage {
  private Long tenantId;
  private Long taskId;
  private String itemKey;
  private Integer sortNo;
  private String fileId;
  private String nameSnapshot;
  private String contentType;
  private Long sizeBytes;
  private String urlSnapshot;
}
