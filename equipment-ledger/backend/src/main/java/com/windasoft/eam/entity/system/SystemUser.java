package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("eam_sys_user")
public class SystemUser {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long tenantId;
  private String account;
  private String displayName;
  private String employeeNo;
  private String phone;
  @TableField(updateStrategy = FieldStrategy.NEVER)
  private String email;
  private String passwordHash;
  private Long orgId;
  private Integer status;
  @TableLogic private Integer deleted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
