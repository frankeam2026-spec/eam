package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 保养类型主体；周期变更及系统类型保护由Service校验。 */
@Data
@TableName("eam_maintenance_type")
public class MaintenanceType {
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
  private Integer periodCount;
  private String periodUnit;
  private String symbol;

  @TableField("is_system")
  private Boolean systemFlag;

  private Boolean enabled;
  private String remark;
}
