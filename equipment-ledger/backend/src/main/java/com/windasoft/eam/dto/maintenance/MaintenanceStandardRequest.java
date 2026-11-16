package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import com.windasoft.eam.common.RowVersionDeserializer;
import com.windasoft.eam.domain.maintenance.MaintenanceDefinitionLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MaintenanceStandardRequest(
    @Size(
            max = MaintenanceDefinitionLimits.CODE_MAX_LENGTH,
            message = "{server.inventory.message0191}")
        String code,
    @NotBlank(message = "{server.maintenance.message0020}")
        @Size(
            max = MaintenanceDefinitionLimits.NAME_MAX_LENGTH,
            message = "{server.maintenance.message0020}")
        String name,
    @JsonDeserialize(using = ExactLongDeserializer.class) Long departmentId,
    Boolean enabled,
    String remark,
    @JsonDeserialize(using = RowVersionDeserializer.class) Integer rowVersion,
    @Valid List<@NotNull MaintenanceStandardItemRequest> items,
    @JsonDeserialize(contentUsing = ExactLongDeserializer.class) List<Long> equipmentIds,
    @JsonDeserialize(contentUsing = ExactLongDeserializer.class) List<Long> categoryIds,
    List<String> categoryNames,
    List<String> legacyCategoryNames,
    @JsonDeserialize(contentUsing = ExactLongDeserializer.class) List<Long> owners,
    @JsonDeserialize(contentUsing = ExactLongDeserializer.class) List<Long> reviewers) {
  public MaintenanceStandardRequest {
    code = Objects.toString(code, "").trim();
    name = Objects.toString(name, "").trim();
  }
}
