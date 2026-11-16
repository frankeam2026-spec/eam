package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import com.windasoft.eam.common.OptionalLongDeserializer;
import com.windasoft.eam.domain.maintenance.MaintenanceDefinitionLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** 安排是完整替换；周期、有效时长和项目选择保留未传标志，避免覆盖旧安排语义。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MaintenancePlanDeviceRequest {
  @JsonDeserialize(using = OptionalLongDeserializer.class)
  private Long id;

  @NotNull
  @Positive
  @JsonDeserialize(using = ExactLongDeserializer.class)
  private Long equipmentId;

  @NotNull
  @Positive
  @JsonDeserialize(using = ExactLongDeserializer.class)
  private Long typeId;

  @JsonDeserialize(using = OptionalLongDeserializer.class)
  private Long standardId;

  @NotNull private LocalDate nextDate;

  @Min(value = 1, message = "{server.maintenance.message0036}")
  @Max(
      value = MaintenanceDefinitionLimits.PERIOD_MAX_COUNT,
      message = "{server.maintenance.message0036}")
  @JsonDeserialize(using = ExactLongDeserializer.class)
  private Long period;

  private String unit;

  @Min(value = 0, message = "请填写有效时长整数并选择天、周或月")
  @Max(value = MaintenanceDefinitionLimits.MAX_VALIDITY_DAYS, message = "请填写有效时长整数并选择天、周或月")
  @JsonDeserialize(using = ExactLongDeserializer.class)
  private Long validDuration;

  @JsonDeserialize(using = ExactLongDeserializer.class)
  private Long validDays;

  private String validUnit;
  private String remark;

  @JsonDeserialize(contentUsing = ExactLongDeserializer.class)
  private List<Long> owners = List.of();

  @JsonDeserialize(contentUsing = ExactLongDeserializer.class)
  private List<Long> reviewers = List.of();

  @Valid private List<@NotNull MaintenanceProjectReference> projectRefs = List.of();
  @JsonIgnore private boolean periodPresent;
  @JsonIgnore private boolean unitPresent;
  @JsonIgnore private boolean validDurationPresent;
  @JsonIgnore private boolean validUnitPresent;
  @JsonIgnore private boolean projectRefsPresent;

  @JsonSetter
  public void setPeriod(Long value) {
    period = value;
    periodPresent = true;
  }

  @JsonSetter
  public void setUnit(String value) {
    unit = value;
    unitPresent = true;
  }

  @JsonSetter
  public void setValidDuration(Long value) {
    validDuration = value;
    validDurationPresent = true;
  }

  @JsonSetter
  public void setValidUnit(String value) {
    validUnit = value;
    validUnitPresent = true;
  }

  @JsonSetter
  public void setProjectRefs(List<MaintenanceProjectReference> value) {
    projectRefs = value;
    projectRefsPresent = true;
  }
}
