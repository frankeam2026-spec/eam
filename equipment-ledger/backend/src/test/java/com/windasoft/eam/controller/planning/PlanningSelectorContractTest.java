package com.windasoft.eam.controller.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.inspection.InspectionStandardCandidate;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardRow;
import com.windasoft.eam.dto.planning.PlanningCandidate.ResolveRequest;
import com.windasoft.eam.service.planning.PlanningSelectorService;
import com.windasoft.eam.support.ContractTypes;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class PlanningSelectorContractTest {
  @Test
  void endpointsAndBothStandardVariantsKeepExplicitTypes() {
    for (var method : PlanningSelectorController.class.getDeclaredMethods()) {
      if (method.isAnnotationPresent(GetMapping.class)
          || method.isAnnotationPresent(PostMapping.class)) {
        ContractTypes.assertEndpoint(method);
      }
    }
    ContractTypes.assertExplicit(MaintenanceStandardRow.class);
    ContractTypes.assertExplicit(InspectionStandardCandidate.class);
  }

  @Test
  void resolveAcceptsStringAndIntegerIdsWithoutRoundingInvalidValues() throws Exception {
    var json = new ObjectMapper();
    var request =
        json.readValue(
            """
            {"equipmentIds":["9007199254740993", 9007199254740993], "standardIds":[123]}
            """,
            ResolveRequest.class);
    assertEquals(List.of("9007199254740993", "9007199254740993"), request.equipmentIds());
    assertEquals(
        List.of(9007199254740993L),
        List.copyOf(PlanningSelectorService.ids(request.equipmentIds())));
    var invalid = json.readValue("{\"standardIds\":[10.5]}", ResolveRequest.class);
    assertThrows(
        IllegalArgumentException.class, () -> PlanningSelectorService.ids(invalid.standardIds()));
  }
}
