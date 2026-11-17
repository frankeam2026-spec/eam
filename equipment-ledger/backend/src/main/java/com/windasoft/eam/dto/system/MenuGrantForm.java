package com.windasoft.eam.dto.system;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import java.util.List;

public record MenuGrantForm(
    List<String> actions,
    List<String> dataScopes,
    @JsonDeserialize(contentUsing = ExactLongDeserializer.class) List<Long> orgIds) {}
