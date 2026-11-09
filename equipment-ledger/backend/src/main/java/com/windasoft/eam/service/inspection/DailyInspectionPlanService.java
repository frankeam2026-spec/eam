package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 日常点检计划预览、排班影响评估和人工事件编排。 */
@Service
public class DailyInspectionPlanService {
  private final AuthContext auth;
  private final PermissionService permissions;
  private final InspectionProjectService projects;
  private final InspectionValidationService validation;
  private final DailyArrangementService arrangements;
  private final DailyArrangementRepository storage;
  private final InspectionGenerationService generation;
  private final InspectionTaskAssignmentService assignments;
  private final ObjectMapper json;

  public DailyInspectionPlanService(
      AuthContext auth,
      PermissionService permissions,
      InspectionProjectService projects,
      InspectionValidationService validation,
      DailyArrangementService arrangements,
      DailyArrangementRepository storage,
      InspectionGenerationService generation,
      InspectionTaskAssignmentService assignments,
      ObjectMapper json) {
    this.auth = auth;
    this.permissions = permissions;
    this.projects = projects;
    this.validation = validation;
    this.arrangements = arrangements;
    this.storage = storage;
    this.generation = generation;
    this.assignments = assignments;
    this.json = json;
  }

  public Object dailyDeviceOptions(String keyword, int page, int size) {
    return storage.deviceEquipment(
        auth.tenantId(),
        auth.userId(),
        permissions.inspection("daily", "执行"),
        keyword,
        Math.max(1, page),
        Math.max(1, Math.min(100, size)));
  }

  public Object dailyEventOptions(String keyword, int page, int size) {
    permissions.require("inspectionDailyTask", "查看");
    permissions.require("inspectionDailyTask", "执行");
    return new com.windasoft.eam.dto.common.PageResult<>(
        List.of(), Math.max(1, page), Math.max(1, Math.min(200, size)), 0);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> dailyPreview(Map<String, Object> body) {
    permissions.require("inspectionDailyPlan", "查看");
    var data = copy(body);
    long department = number(data.get("departmentId"));
    var scope =
        permissions.inspection(
            "daily-plans", text(data, "id").isBlank() ? "新增" : "编辑");
    if (!scope.matches(
        permissions.companyOfOrganization(department), department, auth.userId(), auth.userId()))
      deny();
    require(
        !LocalDate.parse(text(data, "endDate"))
            .isBefore(LocalDate.parse(text(data, "startDate"))),
        com.windasoft.eam.common.MessageContext.text("daily.error.dateRange"));
    require(
        rows(data.get("details")).size() <= 500,
        com.windasoft.eam.common.MessageContext.text("daily.error.equipmentLimit"));
    for (var detail : rows(data.get("details"))) {
      validation.checkEquipment(
          projects.equipment(auth.tenantId(), number(detail.get("equipmentId"))),
          "daily-plans",
          text(data, "id").isBlank() ? "新增" : "编辑");
      projects.projects(auth.tenantId(), detail, true);
    }
    arrangements.validate(
        auth.tenantId(), text(data, "id").isBlank() ? null : number(data.get("id")), data);
    return generation.preview(
        auth.tenantId(), data, Math.toIntExact(number(data.getOrDefault("previewDays", 7))));
  }

  @Transactional
  public Map<String, Object> assignmentImpact(Map<String, Object> body) {
    long planId = number(body.get("id"));
    var plan = projects.raw("daily-plans", auth.tenantId(), planId, true);
    if (!projects.scoped(plan, "编辑")) deny();
    VersionGuard.checkPayload((Number) plan.get("row_version"), body);
    dailyPreview(body);
    return Map.of("count", assignments.assignmentImpact(auth.tenantId(), planId, copy(body)));
  }

  @Transactional
  public Map<String, Object> dailyEvent(long planId, Map<String, Object> body) {
    var plan = projects.raw("daily-plans", auth.tenantId(), planId, true);
    boolean supervisor = projects.scoped(plan, "编辑");
    if (!supervisor) permissions.require("inspectionDailyTask", "执行");
    body.put("_actorId", Long.toString(auth.userId()));
    body.put("_supervisor", supervisor);
    VersionGuard.checkPayload((Number) plan.get("row_version"), body);
    return generation.manualEvent(auth.tenantId(), plan, body);
  }

  private Map<String, Object> copy(Object value) {
    try {
      return json.readValue(
          json.writeValueAsString(value), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0012");
    }
  }
}
