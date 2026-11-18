package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** eam_role_legacy_action使用租户与关联对象的真实复合键，不伪造单列主键。 */
@Data
@TableName("eam_role_legacy_action")
public class RoleLegacyAction {
  private Long tenantId;
  private Long roleId;
  private String menuKey;
  private String actionCode;
  private Integer sortNo;
}
