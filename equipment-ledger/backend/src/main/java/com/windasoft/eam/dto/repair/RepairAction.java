package com.windasoft.eam.dto.repair;

import jakarta.validation.constraints.*;

public record RepairAction(
    @NotBlank(message = "{server.repair.message0005}") String action,
    String assignee,
    String repairAction,
    Integer downtimeMinutes) {}
