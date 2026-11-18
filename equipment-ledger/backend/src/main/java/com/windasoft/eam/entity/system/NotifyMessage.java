package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 消息及待办事实，已读不代表业务已办理。 */
@Data
@TableName("eam_notify_message")
public class NotifyMessage {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private String sourceKey;
  private String module;
  private Long objectId;
  private String legacySource;
  private Long legacyId;
  private Long ruleId;
  private Integer policyVersion;
  private String scene;
  private String subject;
  private String content;
  private String messageKey;
  private String messageArgs;
  private Long recipientId;
  private String recipientKind;
  private LocalDateTime triggerAt;
  private LocalDateTime createdAt;
  private LocalDateTime readAt;
  private LocalDateTime cancelledAt;
}
