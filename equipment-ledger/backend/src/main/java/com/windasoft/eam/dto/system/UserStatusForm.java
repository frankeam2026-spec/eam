package com.windasoft.eam.dto.system;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UserStatusForm(@NotNull @Min(0) @Max(1) Integer status) {}
