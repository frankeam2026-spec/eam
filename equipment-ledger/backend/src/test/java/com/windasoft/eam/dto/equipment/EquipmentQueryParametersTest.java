package com.windasoft.eam.dto.equipment;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class EquipmentQueryParametersTest {
  final ObjectMapper json = new ObjectMapper();

  @Test
  void preservesOrdinaryAndAdvancedIntersectionAndIgnoresUiMetadata() {
    var query =
        EquipmentQueryParameters.read(
            Map.of(
                "department",
                "生产部",
                "advanced",
                """
[{"field":"department","operator":"in","values":["维修部"],"selectedNodeIds":["71003"],"valueLabels":{"71003":"维修部"}}]
"""),
            json);
    assertEquals(2, query.conditions().size());
    assertEquals(Operator.EQ, query.conditions().getFirst().operator());
    assertEquals(List.of("维修部"), query.conditions().getLast().values());
    assertEquals(20, query.size());
  }

  @Test
  void separatesUiPaginationFromExportBatchAndPreservesDateFilters() {
    var input =
        Map.of(
            "size",
            "500",
            "page",
            "2",
            "acquiredFrom",
            "2026-10-01",
            "acquiredTo",
            "2026-10-31",
            "warrantyBefore",
            "2027-01-01");
    assertEquals(20, EquipmentQueryParameters.read(input, json).size());
    var export = EquipmentQueryParameters.export(input, json);
    assertEquals(1, export.page());
    assertEquals(500, export.size());
    assertEquals(EquipmentQueryParameters.read(input, json).conditions(), export.conditions());
    assertEquals(50, EquipmentQueryParameters.read(Map.of("size", "50"), json).size());
  }

  @Test
  void rejectsMalformedInputsInsteadOfBroadeningQuery() {
    for (var input :
        List.of(
            Map.of("page", "0"),
            Map.of("size", "bad"),
            Map.of("direction", "drop"),
            Map.of("acquiredFrom", "2026-02-30"),
            Map.of("scopeType", "location", "scopeIds", "1,,2"),
            Map.of("scopeType", "organization"),
            Map.of("scopeType", "category", "scopeIds", "-1"),
            Map.of("advanced", "[{\"field\":\"name\",\"operator\":\"contains\",\"values\":[3]}]"),
            Map.of(
                "advanced",
                "[{\"field\":\"name\",\"operator\":\"contains\",\"values\":[\"a\"]},{\"field\":\"name\",\"operator\":\"contains\",\"values\":[\"b\"]}]"))) {
      assertThrows(
          IllegalArgumentException.class,
          () -> EquipmentQueryParameters.read(input, json),
          input.toString());
    }
  }

  @Test
  void normalizesScopeAndPreventsMutableCallerState() {
    var query =
        EquipmentQueryParameters.read(
            Map.of(
                "scopeType",
                "location",
                "scopeIds",
                "11,11,12",
                "scopeCompanyId",
                "7",
                "direction",
                "asc"),
            json);
    assertEquals(List.of(11L, 12L), query.scope().ids());
    assertEquals(7L, query.scope().companyId());
    assertEquals(Direction.ASC, query.sort().direction());
    assertThrows(UnsupportedOperationException.class, () -> query.scope().ids().add(99L));
  }
}
