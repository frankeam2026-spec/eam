package com.windasoft.eam.dto.system;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactIntegerDeserializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PersonalizationForm(
    @NotBlank(message = "{ui.equipment.text0927}")
        @Size(max = 30, message = "{server.system.message0001}")
        String systemName,
    String logoFileId,
    String backgroundFileId,
    @NotBlank(message = "{server.system.message0002}") String backgroundPosition,
    @NotNull(message = "{server.system.message0003}")
        @Min(0)
        @JsonDeserialize(using = ExactIntegerDeserializer.class)
        Integer rowVersion) {}
