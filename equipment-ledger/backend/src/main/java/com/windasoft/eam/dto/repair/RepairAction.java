package com.windasoft.eam.dto.repair;

import jakarta.validation.constraints.*;

public record RepairAction(
    @NotBlank(message = "操作类型必填") String action,
    String assignee,
    String repairAction,
    Integer downtimeMinutes) {}
