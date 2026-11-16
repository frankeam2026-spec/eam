package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 计划主体是周期与验收策略的权威；人员、设备安排分别持久化。 */
@Data
@TableName("eam_maintenance_plan")
public class MaintenancePlan {
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
  private String calculationMode;
  private Integer advanceDays;
  private Integer remindDays;
  private String autoFill;
  private Boolean scanStart;
  private String reviewMode;
  private String reviewRule;
}
