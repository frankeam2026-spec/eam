package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.windasoft.eam.common.ExactLongDeserializer;
import java.math.BigDecimal;

public record MaintenanceSpareRequirement(
    @JsonDeserialize(using = ExactLongDeserializer.class)
        @JsonSerialize(using = ToStringSerializer.class)
        Long stockId,
    BigDecimal quantity) {}
