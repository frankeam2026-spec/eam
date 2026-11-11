package com.windasoft.eam.service.inspection;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.ApiExceptionHandler;
import com.windasoft.eam.controller.inspection.InspectionController;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.service.business.BusinessQueryService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 通过原 Controller 与异常处理器验证预览的对象权限和非法日期响应，不能只证明抛出了某种异常。 */
class InspectionPreviewContractTest {
  private final ObjectMapper json = new ObjectMapper();
  private final AuthContext auth = mock(AuthContext.class);
  private final PermissionService permissions = mock(PermissionService.class);
  private final InspectionProjectService projects = mock(InspectionProjectService.class);
  private final DailyArrangementService arrangements = mock(DailyArrangementService.class);
  private final InspectionGenerationService generation = mock(InspectionGenerationService.class);
  private final Map<String, Object> existing = Map.of("id", 99L, "creator_id", 27L);
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    when(auth.tenantId()).thenReturn(7L);
    when(auth.userId()).thenReturn(13L);
    when(permissions.inspection(eq("daily-plans"), anyString()))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    when(projects.raw("daily-plans", 7L, 99L, false)).thenReturn(existing);
    when(projects.scoped(existing, "编辑")).thenReturn(true);
    when(generation.preview(eq(7L), anyMap(), eq(7)))
        .thenReturn(Map.of("total", 0, "rows", List.of()));
    var service =
        new DailyInspectionPlanService(
            auth,
            permissions,
            projects,
            mock(InspectionValidationService.class),
            arrangements,
            mock(EquipmentQueryRepository.class),
            generation,
            mock(InspectionTaskAssignmentService.class),
            json);
    var controller =
        new InspectionController(
            mock(BusinessQueryService.class),
            mock(InspectionListService.class),
            mock(InspectionCsvService.class),
            arrangements,
            mock(InspectionService.class),
            mock(InspectionTaskService.class),
            mock(DailyInspectionExemptionService.class),
            service);
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  private Map<String, Object> request(String id) {
    var data = new LinkedHashMap<String, Object>();
    data.put("id", id);
    data.put("departmentId", 3L);
    data.put("startDate", "2026-11-12");
    data.put("endDate", "2026-12-12");
    data.put("details", List.of());
    return data;
  }

  private void send(Map<String, Object> body, int expectedStatus) throws Exception {
    mvc.perform(
            post("/api/v1/inspection/daily-plans/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)))
        .andExpect(status().is(expectedStatus));
  }

  @Test
  void editedPreviewRejectsForbiddenOriginalPlanBeforeReadingArrangements() throws Exception {
    when(projects.scoped(existing, "编辑")).thenReturn(false);
    send(request("99"), 403);
    verify(arrangements, never()).validate(anyLong(), anyLong(), anyMap());
    verify(generation, never()).preview(anyLong(), anyMap(), anyInt());
  }

  @Test
  void editedPreviewStillChecksTargetDepartmentAfterOriginalPlan() throws Exception {
    when(permissions.inspection("daily-plans", "编辑"))
        .thenReturn(new PermissionScope(false, null, Set.of(), false));
    send(request("99"), 403);
    verify(generation, never()).preview(anyLong(), anyMap(), anyInt());
  }

  @Test
  void permittedEditRetainsExistingResponseAndUsesTenantBoundPlanLookup() throws Exception {
    mvc.perform(
            post("/api/v1/inspection/daily-plans/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request("99"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0))
        .andExpect(jsonPath("$.data.rows").isArray());
    verify(projects).raw("daily-plans", 7L, 99L, false);
  }

  @Test
  void newPlanPreviewDoesNotRequireAPersistedPlan() throws Exception {
    send(request(""), 200);
    verify(projects, never()).raw(eq("daily-plans"), anyLong(), anyLong(), eq(false));
  }

  @ParameterizedTest
  @CsvSource({"startDate,''", "endDate,''", "startDate,2026-13-01", "endDate,2025-02-29"})
  void malformedDatesReturnFieldErrorsInsteadOfServerErrors(String field, String value)
      throws Exception {
    var body = request("");
    body.put(field, value);
    mvc.perform(
            post("/api/v1/inspection/daily-plans/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors." + field).exists());
    verify(generation, never()).preview(anyLong(), anyMap(), anyInt());
  }
}
