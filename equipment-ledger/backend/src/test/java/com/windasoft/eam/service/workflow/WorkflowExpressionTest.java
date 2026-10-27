package com.windasoft.eam.service.workflow;

import static com.windasoft.eam.common.workflow.WorkflowExpression.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class WorkflowExpressionTest {
  final Map<String, Variable> fields =
      Map.of(
          "quantity", new Variable("quantity", "数量", Type.NUMBER, true, false),
          "amount", new Variable("amount", "金额", Type.NUMBER, false, true),
          "departmentId", new Variable("departmentId", "部门", Type.REFERENCE, true, true),
          "lines.code", new Variable("lines.code", "料号", Type.TEXT, true, false),
          "lines.quantity", new Variable("lines.quantity", "数量", Type.NUMBER, true, false));

  boolean run(String expression, Map<String, Object> values) {
    return test(parse(expression, fields), values).matched();
  }

  @Test
  void boundariesAndExactDecimals() {
    assertTrue(run("quantity >= 0.30 AND quantity <= 0.3", Map.of("quantity", "0.300000")));
    assertFalse(run("quantity > 0.3", Map.of("quantity", "0.30")));
    assertTrue(run("NOT (quantity < 10 OR quantity > 20)", Map.of("quantity", 15)));
  }

  @Test
  void identifiersAndNullGuards() {
    assertTrue(run("departmentId IN (71003,71004)", Map.of("departmentId", 71003L)));
    assertFalse(run("departmentId NOT IN (71003,71004)", Map.of("departmentId", 71003L)));
    Map<String, Object> values = new HashMap<>();
    values.put("departmentId", null);
    assertTrue(run("departmentId IS NULL OR departmentId == 71003", values));
    assertThrows(IllegalArgumentException.class, () -> run("departmentId == 71003", values));
    assertThrows(IllegalArgumentException.class, () -> run("departmentId IS NULL", Map.of()));
  }

  @Test
  void collectionPredicatesStayOnSameRow() {
    var values =
        Map.<String, Object>of(
            "lines",
            List.of(
                Map.of("code", "WX-1", "quantity", 1), Map.of("code", "OTHER", "quantity", 20)));
    assertFalse(run("ANY(lines, lines.code STARTS_WITH \"WX-\" AND lines.quantity > 10)", values));
    assertTrue(run("ANY(lines, lines.code CONTAINS \"WX\")", values));
    assertTrue(run("ALL(lines, lines.quantity >= 1)", values));
    assertTrue(run("SUM(lines.quantity) == 21", values));
    assertFalse(run("ALL(lines, lines.quantity > 0)", Map.of("lines", List.of())));
  }

  @Test
  void rejectUnavailableVariablesScriptsAndTypeMismatch() {
    for (String bad :
        List.of(
            "amount > 0",
            "unknown == 0",
            "quantity > \"10\"",
            "departmentId == 1.5",
            "lines.quantity > 0",
            "T(java.lang.Runtime).exec(\"cmd\")",
            "quantity == 1; DELETE FROM users",
            "ANY(lines, ALL(lines, lines.quantity > 0))",
            "SUM(quantity) > 0",
            "quantity = 1"))
      assertThrows(IllegalArgumentException.class, () -> parse(bad, fields), bad);
  }

  @Test
  void nullNumbersNeverBecomeZero() {
    var row = new HashMap<String, Object>();
    row.put("quantity", null);
    assertThrows(
        IllegalArgumentException.class,
        () -> run("SUM(lines.quantity) == 0", Map.of("lines", List.of(row))));
    assertThrows(
        IllegalArgumentException.class, () -> parse("NOT ".repeat(40) + "quantity > 1", fields));
  }
}
