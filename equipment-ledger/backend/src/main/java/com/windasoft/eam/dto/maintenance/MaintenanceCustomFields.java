package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** 仅用户发布的扩展字段及其显示标签，值和字段权限仍由现有BusinessValueService负责。 */
public record MaintenanceCustomFields(
    ObjectNode customFields, ObjectNode customFieldLabels, int customSchemaVersion) {}
