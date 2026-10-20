package com.windasoft.eam.dto.repair;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;

public record RepairForm(
    @NotBlank(message = "{server.repair.message0001}") String equipmentCode,
    @NotBlank(message = "{server.equipment.message0003}") String equipmentName,
    String department,
    @NotNull(message = "{ui.equipment.text1061}") Long faultTypeId,
    @NotBlank(message = "{server.repair.message0002}") String severity,
    @NotBlank(message = "{server.repair.message0003}") String description,
    @NotBlank(message = "{server.repair.message0004}") String reporter,
    String assignee,
    String repairAction,
    Integer downtimeMinutes,
    JsonNode attachments,
    JsonNode customFields,
    Integer customSchemaVersion) {}
