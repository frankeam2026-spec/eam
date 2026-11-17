package com.windasoft.eam.dto.basedata;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactIntegerDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BaseItemStatusForm(
    @NotNull @Min(0) @Max(1) @JsonDeserialize(using = ExactIntegerDeserializer.class)
        Integer status) {}
