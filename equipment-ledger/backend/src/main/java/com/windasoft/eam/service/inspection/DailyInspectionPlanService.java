package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.deny;
import static com.windasoft.eam.common.inspection.InspectionSupport.number;
import static com.windasoft.eam.common.inspection.InspectionSupport.require;
import static com.windasoft.eam.common.inspection.InspectionSupport.rows;
import static com.windasoft.eam.common.inspection.InspectionSupport.text;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.FieldValidationException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.common.MessageRef;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.inspection.InspectionDeviceOption;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private final EquipmentQueryRepository equipmentQueries;
  private final InspectionGenerationService generation;
  private final InspectionTaskAssignmentService assignments;
  private final ObjectMapper json;

  public DailyInspectionPlanService(
      AuthContext auth,
      PermissionService permissions,
      InspectionProjectService projects,
      InspectionValidationService validation,
      DailyArrangementService arrangements,
      EquipmentQueryRepository equipmentQueries,
      InspectionGenerationService generation,
      InspectionTaskAssignmentService assignments,
      ObjectMapper json) {
    this.auth = auth;
    this.permissions = permissions;
    this.projects = projects;
    this.validation = validation;
    this.arrangements = arrangements;
    this.equipmentQueries = equipmentQueries;
    this.generation = generation;
    this.assignments = assignments;
    this.json = json;
  }

  /** 候选按点检执行权限查询，不能借用设备台账查看范围；分页裁剪保持原选择器契约。 */
  public PageResult<InspectionDeviceOption> dailyDeviceOptions(String keyword, int page, int size) {
    int effectivePage = Math.max(1, page), effectiveSize = Math.max(1, Math.min(100, size));
    var result =
        equipmentQueries.deviceCandidates(
            auth.tenantId(),
            auth.userId(),
            permissions.inspection("daily", "执行"),
            keyword,
            effectivePage,
            effectiveSize);
    var rows =
        result.getRecords().stream()
            .map(e -> new InspectionDeviceOption(e.getId(), e.getName(), e.getEquipmentCode()))
            .toList();
    return new PageResult<>(rows, effectivePage, effectiveSize, result.getTotal());
  }

  /** 人工事件已停用，旧客户端仍收到原空页；Void明确无候选模型，不为关闭的能力虚构字段。 */
  public PageResult<Void> dailyEventOptions(String keyword, int page, int size) {
    permissions.require("inspectionDailyTask", "查看");
    permissions.require("inspectionDailyTask", "执行");
    return new PageResult<>(List.of(), Math.max(1, page), Math.max(1, Math.min(200, size)), 0);
  }

  /** 编辑预览可能补回原安排的排班，必须先复核原计划，再校验目标部门及设备；否则可借请求部门绕过对象范围。 校验只修改请求副本，不锁定计划、不保存安排，也不创建任务或通知。 */
  @Transactional(readOnly = true)
  public Map<String, Object> dailyPreview(Map<String, Object> body) {
    permissions.require("inspectionDailyPlan", "查看");
    var data = copy(body);
    Long planId = text(data, "id").isBlank() ? null : number(data.get("id"));
    String action = planId == null ? "新增" : "编辑";
    if (planId != null) {
      var existing = projects.raw("daily-plans", auth.tenantId(), planId, false);
      if (!projects.scoped(existing, action)) {
        deny();
      }
    }
    long department = number(data.get("departmentId"));
    var scope = permissions.inspection("daily-plans", action);
    if (!scope.matches(
        permissions.companyOfOrganization(department), department, auth.userId(), auth.userId())) {
      deny();
    }
    LocalDate start = previewDate(data, "startDate"), end = previewDate(data, "endDate");
    require(!end.isBefore(start), MessageContext.text("daily.error.dateRange"));
    require(
        rows(data.get("details")).size() <= 500, MessageContext.text("daily.error.equipmentLimit"));
    for (var detail : rows(data.get("details"))) {
      validation.checkEquipment(
          projects.equipment(auth.tenantId(), number(detail.get("equipmentId"))),
          "daily-plans",
          action);
      projects.projects(auth.tenantId(), detail, true);
    }
    arrangements.validate(auth.tenantId(), planId, data);
    return generation.preview(
        auth.tenantId(), data, Math.toIntExact(number(data.getOrDefault("previewDays", 7))));
  }

  private LocalDate previewDate(Map<String, Object> data, String field) {
    try {
      return LocalDate.parse(text(data, field));
    } catch (DateTimeParseException invalid) {
      throw new FieldValidationException(field, MessageRef.of("daily.error.date"));
    }
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
