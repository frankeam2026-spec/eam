package com.windasoft.eam.dto.system;

import java.util.List;

public record PersonalSettingsView(
    String locale, String effectiveLocale, String theme, List<String> roles) {}
