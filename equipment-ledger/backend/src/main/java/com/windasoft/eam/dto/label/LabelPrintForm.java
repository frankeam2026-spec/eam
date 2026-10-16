package com.windasoft.eam.dto.label;

import jakarta.validation.constraints.*;
import java.util.List;

public record LabelPrintForm(@NotEmpty @Size(max = 200) List<Long> equipmentIds) {}
