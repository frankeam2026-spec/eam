package com.windasoft.eam.entity.repair;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** eam_repair_inbox 的持久化对象；业务状态与事务由服务层维护。 */
@Data
@TableName("eam_repair_inbox")
public class RepairInbox {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long userId;
  private Long orderId;
  private String kind;
  private String title;
  private Integer isTodo;
  private Integer done;
  private LocalDateTime readAt;
  private LocalDateTime createdAt;
  private String messageKey;
  private String messageArgs;
}
