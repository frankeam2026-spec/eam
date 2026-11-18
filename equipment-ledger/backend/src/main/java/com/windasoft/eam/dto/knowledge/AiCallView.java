package com.windasoft.eam.dto.knowledge;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 调用记录不包含提示词、令牌或密钥；可空用量保持原null语义。 */
public record AiCallView(
    String id,
    String purpose,
    String modelName,
    String status,
    Long inputTokens,
    Long outputTokens,
    BigDecimal estimatedCost,
    Long elapsedMs,
    LocalDateTime createdAt) {}
