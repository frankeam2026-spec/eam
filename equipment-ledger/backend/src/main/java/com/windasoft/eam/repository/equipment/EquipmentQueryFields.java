package com.windasoft.eam.repository.equipment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.Condition;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.Operator;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.Sort;
import com.windasoft.eam.repository.common.SqlListFilters;
import com.windasoft.eam.repository.common.SqlPredicate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 仅使用已登记字段表达式，不接收HTTP或模型提供的SQL标识符。 */
final class EquipmentQueryFields {
  private EquipmentQueryFields() {}

  private static final String T = "eam_equipment.";

  private static SqlListFilters.Field field(String expression, SqlListFilters.Kind kind) {
    return new SqlListFilters.Field(expression, kind);
  }

  private static SqlListFilters.Field text(String column) {
    return field(T + column, SqlListFilters.Kind.TEXT);
  }

  private static SqlListFilters.Field choice(String column) {
    return field(T + column, SqlListFilters.Kind.CHOICE);
  }

  private static SqlListFilters.Field date(String column) {
    return field(T + column, SqlListFilters.Kind.DATE);
  }

  private static String base(String id, String type, String snapshot) {
    return "COALESCE((SELECT b.name FROM eam_base_item b WHERE b.tenant_id="
        + T
        + "tenant_id AND b.id="
        + T
        + id
        + " AND b.item_type='"
        + type
        + "' AND b.deleted=0),"
        + T
        + snapshot
        + ")";
  }

  private static final Map<String, SqlListFilters.Field> FIELDS =
      Map.ofEntries(
          Map.entry("code", text("equipment_code")),
          Map.entry("name", text("name")),
          Map.entry("state", choice("equipment_status")),
          Map.entry(
              "category",
              field(base("category_id", "CATEGORY", "category"), SqlListFilters.Kind.CHOICE)),
          Map.entry(
              "useState",
              field(base("use_status_id", "USE_STATUS", "use_status"), SqlListFilters.Kind.CHOICE)),
          Map.entry(
              "source", field(base("source_id", "SOURCE", "source"), SqlListFilters.Kind.CHOICE)),
          Map.entry(
              "unit", field(base("unit_id", "UNIT", "unit_name"), SqlListFilters.Kind.CHOICE)),
          Map.entry("assetNo", text("asset_no")),
          Map.entry("department", text("department")),
          Map.entry("company", text("company")),
          Map.entry("functionLocation", choice("function_location")),
          Map.entry("model", text("model")),
          Map.entry("serial", text("serial_no")),
          Map.entry("owner", text("owner_name")),
          Map.entry("brand", text("brand")),
          Map.entry("supplier", text("supplier")),
          Map.entry("tagCode", text("tag_code")),
          Map.entry("level", text("equipment_level")),
          Map.entry(
              "acquiredAt",
              field(
                  "COALESCE(" + T + "purchase_date," + T + "acquired_at)",
                  SqlListFilters.Kind.DATE)),
          Map.entry("warranty", date("warranty_until")),
          Map.entry("inServiceDate", date("in_service_date")),
          Map.entry("retirementDate", date("expected_retirement_date")),
          Map.entry("amount", field(T + "acquired_amount", SqlListFilters.Kind.NUMBER)));
  private static final Map<String, String> SORT_ALIASES =
      Map.ofEntries(
          Map.entry("equipmentCode", "code"),
          Map.entry("serialNo", "serial"),
          Map.entry("ownerName", "owner"),
          Map.entry("equipmentStatus", "state"),
          Map.entry("useStatus", "useState"),
          Map.entry("acquiredAmount", "amount"),
          Map.entry("warrantyUntil", "warranty"));

  static SqlPredicate filters(ObjectMapper json, List<Condition> conditions) {
    var result = SqlPredicate.of("1=1");
    for (var condition : conditions) {
      if (condition.field().startsWith("custom:")) continue;
      var field = FIELDS.get(condition.field());
      if (field == null)
        throw new LocalizedArgumentException("server.common.message0015", condition.field());
      validate(condition, field.kind());
      var values = condition.values();
      if (condition.operator() == Operator.RANGE && values.size() == 1)
        values = List.of(values.getFirst(), "");
      result =
          result.and(
              SqlListFilters.conditions(
                  json,
                  List.of(new Condition(condition.field(), condition.operator(), values)),
                  FIELDS));
    }
    return result;
  }

  private static void validate(Condition c, SqlListFilters.Kind type) {
    var op = c.operator();
    var values = c.values();
    boolean valid =
        switch (op) {
          case EMPTY, NOT_EMPTY -> values.isEmpty();
          case CONTAINS, EQ ->
              type == SqlListFilters.Kind.TEXT
                  && values.size() == 1
                  && !values.getFirst().isBlank();
          case IN ->
              Set.of(SqlListFilters.Kind.TEXT, SqlListFilters.Kind.CHOICE).contains(type)
                  && !values.isEmpty()
                  && values.stream().noneMatch(String::isBlank);
          case RANGE ->
              Set.of(SqlListFilters.Kind.DATE, SqlListFilters.Kind.NUMBER).contains(type)
                  && !values.isEmpty()
                  && values.size() <= 2
                  && values.stream().anyMatch(v -> !v.isBlank());
        };
    if (!valid) throw new LocalizedArgumentException("server.common.message0013");
  }

  static SqlPredicate keyword(String value) {
    var result = SqlPredicate.of("1=0");
    for (String field :
        List.of("code", "assetNo", "tagCode", "name", "model", "serial", "department", "owner"))
      result = result.or(SqlListFilters.contains(FIELDS.get(field).expression(), value));
    return result;
  }

  static String order(Sort sort) {
    String key = SORT_ALIASES.getOrDefault(sort.field(), sort.field());
    return expression(key);
  }

  static String expression(String key) {
    if (key.equals("createdAt")) {
      return T + "created_at";
    }
    var field = FIELDS.get(key);
    if (field == null) {
      throw new LocalizedArgumentException("server.common.message0015", key);
    }
    return field.expression();
  }
}
