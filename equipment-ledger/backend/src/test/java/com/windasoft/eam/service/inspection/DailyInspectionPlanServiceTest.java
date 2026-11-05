package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DailyInspectionPlanServiceTest {
  @Test
  void dailyEventCarriesActorAndSupervisorContextIntoGeneration() {
    var auth = mock(AuthContext.class);
    var permissions = mock(PermissionService.class);
    var projects = mock(InspectionProjectService.class);
    var validation = mock(InspectionValidationService.class);
    var arrangements = mock(DailyArrangementService.class);
    var storage = mock(DailyArrangementRepository.class);
    var generation = mock(InspectionGenerationService.class);
    var service =
        new DailyInspectionPlanService(
            auth, permissions, projects, validation, arrangements, storage, generation, new ObjectMapper());

    when(auth.tenantId()).thenReturn(7L);
    when(auth.userId()).thenReturn(13L);
    var plan = Map.<String, Object>of("row_version", 4L);
    when(projects.raw("daily-plans", 7L, 99L, true)).thenReturn(plan);
    when(projects.scoped(plan, "编辑")).thenReturn(true);
    when(generation.manualEvent(eq(7L), eq(plan), anyMap()))
        .thenReturn(Map.of("created", 1));
    var body = new LinkedHashMap<String, Object>(Map.of("rowVersion", 4L));

    assertEquals(Map.of("created", 1), service.dailyEvent(99L, body));
    assertEquals("13", body.get("_actorId"));
    assertEquals(true, body.get("_supervisor"));
    verify(generation).manualEvent(7L, plan, body);
  }

  @Test
  void dailyPreviewRejectsInvalidDateRangeBeforeGeneration() {
    var auth = mock(AuthContext.class);
    var permissions = mock(PermissionService.class);
    var projects = mock(InspectionProjectService.class);
    var validation = mock(InspectionValidationService.class);
    var arrangements = mock(DailyArrangementService.class);
    var storage = mock(DailyArrangementRepository.class);
    var generation = mock(InspectionGenerationService.class);
    var service =
        new DailyInspectionPlanService(
            auth, permissions, projects, validation, arrangements, storage, generation, new ObjectMapper());

    when(auth.tenantId()).thenReturn(7L);
    when(auth.userId()).thenReturn(13L);
    when(permissions.inspection("daily-plans", "新增"))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    when(permissions.companyOfOrganization(3L)).thenReturn(2L);
    var body =
        Map.<String, Object>of(
            "id", "",
            "departmentId", 3L,
            "startDate", "2026-11-06",
            "endDate", "2026-11-05",
            "details", List.of());

    assertThrows(IllegalArgumentException.class, () -> service.dailyPreview(body));
    verify(generation, never()).preview(anyLong(), anyMap(), anyInt());
  }
}
