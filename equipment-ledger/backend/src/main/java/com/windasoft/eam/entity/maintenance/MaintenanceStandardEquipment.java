package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** eam_maintenance_standard_equipment的真实复合键明细，不使用虚构的单列ID。 */
@Data
@TableName("eam_maintenance_standard_equipment")
public class MaintenanceStandardEquipment {
  private Long tenantId;
  private Long standardId;
  private Long equipmentId;
  private Integer sortNo;
}
