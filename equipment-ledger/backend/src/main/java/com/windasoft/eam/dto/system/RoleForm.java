package com.windasoft.eam.dto.system;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactIntegerDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record RoleForm(
    @NotBlank String code,
    @NotBlank String name,
    String description,
    @Min(0) @Max(1) @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer status,
    @NotNull Map<String, MenuGrantForm> menuPermissions) {}
