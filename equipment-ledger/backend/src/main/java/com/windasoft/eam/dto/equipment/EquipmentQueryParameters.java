package com.windasoft.eam.dto.equipment;

import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.*;
import java.time.*;
import java.util.*;

/** Compatibility adapter for existing GET parameters. Parsing happens once at the boundary. */
public final class EquipmentQueryParameters {
  private EquipmentQueryParameters() {}

  public static EquipmentListQuery read(Map<String, ?> parameters, ObjectMapper json) {
    return read(parameters, json, false);
  }

  public static EquipmentListQuery export(Map<String, ?> parameters, ObjectMapper json) {
    return read(parameters, json, true);
  }

  private static EquipmentListQuery read(Map<String, ?> p, ObjectMapper json, boolean export) {
    try {
      long page = export ? 1 : number(p, "page", 1), size = export ? 500 : number(p, "size", 20);
      if (!export && !Set.of(20L, 50L, 100L).contains(size)) size = 20;
      var conditions = new ArrayList<Condition>();
      add(p, conditions, "category", Operator.IN);
      add(p, conditions, "state", Operator.IN);
      add(p, conditions, "department", Operator.EQ);
      for (String field : List.of("company", "model", "serial", "owner", "brand"))
        add(p, conditions, field, Operator.CONTAINS);
      add(p, conditions, "useState", Operator.IN);
      add(p, conditions, "source", Operator.IN);
      String from = date(p, "acquiredFrom"),
          to = date(p, "acquiredTo"),
          warranty = date(p, "warrantyBefore");
      if (!from.isEmpty() || !to.isEmpty())
        conditions.add(new Condition("acquiredAt", Operator.RANGE, List.of(from, to)));
      if (!warranty.isEmpty())
        conditions.add(new Condition("warranty", Operator.RANGE, List.of("", warranty)));
      String raw = text(p, "advanced");
      if (!raw.isBlank()) {
        if (raw.length() > 256000) throw invalid();
        JsonNode array = json.readTree(raw);
        if (!array.isArray() || array.size() > 150) throw invalid();
        var seen = new HashSet<String>();
        for (var item : array) {
          if (!item.isObject()
              || !item.path("field").isTextual()
              || !item.path("operator").isTextual()
              || !item.path("values").isArray()
              || !seen.add(item.path("field").asText())) throw invalid();
          // Tree selection and labels are persisted UI state, never query operands.
          var keys = item.fieldNames();
          while (keys.hasNext())
            if (!Set.of("field", "operator", "values", "selectedNodeIds", "valueLabels")
                .contains(keys.next())) throw invalid();
          var values = new ArrayList<String>();
          for (var value : item.path("values")) {
            if (!value.isTextual()) throw invalid();
            values.add(value.asText());
          }
          conditions.add(
              new Condition(
                  item.path("field").asText(),
                  Operator.parse(item.path("operator").asText()),
                  values));
        }
      }
      String scopeType = text(p, "scopeType"),
          scopeIds = text(p, "scopeIds"),
          company = text(p, "scopeCompanyId");
      var ids = new ArrayList<Long>();
      if (!scopeIds.isBlank())
        for (String id : scopeIds.split(",", -1)) {
          if (id.isBlank()) throw invalid();
          ids.add(Long.valueOf(id));
        }
      ScopeType type =
          scopeType.isBlank()
              ? ScopeType.NONE
              : ScopeType.valueOf(scopeType.toUpperCase(Locale.ROOT));
      return new EquipmentListQuery(
          page,
          size,
          text(p, "keyword"),
          conditions,
          new Sort(text(p, "sortBy"), Direction.parse(text(p, "direction"))),
          new Scope(type, ids, company.isBlank() ? null : Long.valueOf(company)));
    } catch (LocalizedArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw invalid();
    }
  }

  private static void add(Map<String, ?> p, List<Condition> out, String field, Operator op) {
    String value = text(p, field);
    if (!value.isBlank()) out.add(new Condition(field, op, List.of(value)));
  }

  private static String text(Map<String, ?> p, String key) {
    return Objects.toString(p.get(key), "");
  }

  private static long number(Map<String, ?> p, String key, long fallback) {
    String value = text(p, key);
    return value.isBlank() ? fallback : Long.parseLong(value);
  }

  private static String date(Map<String, ?> p, String key) {
    String value = text(p, key);
    return value.isBlank() ? "" : LocalDate.parse(value).toString();
  }

  private static LocalizedArgumentException invalid() {
    return new LocalizedArgumentException("server.common.message0009");
  }
}
