package com.windasoft.eam.entity.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** eam_ai_call真实持久化模型；预算和费用保持十进制定点类型。 */
@Data
@TableName("eam_ai_call")
public class AiCall {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long userId;
  private String billingMonth;
  private String purpose;
  private String modelName;
  private String status;
  private BigDecimal reservedAmount;
  private BigDecimal estimatedCost;
  private Long inputTokens;
  private Long outputTokens;
  private Long elapsedMs;
  private LocalDateTime createdAt;
  private LocalDateTime completedAt;
}
