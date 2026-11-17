package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.windasoft.eam.common.ExactLongDeserializer;
import com.windasoft.eam.common.OptionalLongDeserializer;
import com.windasoft.eam.common.RowVersionDeserializer;
import com.windasoft.eam.domain.maintenance.MaintenanceTaskAction;
import java.time.LocalDateTime;
import java.util.List;

/** 七类办理动作只接受各自可编辑字段；动作由HTTP路由决定，不信任请求中的状态和审核人。 */
public sealed interface MaintenanceTaskCommand {
  Integer rowVersion();

  default String description() {
    return "";
  }

  @JsonIgnore
  default MaintenanceTaskAction action() {
    return switch (this) {
      case Start ignored -> MaintenanceTaskAction.START;
      case Process ignored -> MaintenanceTaskAction.PROCESS;
      case Accept ignored -> MaintenanceTaskAction.ACCEPT;
      case Suspend ignored -> MaintenanceTaskAction.SUSPEND;
      case Resume ignored -> MaintenanceTaskAction.RESUME;
      case Transfer ignored -> MaintenanceTaskAction.TRANSFER;
      case Void ignored -> MaintenanceTaskAction.VOID;
    };
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Start(
      @JsonDeserialize(using = RowVersionDeserializer.class) Integer rowVersion,
      String equipmentCode)
      implements MaintenanceTaskCommand {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Process(
      @JsonDeserialize(using = RowVersionDeserializer.class) Integer rowVersion,
      LocalDateTime startAt,
      LocalDateTime endAt,
      String description,
      @JsonDeserialize(contentUsing = ExactLongDeserializer.class)
          @JsonSerialize(contentUsing = ToStringSerializer.class)
          List<Long> collaborators,
      List<MaintenanceFileReference> attachments,
      List<MaintenanceExecutionResult.Item> items,
      ObjectNode customFields,
      Integer customSchemaVersion,
      String surplusRemark,
      @JsonDeserialize(using = OptionalLongDeserializer.class)
          @JsonSerialize(using = ToStringSerializer.class)
          Long holderId)
      implements MaintenanceTaskCommand {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Accept(
      @JsonDeserialize(using = RowVersionDeserializer.class) Integer rowVersion,
      String decision,
      @JsonDeserialize(using = ExactLongDeserializer.class) Long score,
      String description,
      List<MaintenanceFileReference> attachments)
      implements MaintenanceTaskCommand {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Suspend(
      @JsonDeserialize(using = RowVersionDeserializer.class) Integer rowVersion, String description)
      implements MaintenanceTaskCommand {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Resume(
      @JsonDeserialize(using = RowVersionDeserializer.class) Integer rowVersion, String description)
      implements MaintenanceTaskCommand {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Transfer(
      @JsonDeserialize(using = RowVersionDeserializer.class) Integer rowVersion,
      @JsonDeserialize(contentUsing = ExactLongDeserializer.class)
          @JsonSerialize(contentUsing = ToStringSerializer.class)
          List<Long> owners)
      implements MaintenanceTaskCommand {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Void(
      @JsonDeserialize(using = RowVersionDeserializer.class) Integer rowVersion, String description)
      implements MaintenanceTaskCommand {}
}
