package com.windasoft.eam.dto.experience;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import com.windasoft.eam.common.OptionalLongDeserializer;
import java.util.List;

public final class ExperienceCommands {
  private ExperienceCommands() {}

  public record AttachmentRef(String id) {}

  public record CreateAnomaly(
      @JsonDeserialize(using = ExactLongDeserializer.class) Long equipmentId,
      String sourceKind,
      @JsonDeserialize(using = OptionalLongDeserializer.class) Long sourceTaskId,
      String sourceItemId,
      String description,
      String severity,
      List<AttachmentRef> attachments) {}

  public record ChangeAnomaly(
      String action,
      @JsonDeserialize(using = ExactLongDeserializer.class) Long rowVersion,
      String reason,
      @JsonDeserialize(using = OptionalLongDeserializer.class) Long repairId,
      @JsonDeserialize(using = OptionalLongDeserializer.class) Long faultTypeId,
      String severity,
      List<AttachmentRef> attachments) {}

  public record SaveRepairPlan(
      @JsonDeserialize(using = ExactLongDeserializer.class) Long equipmentId,
      String description,
      @JsonDeserialize(using = ExactLongDeserializer.class) Long faultTypeId,
      String severity,
      String plannedAt,
      @JsonDeserialize(using = ExactLongDeserializer.class) Long ownerId,
      @JsonDeserialize(using = ExactLongDeserializer.class) Long rowVersion,
      @JsonDeserialize(contentUsing = ExactLongDeserializer.class) List<Long> anomalyIds) {}

  public record ChangeRepairPlan(
      String action,
      @JsonDeserialize(using = ExactLongDeserializer.class) Long rowVersion,
      String reason) {}
}
