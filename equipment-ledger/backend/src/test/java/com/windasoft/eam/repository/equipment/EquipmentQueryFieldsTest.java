package com.windasoft.eam.repository.equipment;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class EquipmentQueryFieldsTest {
  final ObjectMapper json = new ObjectMapper();

  @Test
  void registeredAliasesAndDisplayedValuesUseTheSameExpressions() {
    assertEquals(
        "eam_equipment.equipment_code",
        EquipmentQueryFields.order(new Sort("equipmentCode", Direction.ASC)));
    assertTrue(
        EquipmentQueryFields.order(new Sort("category", Direction.ASC)).contains("b.deleted=0"));
    assertTrue(
        EquipmentQueryFields.order(new Sort("acquiredAt", Direction.ASC))
            .contains("COALESCE(eam_equipment.purchase_date,eam_equipment.acquired_at)"));
    assertThrows(
        IllegalArgumentException.class,
        () -> EquipmentQueryFields.order(new Sort("name;DROP TABLE", Direction.ASC)));
  }

  @Test
  void ordinaryAndAdvancedConditionsOnSameFieldRemainAnIntersection() {
    var sql =
        EquipmentQueryFields.filters(
            json,
            List.of(
                new Condition("department", Operator.EQ, List.of("A")),
                new Condition("department", Operator.IN, List.of("B"))));
    assertEquals(List.of("A", "B"), sql.arguments());
    assertTrue(sql.sql().contains("AND"));
  }

  @Test
  void keywordTreatsWildcardCharactersAsLiterals() {
    var sql = EquipmentQueryFields.keyword("a%_!");
    assertTrue(sql.sql().contains("ESCAPE '!'"));
    assertEquals(8, sql.arguments().size());
    assertTrue(sql.arguments().stream().allMatch("%a!%!_!!%"::equals));
  }

  @Test
  void emptyChecksPreserveTextAndNumberSemantics() {
    assertTrue(
        EquipmentQueryFields.filters(
                json, List.of(new Condition("code", Operator.EMPTY, List.of())))
            .sql()
            .contains("equipment_code=''"));
    var numeric =
        EquipmentQueryFields.filters(
                json, List.of(new Condition("amount", Operator.NOT_EMPTY, List.of())))
            .sql();
    assertTrue(numeric.contains("acquired_amount IS NOT NULL"));
    assertFalse(numeric.contains("=''"));
  }

  @Test
  void rejectsInvalidTypesRangesAndOperators() {
    for (var c :
        List.of(
            new Condition("amount", Operator.CONTAINS, List.of("1")),
            new Condition("code", Operator.RANGE, List.of("1", "2")),
            new Condition("amount", Operator.RANGE, List.of("9", "2")),
            new Condition("acquiredAt", Operator.RANGE, List.of("2026-02-30", "")),
            new Condition("unknown", Operator.IN, List.of("x")),
            new Condition("code", Operator.EMPTY, List.of("x")))) {
      assertThrows(
          IllegalArgumentException.class,
          () -> EquipmentQueryFields.filters(json, List.of(c)),
          c.toString());
    }
  }
}
