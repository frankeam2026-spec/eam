package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** eam_maintenance_execution的真实复合键记录；不得按taskId单列执行通用ById操作。 */
@Data
@TableName("eam_maintenance_execution")
public class MaintenanceExecution {
  private Long tenantId;
  private Long taskId;
  private String description;
  private LocalDateTime startedAt;
  private LocalDateTime workStartAt;
  private LocalDateTime workEndAt;
  private String reviewDecision;
  private String reviewDescription;
  private BigDecimal acceptanceScore;
  private Long reviewerId;
  private String reviewerName;
  private LocalDateTime reviewedAt;
}
