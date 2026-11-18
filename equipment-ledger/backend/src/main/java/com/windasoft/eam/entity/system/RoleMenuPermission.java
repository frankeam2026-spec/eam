package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@TableName("eam_sys_role_menu_permission")
public class RoleMenuPermission {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long tenantId;
  private Long roleId;
  private String menuKey;

  @TableField(exist = false)
  private List<String> actions = List.of();

  @TableField(exist = false)
  private List<String> dataScopes = List.of();

  @TableField(exist = false)
  private List<Long> targetIds = List.of();

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
