package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("eam_sys_role_menu_permission")
public class RoleMenuPermission {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long tenantId;
  private Long roleId;
  private String menuKey;
  private String actionsJson;
  private String dataScopesJson;
  private String orgIdsJson;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
