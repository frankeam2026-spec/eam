package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 按租户、父对象、角色和用户定位；不伪造单列主键。 */
@Data
@TableName("eam_maintenance_plan_person")
public class MaintenancePlanPerson {
  private Long tenantId;
  private Long planId;
  private String roleCode;
  private Long userId;
  private Integer sortNo;
}
