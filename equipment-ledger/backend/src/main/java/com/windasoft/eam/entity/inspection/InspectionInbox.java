package com.windasoft.eam.entity.inspection;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 消息及待办事实，已读不代表业务已办理。 */
@Data
@TableName("eam_inspection_inbox")
public class InspectionInbox {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long taskId;
  private Long userId;
  private String kind;
  private String title;
  private String messageKey;
  private String messageArgs;
  private Integer isTodo;
  private Integer done;
  private LocalDateTime readAt;
  private LocalDateTime createdAt;
}
