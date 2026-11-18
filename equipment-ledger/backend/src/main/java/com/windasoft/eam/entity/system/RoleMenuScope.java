package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** eam_role_menu_scope使用租户与关联对象的真实复合键，不伪造单列主键。 */
@Data
@TableName("eam_role_menu_scope")
public class RoleMenuScope {
  private Long tenantId;
  private Long grantId;
  private String scopeCode;
  private Integer sortNo;
}
