package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 保养任务审计事件；系统生成事件的 actorId 允许为空，details 为按动作变化的审计详情。 */
@Data
@TableName("eam_maintenance_event")
public class MaintenanceEvent {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long taskId;
  private Long actorId;
  private String actorName;
  private String action;
  private String details;
  private LocalDateTime createdAt;
}
