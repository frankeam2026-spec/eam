package com.windasoft.eam.dto.system;

import jakarta.validation.constraints.*;

public record LoginForm(
    @NotNull Long tenantId, @NotBlank String account, @NotBlank String password) {}
