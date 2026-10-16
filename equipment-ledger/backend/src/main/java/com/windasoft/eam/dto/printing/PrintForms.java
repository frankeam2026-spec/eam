package com.windasoft.eam.dto.printing;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;

public final class PrintForms {
  private PrintForms() {}

  public record Template(
      @NotBlank @Size(max = 100) String name,
      Long companyId,
      boolean enabled,
      boolean isDefault,
      Integer rowVersion,
      @NotNull JsonNode config) {}

  public record Output(
      @NotBlank @Size(max = 64) String documentId,
      @NotBlank @Size(max = 64) String templateId,
      @Size(max = 64) String fingerprint,
      @Size(max = 64) String requestKey) {}
}
