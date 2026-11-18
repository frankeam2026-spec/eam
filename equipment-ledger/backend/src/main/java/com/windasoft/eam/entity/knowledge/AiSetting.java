package com.windasoft.eam.entity.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/** eam_ai_setting真实持久化模型；预算和费用保持十进制定点类型。 */
@Data
@TableName("eam_ai_setting")
public class AiSetting {
  @TableId(type = IdType.INPUT)
  private Long tenantId;

  private Boolean enabled;
  private String baseUrl;
  private String apiSecret;
  private String chatModel;
  private String embeddingModel;
  private BigDecimal monthlyLimit;
  private Integer warningPercent;
  private BigDecimal inputPrice;
  private BigDecimal outputPrice;
  private BigDecimal embeddingPrice;
  private Long rowVersion;
  private String visionModel;
  private String audioModel;
  private BigDecimal visionInputPrice;
  private BigDecimal visionOutputPrice;
  private BigDecimal audioMinutePrice;
}
