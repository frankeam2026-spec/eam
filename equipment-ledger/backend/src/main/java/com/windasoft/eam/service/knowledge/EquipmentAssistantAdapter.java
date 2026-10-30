package com.windasoft.eam.service.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Aggregate;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.All;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Bucket;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Comparison;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Condition;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.DateRange;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.DateTimeValue;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.DateValue;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Field;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.FieldSort;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Filter;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Identifier;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Junction;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Logical;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Operation;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Period;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Reference;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.ResultSort;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.SortBy;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Text;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Value;
import com.windasoft.eam.dto.equipment.EquipmentAssistantResult;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.Direction;
import com.windasoft.eam.dto.knowledge.AiBusinessPlan;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 仅适配助手既有计划与表格响应，不参与设备授权、不构造或执行SQL。 */
public final class EquipmentAssistantAdapter {
  private EquipmentAssistantAdapter() {}

  public static EquipmentAssistantQuery request(
      AiBusinessPlan plan, Long equipment, int page, int size) {
    if (!"equipment".equals(plan.domain())) {
      throw new LocalizedArgumentException("server.equipment.assistant.invalid");
    }
    var operation = Operation.valueOf(plan.operation());
    var aggregate = Aggregate.valueOf(plan.aggregate().toUpperCase(Locale.ROOT));
    SortBy sort = null;
    if (operation != Operation.COUNT && !plan.sortBy().isEmpty()) {
      sort =
          switch (plan.sortBy()) {
            case "count" -> ResultSort.COUNT;
            case "value" -> ResultSort.VALUE;
            case "period" -> ResultSort.PERIOD;
            default -> new FieldSort(Field.fromKey(plan.sortBy()));
          };
    }
    Period period =
        switch (plan.period()) {
          case "lastWeek" -> Period.LAST_WEEK;
          case "lastMonth" -> Period.LAST_MONTH;
          case "lastYear" -> Period.LAST_YEAR;
          default -> Period.valueOf(plan.period().toUpperCase(Locale.ROOT));
        };
    return new EquipmentAssistantQuery(
        operation,
        filter(plan.where()),
        plan.groupBy().stream().map(Field::fromKey).toList(),
        aggregate,
        aggregate == Aggregate.COUNT ? null : Field.fromKey(plan.measure()),
        sort,
        Direction.parse(plan.direction()),
        plan.timeField().isEmpty() ? null : Field.fromKey(plan.timeField()),
        period,
        period == Period.CUSTOM
            ? new DateRange(LocalDate.parse(plan.from()), LocalDate.parse(plan.to()))
            : null,
        Bucket.valueOf(plan.bucket().toUpperCase(Locale.ROOT)),
        plan.topN(),
        equipment,
        page,
        size);
  }

  private static Filter filter(JsonNode node) {
    if (node.isMissingNode() || node.isNull()) {
      return new All();
    }
    if (node.has("children")) {
      var children = new ArrayList<Filter>();
      for (var child : node.path("children")) {
        children.add(filter(child));
      }
      return new Junction(Logical.valueOf(node.path("op").asText()), children);
    }
    var field = Field.fromKey(node.path("field").asText());
    var op =
        node.path("op").asText().equals("notEmpty")
            ? Comparison.NOT_EMPTY
            : Comparison.valueOf(node.path("op").asText().toUpperCase(Locale.ROOT));
    var values = new ArrayList<Value>();
    try {
      for (var item : node.path("values")) {
        String value = item.asText();
        values.add(
            switch (field.kind()) {
              case TEXT -> new Text(value);
              case ID ->
                  value.equals("$me") && field == Field.OWNER_ID
                      ? Reference.ME
                      : new Identifier(Long.valueOf(value));
              case DATE ->
                  value.equals("$today") ? Reference.TODAY : new DateValue(LocalDate.parse(value));
              case DATETIME ->
                  value.equals("$now")
                      ? Reference.NOW
                      : value.equals("$today")
                          ? Reference.TODAY
                          : new DateTimeValue(
                              value.length() == 10
                                  ? LocalDate.parse(value).atStartOfDay()
                                  : LocalDateTime.parse(value.replace(' ', 'T')));
            });
      }
    } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
      throw new LocalizedArgumentException("server.equipment.assistant.invalid");
    }
    return new Condition(field, op, values);
  }

  /** 动态键仅用于兼容已有助手表格；设备模块的请求及结果均为明确类型。 */
  public static Map<String, Object> response(
      EquipmentAssistantResult result, EquipmentAssistantQuery plan) {
    var out = new LinkedHashMap<String, Object>();
    if (result.data() instanceof EquipmentAssistantResult.Count(long value1, long total2)) {
      out.put("value", value1);
      out.put("total", total2);
      return out;
    }
    var rows = new ArrayList<Map<String, Object>>();
    var columns = new ArrayList<String>();
    long total, records;
    if (result.data() instanceof EquipmentAssistantResult.ListData(
      java.util.List<EquipmentAssistantResult.Row> rows1, long total1, long records1
    )) {
      total = total1;
      records = records1;
      for (var field : EquipmentAssistantQuery.LIST_FIELDS) {
        columns.add(field.key());
      }
      for (var row : rows1) {
        var value = new LinkedHashMap<String, Object>();
        value.put("id", row.id().toString());
        value.put("code", row.code());
        value.put("name", row.name());
        value.put("category", row.category());
        value.put("status", row.status());
        value.put("useState", row.useState());
        value.put("department", row.department());
        value.put("model", row.model());
        rows.add(value);
      }
    } else {
      var grouped = (EquipmentAssistantResult.Grouped) result.data();
      total = grouped.total();
      records = grouped.records();
      if (plan.operation() == Operation.TREND) {
        columns.add("period");
      }
      for (var field : plan.groupBy()) {
        columns.add(field.key());
      }
      columns.add("count");
      if (plan.aggregate() != Aggregate.COUNT) {
        columns.add("value");
      }
      columns.add("percent");
      for (var row : grouped.rows()) {
        var value = new LinkedHashMap<String, Object>();
        if (plan.operation() == Operation.TREND) {
          value.put("period", row.period());
        }
        for (var dimension : row.dimensions()) {
          value.put(dimension.field().key(), dimension.value());
          if (dimension.field().identity() != null) {
            value.put(
                dimension.field().identity().key(),
                dimension.identity() == null ? "" : dimension.identity().toString());
          }
        }
        value.put("count", row.count());
        value.put("value", row.value());
        value.put("percent", row.percent());
        rows.add(value);
      }
    }
    out.put("data", rows);
    out.put("total", total);
    out.put("records", records);
    out.put("columns", columns);
    return out;
  }
}
