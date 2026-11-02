package com.windasoft.eam.dto.repair;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

/** 维修动作输入。各动作必需字段由状态机校验；客户扩展值和旧附件元数据在专用验证边界处理。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RepairWorkflowCommand(
    @NotBlank String action,
    String reason,
    @JsonDeserialize(contentUsing = ExactLongDeserializer.class) List<Long> userIds,
    JsonNode customFields,
    Integer customSchemaVersion,
    String content,
    String repairMethod,
    @JsonDeserialize(using = ExactLongDeserializer.class) Long faultTypeId,
    @JsonDeserialize(using = ExactLongDeserializer.class) Long faultCauseId,
    @JsonDeserialize(using = ExactLongDeserializer.class) Long processingResultId,
    @JsonDeserialize(contentUsing = ExactLongDeserializer.class) List<Long> collaboratorIds,
    String prevention,
    JsonNode attachments,
    String vendor,
    Integer score,
    String comment,
    @JsonDeserialize(using = ExactLongDeserializer.class) Long materialId,
    @JsonDeserialize(using = ExactLongDeserializer.class) Long installedId,
    BigDecimal quantity) {}
