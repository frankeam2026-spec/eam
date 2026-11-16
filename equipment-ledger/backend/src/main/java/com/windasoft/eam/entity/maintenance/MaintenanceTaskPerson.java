package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** eam_maintenance_task_person的真实复合键记录；不得按taskId单列执行通用ById操作。 */
@Data
@TableName("eam_maintenance_task_person")
public class MaintenanceTaskPerson {
  private Long tenantId;
  private Long taskId;
  private String roleCode;
  private Long userId;
  private Integer sortNo;
}
