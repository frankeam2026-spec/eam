package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;

/** 保养历史备件选择项；沿用数量的 JSON 数值格式，不向客户端返回内部公司授权字段。 */
public record MaintenanceLegacyStockOption(
    @JsonSerialize(using = ToStringSerializer.class) Long id,
    String code,
    String name,
    String model,
    BigDecimal quantity) {}
