package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("eam_auth_session")
public class AuthSession {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long tenantId;
  private Long userId;
  private String tokenHash;
  private LocalDateTime expiresAt;
  private LocalDateTime createdAt;
}
