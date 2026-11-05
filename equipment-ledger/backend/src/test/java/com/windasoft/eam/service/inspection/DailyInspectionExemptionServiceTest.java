package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.inspection.InspectionTaskAction;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.service.inventory.InventoryMutex;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DailyInspectionExemptionServiceTest {
  @Test
  void exemptionUsesTheSharedTaskLockAndOnlyTransitionsEligiblePlanTasks() {
    var taskMutex = mock(InventoryMutex.class);
    var projects = mock(InspectionProjectService.class);
    var auth = mock(AuthContext.class);
    var permissions = mock(PermissionService.class);
    var arrangements = mock(DailyArrangementRepository.class);
    var taskCommands = mock(InspectionTaskService.class);
    var service =
        new DailyInspectionExemptionService(
            taskMutex, projects, new ObjectMapper(), auth, permissions, arrangements, taskCommands);

    when(auth.tenantId()).thenReturn(7L);
    when(auth.userId()).thenReturn(13L);
    var plan =
        Map.<String, Object>of(
            "payload", "{\"details\":[{\"equipmentId\":11}]}",
            "department_id", 3L,
            "row_version", 4L);
    when(projects.raw("daily-plans", 7L, 99L, true)).thenReturn(plan);
    when(projects.scoped(plan, "查看")).thenReturn(true);
    when(permissions.inspection("daily", "免检"))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    when(permissions.companyOfOrganization(3L)).thenReturn(2L);

    String start = "2026-11-05T08:00";
    String end = "2026-11-05T17:00";
    String reason = "计划设备停用";
    when(arrangements.exemptionCandidates(7L, 99L, 11L, start, end))
        .thenReturn(List.of(Map.of("id", 1001L, "rowVersion", 5L)));

    var body =
        new LinkedHashMap<String, Object>(
            Map.of(
                "rowVersion", 4L,
                "equipmentIds", List.of(11L),
                "startAt", start,
                "endAt", end,
                "reason", reason));

    assertEquals(1, service.exempt(99L, body).get("count"));

    var order = inOrder(taskMutex, projects);
    order.verify(taskMutex).enter();
    order.verify(projects).raw("daily-plans", 7L, 99L, true);
    verify(arrangements)
        .addExemption(eq(7L), anyLong(), eq(99L), eq(11L), eq(start), eq(end), eq(reason), eq(13L));
    verify(taskCommands)
        .action(
            1001L,
            InspectionTaskAction.EXEMPT.code(),
            Map.of("rowVersion", 5L, "description", reason));
  }
}
