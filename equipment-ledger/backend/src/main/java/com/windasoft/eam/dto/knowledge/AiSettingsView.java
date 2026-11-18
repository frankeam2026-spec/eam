package com.windasoft.eam.dto.knowledge;

import java.math.BigDecimal;

/** 仅返回密钥是否存在，不返回密文或明文；价格仍沿用原JSON数值协议。 */
public record AiSettingsView(
    boolean enabled,
    String baseUrl,
    String chatModel,
    String embeddingModel,
    boolean hasApiKey,
    long rowVersion,
    BigDecimal monthlyLimit,
    int warningPercent,
    BigDecimal inputPrice,
    BigDecimal outputPrice,
    BigDecimal embeddingPrice,
    BigDecimal estimatedSpent,
    BigDecimal reservedAmount,
    String billingMonth,
    boolean budgetWarning,
    String visionModel,
    String audioModel,
    BigDecimal visionInputPrice,
    BigDecimal visionOutputPrice,
    BigDecimal audioMinutePrice) {}
