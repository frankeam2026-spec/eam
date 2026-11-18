package com.windasoft.eam.entity.knowledge;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/** eam_ai_monthly_usage真实持久化模型；预算和费用保持十进制定点类型。 */
@Data
@TableName("eam_ai_monthly_usage")
public class AiMonthlyUsage {
  private Long tenantId;
  private String billingMonth;
  private BigDecimal estimatedSpent;
  private BigDecimal reservedAmount;
}
