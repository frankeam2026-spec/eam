package com.windasoft.eam.dto.repair;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 报修创建与编辑输入；自定义字段和附件为受专用校验流程约束的扩展结构。 */
public record RepairForm(
    @NotBlank(message = "{server.repair.message0001}") @Size(max = 32) String equipmentCode,
    @NotBlank(message = "{server.equipment.message0003}") @Size(max = 100) String equipmentName,
    String department,
    @NotNull(message = "{ui.equipment.text1061}") Long faultTypeId,
    @NotBlank(message = "{server.repair.message0002}") @Size(max = 16) String severity,
    @NotBlank(message = "{server.repair.message0003}") @Size(max = 2000) String description,
    @NotBlank(message = "{server.repair.message0004}") String reporter,
    String assignee,
    String repairAction,
    Integer downtimeMinutes,
    JsonNode attachments,
    JsonNode customFields,
    Integer customSchemaVersion) {}
