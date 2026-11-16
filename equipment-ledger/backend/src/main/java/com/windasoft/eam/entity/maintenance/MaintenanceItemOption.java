package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** eam_maintenance_item_option的真实复合键明细，不使用虚构的单列ID。 */
@Data
@TableName("eam_maintenance_item_option")
public class MaintenanceItemOption {
  private Long tenantId;
  private Long standardId;
  private String itemKey;
  private Integer sortNo;
  private String optionValue;
  private Boolean normalValue;
}
