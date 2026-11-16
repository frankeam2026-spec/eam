package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** eam_maintenance_standard_category_label的真实复合键明细，不使用虚构的单列ID。 */
@Data
@TableName("eam_maintenance_standard_category_label")
public class MaintenanceStandardCategoryLabel {
  private Long tenantId;
  private Long standardId;
  private String labelKind;
  private Integer sortNo;
  private String nameSnapshot;
}
