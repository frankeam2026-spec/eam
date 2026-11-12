package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 保养消息及待办记录；公共通知投递记录仍由通知模块持有。 */
@Data
@TableName("eam_maintenance_inbox")
public class MaintenanceInbox {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long taskId;
  private Long userId;
  private String kind;
  private String title;
  private String messageKey;
  private String messageArgs;

  @TableField("is_todo")
  private Boolean todo;

  private Boolean done;
  private LocalDateTime readAt;
  private LocalDateTime createdAt;
}
