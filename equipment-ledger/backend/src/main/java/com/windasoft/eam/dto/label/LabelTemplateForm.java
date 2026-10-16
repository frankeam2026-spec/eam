package com.windasoft.eam.dto.label;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record LabelTemplateForm(
    @NotBlank @Size(max = 100) String name,
    @NotNull @DecimalMin("10") @DecimalMax("600") BigDecimal widthMm,
    @NotNull @DecimalMin("10") @DecimalMax("600") BigDecimal heightMm,
    @Size(max = 500) String remark,
    JsonNode elements,
    JsonNode layout,
    Integer rowVersion) {}
