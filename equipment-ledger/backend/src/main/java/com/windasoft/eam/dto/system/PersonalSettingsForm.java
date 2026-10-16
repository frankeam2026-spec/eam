package com.windasoft.eam.dto.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PersonalSettingsForm(
    @NotBlank @Pattern(regexp = "blue|navy|pink", message = "请选择有效主题") String theme) {}
