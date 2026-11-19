package com.windasoft.eam.dto.experience;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** 保留前端已使用的蛇形字段和 rowVersion 别名，审计明细按动作保留独立的数据结构。 */
public final class ExperienceDetails {
  private ExperienceDetails() {}

  public record Event(String action, String actor, String time, JsonNode details) {}

  public record Attachment(String id, String name, String contentType, Long size, String url) {}

  public record Anomaly(
      String id,
      @JsonProperty("tenant_id") String tenantId,
      String code,
      @JsonProperty("equipment_id") String equipmentId,
      @JsonProperty("company_org_id") String companyOrgId,
      @JsonProperty("department_org_id") String departmentOrgId,
      @JsonProperty("source_kind") String sourceKind,
      @JsonProperty("source_task_id") String sourceTaskId,
      @JsonProperty("source_item_id") String sourceItemId,
      String description,
      String severity,
      String status,
      @JsonProperty("discovered_by") String discoveredBy,
      @JsonProperty("discovered_name") String discoveredName,
      @JsonProperty("discovered_at") String discoveredAt,
      @JsonProperty("repair_id") String repairId,
      @JsonProperty("plan_id") String planId,
      String resolution,
      @JsonProperty("resolved_at") String resolvedAt,
      @JsonProperty("row_version") int legacyRowVersion,
      @JsonProperty("created_at") String createdAt,
      String equipmentCode,
      String equipmentName,
      List<Attachment> attachments,
      int rowVersion,
      String url,
      String name,
      List<Event> events,
      List<String> actions) {}

  public record RepairPlan(
      String id,
      @JsonProperty("tenant_id") String tenantId,
      String code,
      @JsonProperty("equipment_id") String equipmentId,
      String description,
      @JsonProperty("planned_at") String plannedAt,
      @JsonProperty("owner_id") String ownerId,
      @JsonProperty("creator_id") String creatorId,
      @JsonProperty("fault_type") String faultType,
      @JsonProperty("fault_type_id") String faultTypeId,
      String severity,
      JsonNode attachments,
      String status,
      @JsonProperty("repair_id") String repairId,
      @JsonProperty("row_version") int legacyRowVersion,
      @JsonProperty("generation_error") String generationError,
      @JsonProperty("created_at") String createdAt,
      String equipmentCode,
      String equipmentName,
      int rowVersion,
      List<String> anomalyIds,
      List<Event> events) {}
}
