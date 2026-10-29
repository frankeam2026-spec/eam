package com.windasoft.eam.dto.knowledge;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;

/** Bounded read-only plan. HTTP and model plans pass the same validation. */
public record AiBusinessPlan(
    String domain,
    String operation,
    JsonNode where,
    List<String> groupBy,
    String measure,
    String aggregate,
    String sortBy,
    String direction,
    String timeField,
    String period,
    String from,
    String to,
    String bucket,
    int topN) {
  static final Set<String> OPERATIONS = Set.of("LIST", "COUNT", "GROUP", "TREND");

  public static AiBusinessPlan parse(JsonNode node) {
    if (node == null || !node.isObject() || node.toString().length() > 12000)
      throw new IllegalArgumentException("业务查询计划无效");
    var allowed =
        Set.of(
            "domain",
            "operation",
            "where",
            "groupBy",
            "measure",
            "aggregate",
            "sortBy",
            "direction",
            "timeField",
            "period",
            "from",
            "to",
            "bucket",
            "topN");
    node.fieldNames()
        .forEachRemaining(
            key -> {
              if (!allowed.contains(key)) throw new IllegalArgumentException("业务查询包含未允许的参数");
            });
    String domain = node.path("domain").asText();
    var spec = AiBusinessCatalog.ALL.get(domain);
    if (spec == null) throw new IllegalArgumentException("查询业务尚未接入");
    String operation = node.path("operation").asText();
    if (!OPERATIONS.contains(operation)) throw new IllegalArgumentException("查询操作无效");
    var groups = new ArrayList<String>();
    var input = node.path("groupBy");
    if (!input.isMissingNode() && !input.isArray()) throw new IllegalArgumentException("分组维度无效");
    for (var value : input) {
      String field = value.asText();
      var f = spec.fields().get(field);
      if (f == null || !f.type().equals("TEXT") || groups.contains(field) || groups.size() >= 2)
        throw new IllegalArgumentException("最多支持两个明确的分组维度");
      groups.add(field);
    }
    String aggregate = node.path("aggregate").asText("count"),
        measure = node.path("measure").asText("");
    if (aggregate.isBlank()) aggregate = "count";
    if (!Set.of("count", "distinct", "sum", "avg", "min", "max").contains(aggregate))
      throw new IllegalArgumentException("汇总方式无效");
    if (!aggregate.equals("count")) {
      var field = spec.fields().get(measure);
      if (field == null
          || !(field.type().equals("NUMBER")
              || aggregate.equals("distinct") && Set.of("TEXT", "ID").contains(field.type())))
        throw new IllegalArgumentException("该字段未开放数值汇总");
    }
    if (domain.equals("stock")
        && !Set.of("count", "distinct").contains(aggregate)
        && !groups.contains("unit")) {
      if (groups.size() == 2) throw new IllegalArgumentException("库存数量不能跨计量单位汇总，请减少一个分组维度");
      groups.add("unit");
    }
    if (operation.equals("COUNT") && !groups.isEmpty()) operation = "GROUP";
    if (operation.equals("LIST") && (!aggregate.equals("count") || !groups.isEmpty()))
      throw new IllegalArgumentException("明细和分组汇总需分别查询，不能忽略聚合要求");
    String sort = node.path("sortBy").asText("");
    if (!sort.isEmpty()
        && !Set.of("count", "value", "period").contains(sort)
        && !spec.fields().containsKey(sort)) throw new IllegalArgumentException("排序字段无效");
    if (operation.equals("LIST") && !sort.isEmpty() && !spec.fields().containsKey(sort))
      throw new IllegalArgumentException("明细排序字段无效");
    if (operation.equals("GROUP") && sort.equals("period"))
      throw new IllegalArgumentException("分组查询不能按趋势时间排序");
    String direction = node.path("direction").asText("desc");
    if (!Set.of("asc", "desc").contains(direction)) throw new IllegalArgumentException("排序方向无效");
    String time = node.path("timeField").asText(spec.timeField());
    if (!time.isEmpty()
        && (!spec.fields().containsKey(time)
            || !Set.of("DATE", "DATETIME").contains(spec.fields().get(time).type())))
      throw new IllegalArgumentException("业务时间字段无效");
    String period = node.path("period").asText("all");
    if (!Set.of(
            "all",
            "today",
            "yesterday",
            "week",
            "lastWeek",
            "month",
            "lastMonth",
            "year",
            "lastYear",
            "last7",
            "last30",
            "last90",
            "custom")
        .contains(period)) throw new IllegalArgumentException("统计周期无效");
    String from = node.path("from").asText(""), to = node.path("to").asText("");
    if (period.equals("custom")) {
      var a = LocalDate.parse(from);
      var b = LocalDate.parse(to);
      if (a.isAfter(b)
          || a.isBefore(LocalDate.of(1900, 1, 1))
          || b.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusYears(10)))
        throw new IllegalArgumentException("日期范围无效");
    }
    if (!period.equals("all") && time.isEmpty())
      throw new IllegalArgumentException("该业务没有可用的统计时间字段");
    String bucket = node.path("bucket").asText("month");
    if (bucket.isBlank() && !operation.equals("TREND")) bucket = "month";
    if (!Set.of("day", "month", "year").contains(bucket))
      throw new IllegalArgumentException("趋势粒度无效");
    if (operation.equals("TREND") && time.isEmpty())
      throw new IllegalArgumentException("趋势需要明确时间字段");
    if (operation.equals("GROUP") && groups.isEmpty())
      throw new IllegalArgumentException("请明确分组维度");
    var limit = node.path("topN");
    int top = limit.isMissingNode() ? 0 : limit.asInt(-1);
    if (!limit.isMissingNode() && !limit.isIntegralNumber()
        || top < 0
        || top > 100
        || operation.equals("COUNT") && top != 0)
      throw new IllegalArgumentException("排行数量须为1至100，计数不使用排行限制");
    var where = node.path("where");
    validateFilter(where, spec, 0, new int[] {0});
    return new AiBusinessPlan(
        domain,
        operation,
        where.deepCopy(),
        List.copyOf(groups),
        measure,
        aggregate,
        sort,
        direction,
        time,
        period,
        from,
        to,
        bucket,
        top);
  }

  static void validateFilter(JsonNode node, AiBusinessCatalog.Domain spec, int depth, int[] count) {
    if (node.isMissingNode() || node.isNull()) return;
    if (!node.isObject() || depth > 4 || ++count[0] > 24)
      throw new IllegalArgumentException("筛选条件过多或嵌套过深");
    String op = node.path("op").asText();
    if (Set.of("AND", "OR", "NOT").contains(op)) {
      var children = node.path("children");
      if (!children.isArray()
          || children.isEmpty()
          || children.size() > 12
          || op.equals("NOT") && children.size() != 1) throw new IllegalArgumentException("逻辑筛选无效");
      node.fieldNames()
          .forEachRemaining(
              k -> {
                if (!Set.of("op", "children").contains(k))
                  throw new IllegalArgumentException("逻辑筛选包含未知参数");
              });
      for (var child : children) {
        if (child.isNull()) throw new IllegalArgumentException("逻辑条件不能为空");
        validateFilter(child, spec, depth + 1, count);
      }
      return;
    }
    node.fieldNames()
        .forEachRemaining(
            k -> {
              if (!Set.of("field", "op", "values").contains(k))
                throw new IllegalArgumentException("筛选包含未知参数");
            });
    var field = spec.fields().get(node.path("field").asText());
    var values = node.path("values");
    if (field == null
        || field.type().equals("DISPLAY")
        || !Set.of("eq", "ne", "contains", "in", "gte", "lte", "gt", "lt", "empty", "notEmpty")
            .contains(op)
        || !values.isArray()
        || values.size() > 20) throw new IllegalArgumentException("筛选字段或操作无效");
    if (Set.of("empty", "notEmpty").contains(op)) {
      if (!values.isEmpty()) throw new IllegalArgumentException("空值筛选不接受额外值");
      return;
    }
    if (values.isEmpty() || !op.equals("in") && values.size() != 1)
      throw new IllegalArgumentException("筛选值数量无效");
    if (op.equals("contains") && !field.type().equals("TEXT"))
      throw new IllegalArgumentException("该字段不能模糊匹配");
    if (Set.of("gt", "gte", "lt", "lte").contains(op)
        && !Set.of("NUMBER", "DATE", "DATETIME").contains(field.type()))
      throw new IllegalArgumentException("该字段不能范围比较");
    for (var value : values)
      if (!value.isTextual() || value.asText().length() > 200)
        throw new IllegalArgumentException("筛选值格式无效");
  }
}
