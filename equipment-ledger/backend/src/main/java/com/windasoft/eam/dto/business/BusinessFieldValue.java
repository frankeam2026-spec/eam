package com.windasoft.eam.dto.business;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 经字段规则校验后的一个值；真实存储键由业务调用方及仓储确定，不接受用户提供。 */
public record BusinessFieldValue(
    String textValue,
    BigDecimal numberValue,
    LocalDate dateValue,
    LocalDateTime datetimeValue,
    Integer boolValue,
    String referenceId,
    String referenceName) {}
