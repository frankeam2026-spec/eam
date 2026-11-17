package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** eam_maintenance_execution_item的真实复合键记录；不得按taskId单列执行通用ById操作。 */
@Data
@TableName("eam_maintenance_execution_item")
public class MaintenanceExecutionItem {
  private Long tenantId;
  private Long taskId;
  private String itemKey;
  private Integer sortNo;
  private String resultValue;
  private Boolean abnormal;
  private Boolean resolved;
  private String measure;
  private String remark;
  private Boolean implicitNormal;
  private Boolean skipped;
}
