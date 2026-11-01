package com.windasoft.eam.dto.repair;

import jakarta.validation.constraints.*;

/** 旧维修动作接口的兼容请求，动作名由 Service 检查；不作为新版工作流通用请求契约。 */
public record RepairAction(
    @NotBlank(message = "{server.repair.message0005}") String action,
    String assignee,
    String repairAction,
    Integer downtimeMinutes) {}
