package com.windasoft.eam.controller.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.inspection.InspectionDeviceOption;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.service.business.BusinessQueryService;
import com.windasoft.eam.service.inspection.DailyArrangementService;
import com.windasoft.eam.service.inspection.DailyInspectionExemptionService;
import com.windasoft.eam.service.inspection.DailyInspectionPlanService;
import com.windasoft.eam.service.inspection.InspectionCsvService;
import com.windasoft.eam.service.inspection.InspectionGenerationService;
import com.windasoft.eam.service.inspection.InspectionListService;
import com.windasoft.eam.service.inspection.InspectionProjectService;
import com.windasoft.eam.service.inspection.InspectionService;
import com.windasoft.eam.service.inspection.InspectionTaskAssignmentService;
import com.windasoft.eam.service.inspection.InspectionTaskService;
import com.windasoft.eam.service.inspection.InspectionValidationService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InspectionDeviceOptionContractTest {
  @Test
  void selectorKeepsFlatPageNumericCountersAndExactStringId() throws Exception {
    var plans = mock(DailyInspectionPlanService.class);
    when(plans.dailyDeviceOptions("设备", 2, 50))
        .thenReturn(
            new PageResult<>(
                List.of(new InspectionDeviceOption(9007199254740993L, "设备", "EQ-1")), 2, 50, 51));
    var response =
        mvc(plans)
            .perform(
                get("/api/v1/inspection/daily-device-options")
                    .param("q", "设备")
                    .param("page", "2")
                    .param("size", "50"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var body = new ObjectMapper().readTree(response.getContentAsByteArray());
    assertEquals(2, body.size());
    assertEquals(3, body.path("data").get(0).size());
    assertEquals("9007199254740993", body.path("data").get(0).path("id").asText());
    assertTrue(body.path("data").get(0).path("id").isTextual());
    assertTrue(body.path("page").path("total").isIntegralNumber());
    assertEquals(2, body.path("page").path("number").asInt());
    assertEquals(50, body.path("page").path("size").asInt());
    assertEquals(51, body.path("page").path("total").asLong());
  }

  @Test
  void retiredEventOptionsStayEmptyAndKeepPermissionsAndClamping() throws Exception {
    var permissions = mock(PermissionService.class);
    var equipment = mock(EquipmentQueryRepository.class);
    var plans =
        new DailyInspectionPlanService(
            mock(AuthContext.class),
            permissions,
            mock(InspectionProjectService.class),
            mock(InspectionValidationService.class),
            mock(DailyArrangementService.class),
            equipment,
            mock(InspectionGenerationService.class),
            mock(InspectionTaskAssignmentService.class),
            new ObjectMapper());
    var response =
        mvc(plans)
            .perform(
                get("/api/v1/inspection/daily-event-options")
                    .param("page", "0")
                    .param("size", "1000"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var body = new ObjectMapper().readTree(response.getContentAsByteArray());
    assertTrue(body.path("data").isArray());
    assertEquals(0, body.path("data").size());
    assertEquals(0, body.path("page").path("total").asLong());
    assertEquals(1, body.path("page").path("number").asInt());
    assertEquals(200, body.path("page").path("size").asInt());
    verify(permissions).require("inspectionDailyTask", "查看");
    verify(permissions).require("inspectionDailyTask", "执行");
    verifyNoInteractions(equipment);
  }

  private MockMvc mvc(DailyInspectionPlanService plans) {
    return MockMvcBuilders.standaloneSetup(
            new InspectionController(
                mock(BusinessQueryService.class),
                mock(InspectionListService.class),
                mock(InspectionCsvService.class),
                mock(DailyArrangementService.class),
                mock(InspectionService.class),
                mock(InspectionTaskService.class),
                mock(DailyInspectionExemptionService.class),
                plans))
        .build();
  }
}
