package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.windasoft.eam.common.ExactLongDeserializer;
import com.windasoft.eam.common.OptionalLongDeserializer;
import com.windasoft.eam.common.RowVersionDeserializer;
import com.windasoft.eam.domain.maintenance.MaintenanceDefinitionLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;
import lombok.Data;

/** 完整编辑计划；展示字段可回传但不参与权威写入，提醒模式保留未传/明确默认的区别。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MaintenancePlanRequest {
  @Size(
      max = MaintenanceDefinitionLimits.CODE_MAX_LENGTH,
      message = "{server.inventory.message0191}")
  private String code = "";

  @NotBlank(message = "{server.maintenance.message0020}")
  @Size(
      max = MaintenanceDefinitionLimits.NAME_MAX_LENGTH,
      message = "{server.maintenance.message0020}")
  private String name = "";

  @JsonDeserialize(using = OptionalLongDeserializer.class)
  private Long departmentId;

  @JsonDeserialize(using = RowVersionDeserializer.class)
  private Integer rowVersion;

  private Boolean enabled;
  private String remark;
  private String calculation;

  @Min(value = 0, message = "{server.maintenance.message0033}")
  @Max(
      value = MaintenanceDefinitionLimits.MAX_PLAN_LEAD_DAYS,
      message = "{server.maintenance.message0033}")
  @JsonDeserialize(using = ExactLongDeserializer.class)
  private Long advanceDays = 0L;

  @Min(value = 0, message = "{server.maintenance.message0034}")
  @Max(
      value = MaintenanceDefinitionLimits.MAX_PLAN_LEAD_DAYS,
      message = "{server.maintenance.message0034}")
  @JsonDeserialize(using = ExactLongDeserializer.class)
  private Long remindDays = 0L;

  private String autoFill;
  private Boolean scanStart;
  private String reviewMode;

  @JsonDeserialize(contentUsing = ExactLongDeserializer.class)
  private List<Long> owners = List.of();

  @JsonDeserialize(contentUsing = ExactLongDeserializer.class)
  private List<Long> reviewers = List.of();

  @Valid private List<@NotNull MaintenancePlanDeviceRequest> details = List.of();

  /** 扩展字段由现有发布Schema、字段权限和类型化值存储校验，不承载任何固定计划字段。 */
  private ObjectNode customFields;

  private Integer customSchemaVersion;
  private String reminderMode;

  @JsonDeserialize(using = OptionalLongDeserializer.class)
  private Long reminderPolicyId;

  @JsonIgnore private boolean reminderModePresent;

  public void setCode(String code) {
    this.code = Objects.toString(code, "").trim();
  }

  public void setName(String name) {
    this.name = Objects.toString(name, "").trim();
  }

  @JsonSetter
  public void setReminderMode(String mode) {
    this.reminderMode = mode;
    this.reminderModePresent = true;
  }
}
