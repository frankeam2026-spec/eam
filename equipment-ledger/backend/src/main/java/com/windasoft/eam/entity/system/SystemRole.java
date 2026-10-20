package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("eam_sys_role")
public class SystemRole {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long tenantId;
  private String code;
  private String name;
  private String description;
  private Integer status;
  private String dataScope;

  @TableField(exist = false)
  private String orgIdsJson;

  @TableField(exist = false)
  private String permissionsJson;

  @TableLogic private Integer deleted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
