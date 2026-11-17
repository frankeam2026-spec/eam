package com.windasoft.eam.dto.system;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UserBatchForm(
    @NotNull @JsonDeserialize(contentUsing = ExactLongDeserializer.class) List<Long> ids,
    @NotBlank String operation,
    @JsonDeserialize(using = ExactLongDeserializer.class) Long orgId,
    String password) {}
