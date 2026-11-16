package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 安排的真实项目复合键，和任务历史快照相互独立。 */
@Data
@TableName("eam_maintenance_device_project")
public class MaintenanceDeviceProject {
  private Long tenantId;
  private Long deviceId;
  private Long standardId;
  private String itemKey;
  private Integer sortNo;
}
