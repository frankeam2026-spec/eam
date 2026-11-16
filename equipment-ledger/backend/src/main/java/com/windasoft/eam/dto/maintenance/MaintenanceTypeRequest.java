package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import com.windasoft.eam.common.RowVersionDeserializer;
import com.windasoft.eam.domain.maintenance.MaintenanceDefinitionLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Objects;

/** 旧编辑器同时回传展示字段，按既有契约忽略；system和审计字段不能由客户端决定。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MaintenanceTypeRequest(
    @Size(
            max = MaintenanceDefinitionLimits.CODE_MAX_LENGTH,
            message = "{server.inventory.message0191}")
        String code,
    @NotBlank(message = "{server.maintenance.message0020}")
        @Size(
            max = MaintenanceDefinitionLimits.NAME_MAX_LENGTH,
            message = "{server.maintenance.message0020}")
        String name,
    String departmentId,
    @NotNull(message = "{server.maintenance.message0022}")
        @Min(value = 1, message = "{server.maintenance.message0022}")
        @Max(
            value = MaintenanceDefinitionLimits.PERIOD_MAX_COUNT,
            message = "{server.maintenance.message0022}")
        @JsonDeserialize(using = ExactLongDeserializer.class)
        Long period,
    String unit,
    String symbol,
    Boolean enabled,
    String remark,
    @JsonDeserialize(using = RowVersionDeserializer.class) Integer rowVersion) {
  public MaintenanceTypeRequest {
    code = Objects.toString(code, "").trim();
    name = Objects.toString(name, "").trim();
  }
}
