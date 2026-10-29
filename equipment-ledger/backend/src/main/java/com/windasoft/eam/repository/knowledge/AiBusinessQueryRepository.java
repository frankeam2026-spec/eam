package com.windasoft.eam.repository.knowledge;

import com.windasoft.eam.dto.knowledge.AiBusinessCatalog;
import com.windasoft.eam.dto.knowledge.AiBusinessPlan;
import com.windasoft.eam.repository.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AiBusinessQueryRepository {
  private final JdbcTemplate db;

  public AiBusinessQueryRepository(JdbcTemplate db) {
    this.db = db;
  }

  public Map<String, Object> company(long tenant, long id) {
    var rows =
        db.queryForList(
            "SELECT CAST(id AS CHAR) id,name FROM eam_org_unit WHERE tenant_id=? AND id=? AND"
                + " deleted=0",
            tenant,
            id);
    return rows.isEmpty() ? Map.of() : rows.getFirst();
  }

  public SqlPredicate equipmentFilter(AiBusinessPlan plan, long tenant, long equipment) {
    var domain = AiBusinessCatalog.ALL.get(plan.domain());
    if (plan.domain().equals("equipment"))
      return SqlPredicate.of("s.id=?", Long.toString(equipment));
    if (domain.fields().containsKey("equipmentId"))
      return SqlPredicate.of("s.equipmentId=?", Long.toString(equipment));
    if (plan.domain().endsWith("Plan")) {
      String relation =
          plan.domain().equals("maintenancePlan")
              ? "eam_maintenance_plan_device"
              : plan.domain().equals("dailyPlan")
                  ? "eam_inspection_daily_plan_device"
                  : "eam_inspection_periodic_plan_device";
      return SqlPredicate.of(
          "EXISTS(SELECT 1 FROM "
              + relation
              + " pd WHERE pd.tenant_id=? AND pd.plan_id=s.id AND pd.equipment_id=?)",
          tenant,
          equipment);
    }
    throw new IllegalArgumentException("当前查询不能按所选设备归属，请清除设备选择或明确查询设备实际用料");
  }

  public SqlPredicate timeFilter(String field, java.time.LocalDate start, java.time.LocalDate end) {
    return SqlPredicate.of(
        "s.`" + field + "`>=? AND s.`" + field + "`<?",
        start.atStartOfDay(),
        end.plusDays(1).atStartOfDay());
  }

  public Map<String, Object> execute(
      AssistantReadSource source, SqlPredicate filter, AiBusinessPlan plan, int page, int size) {
    var domain = AiBusinessCatalog.ALL.get(plan.domain());
    String from = " FROM (" + source.sql() + ") s WHERE " + filter.sql();
    var args = new ArrayList<Object>(source.arguments());
    args.addAll(filter.arguments());
    long records =
        db.queryForObject(
            "SELECT /*+ MAX_EXECUTION_TIME(8000) */ COUNT(*)" + from, Long.class, args.toArray());
    String distinctField = plan.measure();
    if (plan.aggregate().equals("distinct")
        && !domain.fields().get(distinctField).identity().isEmpty())
      distinctField = domain.fields().get(distinctField).identity();
    String measure =
        plan.aggregate().equals("count")
            ? "COUNT(*)"
            : plan.aggregate().equals("distinct")
                ? "COUNT(DISTINCT s.`" + distinctField + "`)"
                : plan.aggregate().toUpperCase(Locale.ROOT) + "(s.`" + plan.measure() + "`)";
    if (plan.operation().equals("COUNT")) {
      Object value =
          db.queryForObject(
              "SELECT /*+ MAX_EXECUTION_TIME(8000) */ " + measure + from,
              Object.class,
              args.toArray());
      var result = new LinkedHashMap<String, Object>();
      result.put("value", value);
      result.put("total", records);
      return result;
    }
    var columns = new ArrayList<String>();
    String select, group = "", order;
    if (plan.operation().equals("LIST")) {
      columns.addAll(domain.columns());
      select = "s.id," + String.join(",", columns.stream().map(k -> "s.`" + k + "`").toList());
      String sort = plan.sortBy().isEmpty() ? domain.timeField() : plan.sortBy();
      if (sort.isEmpty()) sort = "id";
      if (!domain.fields().containsKey(sort)) throw new IllegalArgumentException("明细排序字段无效");
      order = "s.`" + sort + "` " + plan.direction() + ",s.id " + plan.direction();
    } else {
      var groups = new ArrayList<String>();
      var groupedSelect = new ArrayList<String>();
      if (plan.operation().equals("TREND")) {
        String format =
            switch (plan.bucket()) {
              case "day" -> "%Y-%m-%d";
              case "year" -> "%Y";
              default -> "%Y-%m";
            };
        String expression = "DATE_FORMAT(s.`" + plan.timeField() + "`,'" + format + "')";
        groups.add(expression);
        groupedSelect.add(expression + " period");
        columns.add("period");
      }
      for (String field : plan.groupBy()) {
        String expression = "COALESCE(s.`" + field + "`,'')";
        groups.add(expression);
        groupedSelect.add(expression + " `" + field + "`");
        columns.add(field);
        String identity = domain.fields().get(field).identity();
        if (!identity.isEmpty() && !plan.groupBy().contains(identity)) {
          String id = "COALESCE(s.`" + identity + "`,'')";
          groups.add(id);
          groupedSelect.add(id + " `" + identity + "`");
        }
      }
      select = String.join(",", groupedSelect) + ",COUNT(*) count," + measure + " value";
      group = " GROUP BY " + String.join(",", groups);
      columns.add("count");
      if (!plan.aggregate().equals("count")) columns.add("value");
      columns.add("percent");
      String sort =
          plan.sortBy().isEmpty()
              ? (plan.operation().equals("TREND") ? "period" : "value")
              : plan.sortBy();
      if (!Set.of("count", "value", "period").contains(sort) && !plan.groupBy().contains(sort))
        throw new IllegalArgumentException("统计排序必须使用分组或汇总字段");
      order =
          "`"
              + sort
              + "` "
              + (sort.equals("period") && plan.sortBy().isEmpty() ? "asc" : plan.direction());
      for (String field : plan.groupBy()) {
        order += ",`" + field + "` asc";
        var identity = domain.fields().get(field).identity();
        if (!identity.isEmpty()) order += ",`" + identity + "` asc";
      }
    }
    long total =
        plan.operation().equals("LIST")
            ? records
            : db.queryForObject(
                "SELECT /*+ MAX_EXECUTION_TIME(8000) */ COUNT(*) FROM (SELECT 1"
                    + from
                    + group
                    + ") groups_count",
                Long.class,
                args.toArray());
    if (plan.topN() > 0) total = Math.min(total, plan.topN());
    long offset = ((long) page - 1) * size;
    int limit = plan.topN() > 0 ? (int) Math.max(0, Math.min(size, total - offset)) : size;
    args.add(limit);
    args.add(offset);
    var rows =
        db.queryForList(
            "SELECT /*+ MAX_EXECUTION_TIME(8000) */ "
                + select
                + from
                + group
                + " ORDER BY "
                + order
                + " LIMIT ? OFFSET ?",
            args.toArray());
    for (var row : rows) {
      if (!plan.operation().equals("LIST"))
        row.put(
            "percent",
            records == 0
                ? 0
                : java.math.BigDecimal.valueOf(((Number) row.get("count")).longValue())
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .divide(
                        java.math.BigDecimal.valueOf(records), 2, java.math.RoundingMode.HALF_UP));
      for (String key : new ArrayList<>(row.keySet()))
        if (row.get(key) instanceof java.util.Date) row.put(key, row.get(key).toString());
    }
    return Map.of("data", rows, "total", total, "records", records, "columns", columns);
  }

  public boolean ambiguousIdentity(
      AssistantReadSource source,
      SqlPredicate filter,
      String field,
      String identity,
      String value) {
    var args = new ArrayList<Object>(source.arguments());
    args.addAll(filter.arguments());
    args.add(value);
    return db.queryForObject(
            "SELECT /*+ MAX_EXECUTION_TIME(8000) */ COUNT(*) FROM (SELECT DISTINCT s.`"
                + identity
                + "` FROM ("
                + source.sql()
                + ") s WHERE ("
                + filter.sql()
                + ") AND s.`"
                + field
                + "`=? LIMIT 2) choices",
            Long.class,
            args.toArray())
        > 1;
  }
}
