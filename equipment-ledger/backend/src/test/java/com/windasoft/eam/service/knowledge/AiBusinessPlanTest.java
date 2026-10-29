package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.dto.knowledge.*;
import com.windasoft.eam.repository.knowledge.AiBusinessFilters;
import java.time.*;
import org.junit.jupiter.api.Test;

class AiBusinessPlanTest {
  final ObjectMapper json = new ObjectMapper();

  JsonNode plan(String extra) throws Exception {
    return json.readTree("{\"domain\":\"equipment\",\"operation\":\"COUNT\"" + extra + "}");
  }

  @Test
  void irrelevantEmptyModelDefaultsDoNotRejectValidBusinessQueries() throws Exception {
    var p = AiBusinessPlan.parse(plan(",\"aggregate\":\"\",\"bucket\":\"\""));
    assertEquals("count", p.aggregate());
    assertEquals("month", p.bucket());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AiBusinessPlan.parse(
                json.readTree("{\"domain\":\"repair\",\"operation\":\"TREND\",\"bucket\":\"\"}")));
  }

  @Test
  void rejectsUnknownFieldsAndNeverAcceptsSql() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> AiBusinessPlan.parse(plan(",\"sql\":\"DELETE FROM eam_equipment\"")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AiBusinessPlan.parse(
                plan(
                    ",\"where\":{\"field\":\"status) OR"
                        + " 1=1\",\"op\":\"eq\",\"values\":[\"故障\"]}")));
    assertThrows(
        IllegalArgumentException.class,
        () -> AiBusinessPlan.parse(plan(",\"where\":{\"op\":\"NOT\",\"children\":[]}")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AiBusinessPlan.parse(
                plan(",\"where\":{\"field\":\"ownerId\",\"op\":\"eq\",\"values\":[12]}")));
  }

  @Test
  void nestedConditionsKeepEveryBoundValueAndEscapeWildcards() throws Exception {
    var p =
        AiBusinessPlan.parse(
            plan(
                ",\"where\":{\"op\":\"AND\",\"children\":[{\"field\":\"ownerId\",\"op\":\"eq\",\"values\":[\"$me\"]},{\"op\":\"OR\",\"children\":[{\"field\":\"status\",\"op\":\"eq\",\"values\":[\"故障\"]},{\"field\":\"name\",\"op\":\"contains\",\"values\":[\"%_'"
                    + " OR 1=1\"]}]}]}"));
    var filter =
        AiBusinessFilters.compile(
            p.where(),
            AiBusinessCatalog.ALL.get(p.domain()),
            LocalDateTime.of(2026, 10, 29, 12, 0),
            9007199254740993L,
            10001L);
    assertFalse(filter.sql().contains("OR 1=1"));
    assertEquals("9007199254740993", filter.arguments().get(0));
    assertEquals("故障", filter.arguments().get(3));
    assertEquals(5, filter.arguments().size());
  }

  @Test
  void inventoryCannotSumIncompatibleUnits() throws Exception {
    var p =
        AiBusinessPlan.parse(
            json.readTree(
                "{\"domain\":\"stock\",\"operation\":\"COUNT\",\"aggregate\":\"sum\",\"measure\":\"available\"}"));
    assertEquals("GROUP", p.operation());
    assertEquals(java.util.List.of("unit"), p.groupBy());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AiBusinessPlan.parse(
                json.readTree(
                    "{\"domain\":\"stock\",\"operation\":\"GROUP\",\"groupBy\":[\"company\",\"warehouse\"],\"aggregate\":\"sum\",\"measure\":\"quantity\"}")));
  }

  @Test
  void calendarAndLeapMonthBoundariesAreExplicit() throws Exception {
    var p = AiBusinessPlan.parse(plan(",\"period\":\"lastMonth\""));
    assertArrayEquals(
        new LocalDate[] {LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)},
        AiBusinessQueryService.bounds(p, LocalDate.of(2024, 3, 31)));
    var week = AiBusinessPlan.parse(plan(",\"period\":\"week\""));
    assertArrayEquals(
        new LocalDate[] {LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)},
        AiBusinessQueryService.bounds(week, LocalDate.of(2026, 10, 29)));
  }
}
