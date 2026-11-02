package com.windasoft.eam.entity.repair;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** eam_repair_event 的持久化对象；业务状态与事务由服务层维护。 */
@Data
@TableName("eam_repair_event")
public class RepairEvent {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long orderId;
  private String action;
  private Long actorId;
  private String actorName;
  private String detailsJson;
  private LocalDateTime createdAt;
}
