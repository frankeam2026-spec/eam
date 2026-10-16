package com.windasoft.eam.dto.label;

import jakarta.validation.constraints.*;
import java.util.List;

public record LabelPdfForm(
    @NotEmpty @Size(max = 200) List<Long> equipmentIds, @Min(1) @Max(20) int copies) {}
