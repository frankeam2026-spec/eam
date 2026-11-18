package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** eam_role_organization使用租户与关联对象的真实复合键，不伪造单列主键。 */
@Data
@TableName("eam_role_organization")
public class RoleOrganization {
  private Long tenantId;
  private Long roleId;
  private Long orgId;
  private Integer sortNo;
}
