package com.windasoft.eam.dto.system;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactIntegerDeserializer;
import com.windasoft.eam.common.ExactLongDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrgForm(
    @NotBlank String name,
    @NotBlank String code,
    @NotBlank String type,
    @JsonDeserialize(using = ExactLongDeserializer.class) Long parentId,
    String manager,
    @Min(0) @Max(1) @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer status) {}
