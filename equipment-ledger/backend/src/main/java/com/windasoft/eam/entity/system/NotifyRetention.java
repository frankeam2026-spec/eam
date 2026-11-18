package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 统一通知配置数据，状态和关联事务由Service校验编排。 */
@Data
@TableName("eam_notify_retention")
public class NotifyRetention {
  @TableId(type = IdType.INPUT)
  private Long tenantId;

  private Integer successDays;
  private Integer exceptionDays;
  private Integer rowVersion;
  private LocalDateTime updatedAt;
}
