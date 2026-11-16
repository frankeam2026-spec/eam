package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Data;

/** 设备安排及生成游标；legacyStandardId仅承载原整标准选择语义，不能覆盖显式项目选择。 */
@Data
@TableName("eam_maintenance_plan_device")
public class MaintenancePlanDevice {
  @TableId private Long id;
  private Long tenantId;
  private Long planId;
  private Long equipmentId;
  private Long typeId;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Long legacyStandardId;

  private LocalDate nextDate;
  private LocalDate anchorDate;
  private Integer cycleIndex;
  private Boolean enabled;
  private Integer periodCount;
  private String periodUnit;
  private Integer validDays;
  private Integer validDuration;
  private String validUnit;
  private String remark;
}
