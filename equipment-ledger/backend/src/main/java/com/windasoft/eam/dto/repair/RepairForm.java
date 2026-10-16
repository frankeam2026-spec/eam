package com.windasoft.eam.dto.repair;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;

public record RepairForm(
    @NotBlank(message = "设备编号必填") String equipmentCode,
    @NotBlank(message = "设备名称必填") String equipmentName,
    String department,
    @NotBlank(message = "故障类型必填") String faultType,
    @NotBlank(message = "故障等级必填") String severity,
    @NotBlank(message = "故障描述必填") String description,
    @NotBlank(message = "报修人必填") String reporter,
    String assignee,
    String repairAction,
    Integer downtimeMinutes,
    JsonNode attachments) {}
