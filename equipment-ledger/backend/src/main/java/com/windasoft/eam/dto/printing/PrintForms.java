package com.windasoft.eam.dto.printing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactIntegerDeserializer;
import com.windasoft.eam.common.ExactLongDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PrintForms {
  private PrintForms() {}

  public record Template(
      @NotBlank @Size(max = 100) String name,
      @JsonDeserialize(using = ExactLongDeserializer.class) Long companyId,
      boolean enabled,
      boolean isDefault,
      @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer rowVersion,
      @NotNull JsonNode config) {}

  public record Output(
      @NotBlank @Size(max = 64) String documentId,
      @NotBlank @Size(max = 64) String templateId,
      @Size(max = 64) String fingerprint,
      @Size(max = 64) String requestKey) {}
}
