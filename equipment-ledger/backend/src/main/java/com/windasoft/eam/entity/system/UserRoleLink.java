package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("eam_sys_user_role")
public class UserRoleLink {
  @TableId(value = "user_id", type = IdType.INPUT)
  private Long userId;

  private Long tenantId;
  private Long roleId;
}
