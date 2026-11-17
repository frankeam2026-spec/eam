package com.windasoft.eam.dto.basedata;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactIntegerDeserializer;
import com.windasoft.eam.common.ExactLongDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BaseItemForm(
    @Size(max = 40, message = "{server.common.message0001}") String code,
    @NotBlank(message = "{server.common.message0002}") String name,
    @JsonDeserialize(using = ExactLongDeserializer.class) Long parentId,
    @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer precision,
    @Min(0) @Max(1) @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer status,
    @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer sortNo,
    @JsonDeserialize(using = ExactLongDeserializer.class) Long companyId) {}
