package com.windasoft.eam.entity.inventory;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 库存站内消息本表模型；工作流只操作其精确关联的审批待办。 */
@Data
@TableName("inv_inbox")
public class InventoryInbox {
  @TableId private Long id;
  private Long tenantId;
  private Long userId;
  private Long documentId;
  private String routeKind;
  private String kind;
  private String title;
  private Integer isTodo;
  private Integer done;
  private LocalDateTime readAt;
  private LocalDateTime createdAt;
  private String messageKey;
  private String messageArgs;
}
