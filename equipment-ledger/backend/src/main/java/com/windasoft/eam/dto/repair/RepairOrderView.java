package com.windasoft.eam.dto.repair;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** 工单固定响应契约；仅客户自定义字段及其标签为动态内容，ID与日期格式保持兼容。 */
public record RepairOrderView(
    String id,
    String equipmentCategoryId,
    Map<String, Object> customFields,
    Map<String, String> customFieldLabels,
    Integer customSchemaVersion,
    String orderNo,
    String equipmentCode,
    String equipmentName,
    String department,
    String faultType,
    String faultTypeId,
    String faultCauseId,
    String processingResultId,
    String severity,
    String description,
    String reporter,
    String assignee,
    String status,
    String approvalGate,
    String reporterUserId,
    String repairAction,
    Integer downtimeMinutes,
    String reportedAt,
    JsonNode attachments,
    String faultCause,
    String repairMethod,
    String processingResult,
    String preventiveMeasures,
    JsonNode collaborators,
    JsonNode processAttachments,
    String outsourceVendor,
    Integer rowVersion) {}
