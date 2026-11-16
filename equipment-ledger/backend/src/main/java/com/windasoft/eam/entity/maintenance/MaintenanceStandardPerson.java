package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** eam_maintenance_standard_person的真实复合键明细，不使用虚构的单列ID。 */
@Data
@TableName("eam_maintenance_standard_person")
public class MaintenanceStandardPerson {
  private Long tenantId;
  private Long standardId;
  private String roleCode;
  private Long userId;
  private Integer sortNo;
}
