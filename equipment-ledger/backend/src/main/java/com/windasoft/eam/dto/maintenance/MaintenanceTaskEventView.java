package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** 固定事件展示字段；details 仅承载各审计动作不同的历史详情，不作为任务核心数据模型。 */
public record MaintenanceTaskEventView(
    String action, String actor, String time, ObjectNode details) {}
