package com.windasoft.eam.repository.common;

import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.dto.common.ListQuery;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Only server-registered expressions can reach SQL; all request values remain bound parameters. */
public final class SqlListFilters {
  private SqlListFilters() {}

  public enum Kind {
    TEXT,
    NUMBER,
    DATE,
    DATETIME,
    CHOICE
  }

  public record Field(String expression, Kind kind) {}

  public static String like(String value) {
    return "%"
        + value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
        + "%";
  }

  public static SqlPredicate contains(String expression, String value) {
    return SqlPredicate.of(
        "LOWER(COALESCE(CAST(" + expression + " AS CHAR),'')) LIKE ? ESCAPE '!'", like(value));
  }

  public static SqlPredicate apply(ObjectMapper json, ListQuery query, Map<String, Field> fields) {
    var result = SqlPredicate.of("1=1");
    try {
      JsonNode columns = json.readTree(query.filters()),
          conditions = json.readTree(query.conditions());
      if (!columns.isObject() || !conditions.isArray() || conditions.size() > 150)
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0009");
      for (var iterator = columns.fields(); iterator.hasNext(); ) {
        var entry = iterator.next();
        var field = required(fields, entry.getKey());
        if (!entry.getValue().isTextual())
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.common.message0010");
        result = result.and(contains(field.expression(), entry.getValue().asText()));
      }
      var seen = new HashSet<String>();
      for (var condition : conditions) {
        String key = condition.path("field").asText();
        if (key.startsWith("custom:")) continue;
        var field = required(fields, key);
        var values = condition.path("values");
        String op = condition.path("operator").asText();
        if (!seen.add(key) || !values.isArray() || values.size() > 2000)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.common.message0009");
        for (var value : values)
          if (!value.isTextual() || value.asText().length() > 500)
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.common.message0011");
        if (op.equals("contains") && values.size() == 1)
          result = result.and(contains(field.expression(), values.get(0).asText()));
        else if ((op.equals("in") || op.equals("eq")) && !values.isEmpty()) {
          var args = new ArrayList<Object>();
          values.forEach(v -> args.add(v.asText()));
          result = result.and(SqlScopes.in(field.expression(), args));
        } else if (op.equals("range")
            && values.size() == 2
            && Set.of(Kind.NUMBER, Kind.DATE, Kind.DATETIME).contains(field.kind())) {
          Comparable low = null, high = null;
          for (int i = 0; i < 2; i++) {
            String value = values.get(i).asText();
            if (value.isBlank()) continue;
            boolean exclusive = false;
            Object bound;
            if (field.kind() == Kind.NUMBER) bound = new BigDecimal(value);
            else if (field.kind() == Kind.DATE) bound = LocalDate.parse(value);
            else if (value.length() == 10) {
              bound =
                  i == 1
                      ? LocalDate.parse(value).plusDays(1).atStartOfDay()
                      : LocalDate.parse(value).atStartOfDay();
              exclusive = i == 1;
            } else bound = LocalDateTime.parse(value.replace(' ', 'T'));
            if (i == 0) low = (Comparable) bound;
            else high = (Comparable) bound;
            result =
                result.and(
                    SqlPredicate.of(
                        field.expression() + (i == 0 ? ">=" : exclusive ? "<" : "<=") + "?",
                        bound));
          }
          if (low != null && high != null && low.compareTo(high) > 0)
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.common.message0012");
        } else
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.common.message0013");
      }
      return result;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0014");
    }
  }

  private static Field required(Map<String, Field> fields, String key) {
    var field = fields.get(key);
    if (field == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.common.message0015", key);
    return field;
  }

  public static String order(ListQuery query, Map<String, Field> fields, String fallback) {
    if (query.sortBy().isBlank()) return fallback;
    return required(fields, query.sortBy()).expression()
        + " "
        + query.direction()
        + ",t.id "
        + query.direction();
  }
}
