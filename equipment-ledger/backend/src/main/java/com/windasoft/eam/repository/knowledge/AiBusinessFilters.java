package com.windasoft.eam.repository.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.dto.knowledge.AiBusinessCatalog;
import com.windasoft.eam.repository.common.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Compiles only registered fields to bound SQL predicates. */
public final class AiBusinessFilters {
  private AiBusinessFilters() {}

  public static SqlPredicate compile(
      JsonNode node, AiBusinessCatalog.Domain domain, LocalDateTime now, long userId, long tenant) {
    if (node.isMissingNode() || node.isNull()) return SqlPredicate.of("1=1");
    String op = node.path("op").asText();
    if (Set.of("AND", "OR", "NOT").contains(op)) {
      if (op.equals("NOT")) {
        var value = compile(node.path("children").get(0), domain, now, userId, tenant);
        return new SqlPredicate("NOT (" + value.sql() + ")", value.arguments());
      }
      var value = SqlPredicate.of(op.equals("AND") ? "1=1" : "1=0");
      for (var child : node.path("children"))
        value =
            op.equals("AND")
                ? value.and(compile(child, domain, now, userId, tenant))
                : value.or(compile(child, domain, now, userId, tenant));
      return value;
    }
    String field = node.path("field").asText(), column = "s.`" + field + "`";
    String type = domain.fields().get(field).type();
    boolean textType = Set.of("TEXT", "ID").contains(type);
    if (op.equals("empty"))
      return SqlPredicate.of(
          textType ? "(" + column + " IS NULL OR " + column + "='')" : column + " IS NULL");
    if (op.equals("notEmpty"))
      return SqlPredicate.of(
          textType
              ? "(" + column + " IS NOT NULL AND " + column + "<>'')"
              : column + " IS NOT NULL");
    var values = new ArrayList<Object>();
    for (var item : node.path("values")) {
      String text = item.asText();
      Object value = text;
      if (type.equals("ID")) {
        if (text.equals("$me") && Set.of("ownerId", "reporterId").contains(field))
          value = Long.toString(userId);
        else if (!text.matches("[0-9]{1,19}"))
          throw new IllegalArgumentException("关联ID无效，请明确对象名称或编号");
      } else if (type.equals("NUMBER")) value = new BigDecimal(text);
      else if (type.equals("DATE"))
        value = text.equals("$today") ? now.toLocalDate() : LocalDate.parse(text);
      else if (type.equals("DATETIME"))
        value =
            text.equals("$now")
                ? now
                : text.equals("$today")
                    ? now.toLocalDate().atStartOfDay()
                    : text.length() == 10
                        ? LocalDate.parse(text).atStartOfDay()
                        : LocalDateTime.parse(text.replace(' ', 'T'));
      values.add(value);
    }
    if (field.equals("ownerId") && Set.of("eq", "in", "ne").contains(op)) {
      String table =
          switch (domain.menu()) {
            case "equipment" -> "eam_equipment_owner";
            case "maintenanceTask" -> "eam_maintenance_task_person";
            case "inspectionDailyTask", "inspectionPeriodicTask" -> "eam_inspection_task_person";
            default -> "";
          };
      if (!table.isEmpty()) {
        String marks = String.join(",", Collections.nCopies(values.size(), "?"));
        String relation = domain.menu().equals("equipment") ? "equipment_id" : "task_id";
        String role = domain.menu().equals("equipment") ? "" : " AND p.role_code='owner'";
        var args = new ArrayList<Object>(values);
        args.add(tenant);
        args.addAll(values);
        var predicate =
            new SqlPredicate(
                "(COALESCE("
                    + column
                    + ",'') IN ("
                    + marks
                    + ") OR EXISTS(SELECT 1 FROM "
                    + table
                    + " p WHERE p.tenant_id=? AND p."
                    + relation
                    + "=s.id"
                    + role
                    + " AND p.user_id IN ("
                    + marks
                    + ")))",
                args);
        return op.equals("ne")
            ? new SqlPredicate("NOT (" + predicate.sql() + ")", predicate.arguments())
            : predicate;
      }
    }
    if (op.equals("in"))
      return new SqlPredicate(
          column + " IN (" + String.join(",", Collections.nCopies(values.size(), "?")) + ")",
          values);
    if (op.equals("contains")) return SqlListFilters.contains(column, values.getFirst().toString());
    String operator =
        switch (op) {
          case "eq" -> "=";
          case "ne" -> "<>";
          case "gt" -> ">";
          case "gte" -> ">=";
          case "lt" -> "<";
          case "lte" -> "<=";
          default -> throw new IllegalArgumentException("筛选操作无效");
        };
    return new SqlPredicate(column + operator + "?", values);
  }
}
