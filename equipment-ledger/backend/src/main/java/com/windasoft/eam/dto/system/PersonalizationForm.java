package com.windasoft.eam.dto.system;

import jakarta.validation.constraints.*;

public record PersonalizationForm(
    @NotBlank(message = "{ui.equipment.text0927}")
        @Size(max = 30, message = "{server.system.message0001}")
        String systemName,
    String logoFileId,
    String backgroundFileId,
    @NotBlank(message = "{server.system.message0002}") String backgroundPosition,
    @NotNull(message = "{server.system.message0003}") @Min(0) Integer rowVersion) {}
