package com.windasoft.eam.dto.i18n;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocalePreferenceForm(
    @NotBlank(message = "{language.error.unavailable}")
        @Size(max = 35, message = "{language.error.unavailable}")
        String locale) {}
