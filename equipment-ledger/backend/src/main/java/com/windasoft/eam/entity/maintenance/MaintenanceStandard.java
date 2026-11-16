package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 保养标准主体，不混入项目、人员或设备的组合字段。 */
@Data
@TableName("eam_maintenance_standard")
public class MaintenanceStandard {
  @TableId private Long id;
  private Long tenantId;
  private String code;
  private String name;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Long departmentId;

  private Long creatorId;
  private Integer rowVersion;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private Boolean enabled;
  private String remark;
}
