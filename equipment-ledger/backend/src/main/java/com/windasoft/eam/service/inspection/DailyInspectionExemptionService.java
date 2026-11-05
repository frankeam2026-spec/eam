package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.domain.inspection.InspectionTaskAction;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.service.inventory.InventoryMutex;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 日常点检免检申请及其待执行任务处置。 */
@Service
public class DailyInspectionExemptionService {
  private final InventoryMutex taskMutex;
  private final InspectionProjectService projects;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final DailyArrangementRepository arrangements;
  private final InspectionTaskService taskCommands;

  public DailyInspectionExemptionService(
      InventoryMutex taskMutex,
      InspectionProjectService projects,
      ObjectMapper json,
      AuthContext auth,
      PermissionService permissions,
      DailyArrangementRepository arrangements,
      InspectionTaskService taskCommands) {
    this.taskMutex = taskMutex;
    this.projects = projects;
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
    this.arrangements = arrangements;
    this.taskCommands = taskCommands;
  }

  @Transactional
  public Map<String, Object> exempt(long planId, Map<String, Object> body) {
    // Acquire the shared task lock before plan/task row locks, matching task submission lock order.
    taskMutex.enter();
    var plan = projects.raw("daily-plans", auth.tenantId(), planId, true);
    if (!projects.scoped(plan, "查看")) deny();
    VersionGuard.checkPayload((Number) plan.get("row_version"), body);

    var scope = permissions.inspection("daily", "免检");
    Long department = nullable(plan.get("department_id"));
    if (!scope.matches(
        permissions.companyOfOrganization(department), department, null, auth.userId())) deny();

    var data = decode(plan.get("payload"));
    var knownEquipment =
        rows(data.get("details")).stream().map(d -> number(d.get("equipmentId"))).toList();
    var selectedEquipment = ids(body.get("equipmentIds"));
    require(
        !selectedEquipment.isEmpty()
            && selectedEquipment.size() <= 500
            && knownEquipment.containsAll(selectedEquipment),
        "请选择计划内的设备");

    var start = LocalDateTime.parse(text(body, "startAt"));
    var end = LocalDateTime.parse(text(body, "endAt"));
    require(
        end.isAfter(start),
        com.windasoft.eam.common.MessageContext.text("daily.error.exemptEnd"));
    String reason = text(body, "reason");
    require(
        !reason.isBlank() && reason.length() <= 1000,
        com.windasoft.eam.common.MessageContext.text("daily.error.reason"));

    int count = 0;
    for (long equipmentId : selectedEquipment.stream().sorted().toList()) {
      arrangements.addExemption(
          auth.tenantId(),
          IdWorker.getId(),
          planId,
          equipmentId,
          start.toString(),
          end.toString(),
          reason,
          auth.userId());
      for (var task :
          arrangements.exemptionCandidates(
              auth.tenantId(), planId, equipmentId, start.toString(), end.toString())) {
        taskCommands.action(
            number(task.get("id")),
            InspectionTaskAction.EXEMPT.code(),
            Map.of("rowVersion", task.get("rowVersion"), "description", reason));
        count++;
      }
    }
    return Map.of("count", count);
  }

  private Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0012");
    }
  }
}
