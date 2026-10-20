package com.windasoft.eam.repository.common;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.system.PermissionScope;
import java.util.*;
import org.junit.jupiter.api.Test;

class SqlListFiltersTest {
  final ObjectMapper json = new ObjectMapper();
  final Map<String, SqlListFilters.Field> fields =
      Map.of(
          "name",
          new SqlListFilters.Field("t.name", SqlListFilters.Kind.TEXT),
          "time",
          new SqlListFilters.Field("t.created_at", SqlListFilters.Kind.DATETIME),
          "amount",
          new SqlListFilters.Field("t.amount", SqlListFilters.Kind.NUMBER));

  @Test
  void rawValuesAreNeverSqlIdentifiersOrFragments() {
    var q =
        ListQuery.from(
            Map.of(
                "conditions",
                "[{\"field\":\"name\",\"operator\":\"contains\",\"values\":[\"' OR 1=1 --\"]}]"));
    var predicate = SqlListFilters.apply(json, q, fields);
    assertFalse(predicate.sql().contains("OR 1=1"));
    assertEquals("%' or 1=1 --%", predicate.arguments().get(0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlListFilters.apply(
                json, ListQuery.from(Map.of("filters", "{\"name; DROP TABLE t\":\"x\"}")), fields));
  }

  @Test
  void upperDateIncludesEntireSelectedDay() {
    var p =
        SqlListFilters.apply(
            json,
            ListQuery.from(
                Map.of(
                    "conditions",
                    "[{\"field\":\"time\",\"operator\":\"range\",\"values\":[\"2026-10-17\",\"2026-10-17\"]}]")),
            fields);
    assertTrue(p.sql().contains("t.created_at<?"));
    assertEquals(java.time.LocalDateTime.of(2026, 10, 18, 0, 0), p.arguments().get(1));
  }

  @Test
  void supportsSingleEndedRangeAndRejectsReversed() {
    var p =
        SqlListFilters.apply(
            json,
            ListQuery.from(
                Map.of(
                    "conditions",
                    "[{\"field\":\"amount\",\"operator\":\"range\",\"values\":[\"0\",\"\"]}]")),
            fields);
    assertEquals(new java.math.BigDecimal("0"), p.arguments().get(0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlListFilters.apply(
                json,
                ListQuery.from(
                    Map.of(
                        "conditions",
                        "[{\"field\":\"amount\",\"operator\":\"range\",\"values\":[\"2\",\"1\"]}]")),
                fields));
  }

  @Test
  void pagingAndSortAreBounded() {
    assertThrows(IllegalArgumentException.class, () -> ListQuery.from(Map.of("page", "0")));
    assertThrows(IllegalArgumentException.class, () -> ListQuery.from(Map.of("size", "201")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlListFilters.order(ListQuery.from(Map.of("sortBy", "name desc;--")), fields, "t.id"));
    assertEquals(
        "t.name asc,t.id asc",
        SqlListFilters.order(
            ListQuery.from(Map.of("sortBy", "name", "direction", "asc")), fields, "t.id"));
  }

  @Test
  void absentPermissionIsDenyAndCompanyScopeDoesNotBecomeAll() {
    var p =
        SqlScopes.scope(
            new PermissionScope(false, null, Set.of(), false),
            "t.company_id",
            "t.department_id",
            "t.owner_id",
            1);
    assertEquals("1=0", p.sql());
    p =
        SqlScopes.scope(
            new PermissionScope(false, 2L, Set.of(3L), true),
            "t.company_id",
            "t.department_id",
            "t.owner_id",
            1);
    assertFalse(p.sql().contains("1=1"));
    assertEquals(List.of(2L, 3L, 1L), p.arguments());
  }

  @Test
  void constantScopeBranchesDoNotLeaveUnusedBindingsOrJsonWork() {
    var bound = SqlPredicate.of("t.id=?", 42L);
    assertEquals(bound, SqlPredicate.of("1=1").and(bound));
    assertEquals(bound, SqlPredicate.of("1=0").or(bound));
    assertEquals("1=0", bound.and(SqlPredicate.of("1=0")).sql());
    var all = bound.or(SqlPredicate.of("1=1"));
    assertEquals("1=1", all.sql());
    assertTrue(all.arguments().isEmpty());
  }
}
