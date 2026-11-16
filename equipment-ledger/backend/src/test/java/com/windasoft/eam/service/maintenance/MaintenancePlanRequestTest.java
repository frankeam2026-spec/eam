package com.windasoft.eam.service.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.controller.maintenance.MaintenancePlanController;
import com.windasoft.eam.dto.maintenance.MaintenanceGenerationResult;
import com.windasoft.eam.dto.maintenance.MaintenancePlanDetail;
import com.windasoft.eam.dto.maintenance.MaintenancePlanRequest;
import com.windasoft.eam.dto.maintenance.MaintenancePlanRow;
import com.windasoft.eam.support.ContractTypes;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MaintenancePlanRequestTest {
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  @Test
  void fixedContractsAreExplicit() {
    ContractTypes.assertExplicit(MaintenancePlanRequest.class);
    ContractTypes.assertExplicit(MaintenancePlanDetail.class);
    ContractTypes.assertExplicit(MaintenancePlanRow.class);
    ContractTypes.assertExplicit(MaintenanceGenerationResult.class);
  }

  @Test
  void missingAndNullSelectionsRemainDifferent() throws Exception {
    var absent =
        json.readValue(
            """
            {"details":[{}]}
            """,
            MaintenancePlanRequest.class);
    var first = absent.getDetails().getFirst();
    assertFalse(first.isPeriodPresent());
    assertFalse(first.isUnitPresent());
    assertFalse(first.isValidDurationPresent());
    assertFalse(first.isProjectRefsPresent());
    assertFalse(absent.isReminderModePresent());
    var cleared =
        json.readValue(
            """
{"reminderMode":null,"details":[{"period":null,"unit":null,"validDuration":null,"projectRefs":null}]}
""",
            MaintenancePlanRequest.class);
    var item = cleared.getDetails().getFirst();
    assertTrue(item.isPeriodPresent());
    assertTrue(item.isUnitPresent());
    assertTrue(item.isValidDurationPresent());
    assertTrue(item.isProjectRefsPresent());
    assertTrue(cleared.isReminderModePresent());
    assertNull(item.getPeriod());
    assertNull(item.getProjectRefs());
  }

  @Test
  void fractionalCountsCannotBeTruncated() {
    assertThrows(
        JsonProcessingException.class,
        () ->
            json.readValue(
                """
                {"advanceDays":1.5}
                """,
                MaintenancePlanRequest.class));
    assertThrows(
        JsonProcessingException.class,
        () ->
            json.readValue(
                """
                {"details":[{"period":1.5}]}
                """,
                MaintenancePlanRequest.class));
    assertThrows(
        JsonProcessingException.class,
        () ->
            json.readValue(
                """
                {"details":[{"validDuration":1.5}]}
                """,
                MaintenancePlanRequest.class));
  }

  @Test
  void integerStringsAndVersionSyntaxRemainCompatible() throws Exception {
    var request =
        json.readValue(
            """
            {"advanceDays":"1","details":[{"period":"2","validDuration":"7"}]}
            """,
            MaintenancePlanRequest.class);
    assertEquals(1L, request.getAdvanceDays());
    assertEquals(2L, request.getDetails().getFirst().getPeriod());
    assertThrows(
        JsonProcessingException.class,
        () ->
            json.readValue(
                """
                {"rowVersion":"1"}
                """,
                MaintenancePlanRequest.class));
  }

  @Test
  void invalidBusinessDateIsRejectedDuringBinding() {
    assertThrows(
        JsonProcessingException.class,
        () ->
            json.readValue(
                """
                {"details":[{"nextDate":"2026-02-31"}]}
                """,
                MaintenancePlanRequest.class));
  }

  @Test
  void optionalBlankIdentifiersFromExistingEditorRemainUnselected() throws Exception {
    var request =
        json.readValue(
            """
{"departmentId":"","reminderPolicyId":"","details":[{"id":"","standardId":"","clientKey":"editor-row"}]}
""",
            MaintenancePlanRequest.class);
    assertNull(request.getDepartmentId());
    assertNull(request.getReminderPolicyId());
    assertNull(request.getDetails().getFirst().getId());
    assertNull(request.getDetails().getFirst().getStandardId());
    assertThrows(
        JsonProcessingException.class,
        () ->
            json.readValue(
                """
                {"reminderPolicyId":"1.5"}
                """,
                MaintenancePlanRequest.class));
  }

  @Test
  void manualGenerationKeepsOriginalCountEnvelope() throws Exception {
    var generation = mock(MaintenanceGenerationService.class);
    when(generation.generatePlan(42L)).thenReturn(3);
    var controller =
        new MaintenancePlanController(
            mock(MaintenancePlanService.class), mock(MaintenanceListService.class), generation);
    MockMvcBuilders.standaloneSetup(controller)
        .build()
        .perform(post("/api/v1/maintenance/plans/42/generate"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"count":3}
                    """));
    verify(generation).generatePlan(42L);
  }
}
