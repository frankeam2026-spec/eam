package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 组织审计表：保留既有前后值JSON及数据库时间，不作为组织状态的权威。 */
@Data
@TableName("eam_org_event")
public class OrganizationEvent {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long orgId;
  private Long actorId;
  private String action;
  private String beforeJson;
  private String afterJson;
  private LocalDateTime createdAt;
}
