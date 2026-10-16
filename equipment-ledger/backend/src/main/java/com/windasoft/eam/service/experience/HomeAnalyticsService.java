package com.windasoft.eam.service.experience;

import static com.windasoft.eam.common.experience.ExperienceSupport.*;

import com.windasoft.eam.repository.experience.AnalyticsRepository;
import com.windasoft.eam.service.system.*;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HomeAnalyticsService {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(HomeAnalyticsService.class);
  private final AnalyticsRepository repository;
  private final AnalyticsSourceService sources;
  private final AnalyticsAccess access;
  private final AuthContext auth;
  private final PermissionService permissions;

  public HomeAnalyticsService(
      AnalyticsRepository repository,
      AnalyticsSourceService sources,
      AnalyticsAccess access,
      AuthContext auth,
      PermissionService permissions) {
    this.repository = repository;
    this.sources = sources;
    this.access = access;
    this.auth = auth;
    this.permissions = permissions;
  }

  public static Map<String, Object> map(Object raw) {
    return raw instanceof Map<?, ?> ? (Map<String, Object>) raw : new LinkedHashMap<>();
  }

  public boolean teamAllowed() {
    return permissions.allowed("workspace", "团队工作查看");
  }

  public List<AnalyticsCatalog.Metric> metrics() {
    return AnalyticsCatalog.ALL.stream().filter(access::available).toList();
  }

  public Object presets() {
    catalog();
    var ids = new HashSet<String>();
    metrics().forEach(m -> ids.add("analytics:" + m.id()));
    return Map.of("data", OverviewLayoutSchema.presets(teamAllowed(), ids));
  }

  public Map<String, Object> catalog() {
    if (!permissions.menuActions().getOrDefault("workspace", Set.of()).contains("查看"))
      AnalyticsAccess.deny();
    var companies = new HashSet<Long>();
    var departments = new HashSet<Long>();
    boolean all = false;
    for (String menu :
        List.of(
            "repair",
            "maintenanceTask",
            "inspectionDailyTask",
            "inspectionPeriodicTask",
            "anomaly",
            "equipment")) {
      if (!permissions.menuActions().getOrDefault(menu, Set.of()).contains("查看")) continue;
      var scope = access.scope(menu);
      all |= scope.all();
      companies.addAll(scope.companyIds());
      departments.addAll(scope.departmentIds());
    }
    if (permissions.allowed("inventoryQuery", "查看")) {
      var allowed = access.warehouses(false);
      repository.warehouses(auth.tenantId()).stream()
          .filter(w -> allowed.contains(((Number) w.get("id")).longValue()))
          .forEach(w -> companies.add(((Number) w.get("company_id")).longValue()));
    }
    var companyScopes = new HashSet<>(companies);
    if (auth.user().getOrgId() != null) {
      departments.add(auth.user().getOrgId());
      Long c = permissions.companyOfOrganization(auth.user().getOrgId());
      if (c != null) companies.add(c);
    }
    final boolean any = all;
    var orgs =
        repository.organizations(auth.tenantId()).stream()
            .filter(
                o ->
                    any
                        || companies.contains(((Number) o.get("id")).longValue())
                        || departments.contains(((Number) o.get("id")).longValue())
                        || companyScopes.contains(
                            permissions.companyOfOrganization(((Number) o.get("id")).longValue())))
            .map(
                o -> {
                  var v = new LinkedHashMap<>(o);
                  v.put("id", o.get("id").toString());
                  v.put("parentId", Objects.toString(o.get("parent_id"), ""));
                  return v;
                })
            .toList();
    return Map.of(
        "data",
        metrics().stream().map(this::metricView).toList(),
        "organizations",
        orgs,
        "teamAllowed",
        permissions.allowed("workspace", "团队工作查看"),
        "timezone",
        AnalyticsPeriod.ZONE.toString());
  }

  private Map<String, Object> metricView(AnalyticsCatalog.Metric m) {
    var v = new LinkedHashMap<String, Object>();
    v.put("id", m.id());
    v.put("name", m.name());
    v.put("domain", m.domain());
    v.put("chart", m.chart());
    v.put("period", m.period());
    v.put("unit", m.unit());
    v.put("definition", m.definition());
    v.put("money", m.money());
    v.put(
        "timeField",
        m.id().equals("fault_causes")
            ? "accepted_at"
            : m.id().equals("repair_trend")
                ? "reported_at / accepted_at"
                : m.domain().equals("maintenance")
                    ? "due_date"
                    : m.domain().equals("inspection")
                        ? "due_at"
                        : m.domain().equals("anomaly")
                            ? "discovered_at"
                            : m.domain().equals("material") ? "usage.created_at" : "reported_at");
    v.put(
        "filters",
        m.money()
            ? List.of("companyIds", "departmentIds", "scope", "period", "currency")
            : List.of("companyIds", "departmentIds", "scope", "period"));
    v.put(
        "dimensions",
        switch (m.id()) {
          case "repair_ranking", "anomaly_ranking", "equipment_material_ranking" ->
              List.of("equipment_id");
          case "fault_types" -> List.of("fault_type_id");
          case "fault_causes" -> List.of("fault_cause_id");
          case "personnel" -> List.of("owner_id", "collaborator_id");
          case "maintenance_departments" -> List.of("department_id");
          case "inspection_execution" -> List.of("kind");
          case "material_ranking" -> List.of("material_id");
          case "repair_trend", "material_trend" -> List.of("month");
          default -> List.of("status");
        });
    v.put(
        "exclusions",
        m.domain().equals("repair")
            ? "已删除、已撤销、已取消"
            : m.domain().equals("material")
                ? "已冲销、未使用的出库分配；缺少可靠成本或币种的记录不计入金额"
                : "人工取消不计有效任务；系统过期作废仍计入");
    v.put("qualityNote", "历史缺失事件、分类、成本或归属不补造；按缺失项分别报告覆盖情况");
    v.put("detail", true);
    v.put("exportable", access.exportable(m));
    v.put("teamRequires", "workspace.团队工作查看");
    return v;
  }

  private record Query(
      AnalyticsCatalog.Metric metric,
      AnalyticsPeriod period,
      AnalyticsRepository.Source source,
      String where,
      List<Object> args,
      Map<String, Object> input,
      int topN,
      String currency) {}

  private Query prepare(
      Map<String, Object> widget, Map<String, Object> context, LocalDateTime now) {
    var metric = AnalyticsCatalog.require(text(widget, "metric"));
    String scope = text(widget, "scope");
    if (scope.isBlank()) scope = "personal";
    access.require(metric, scope);
    var period =
        AnalyticsPeriod.resolve(map(widget.get("period")), map(context.get("period")), now);
    int top = (int) id(widget.getOrDefault("topN", 10));
    check(Set.of(5, 10, 20).contains(top), "排名展示数量必须为5、10或20");
    var source =
        sources.source(metric.domain(), scope, metric.id().equals("equipment_material_ranking"));
    var args = new ArrayList<Object>();
    String where = "1=1";
    List<Map<String, Object>> orgs = null;
    for (var range : List.of(context, widget))
      for (String field : List.of("companyIds", "departmentIds")) {
        Object raw = range.get(field);
        var ids = raw instanceof List<?> list ? list : List.of();
        check(ids.size() <= 100, "组织筛选过多");
        if (!ids.isEmpty() && orgs == null)
          orgs = (List<Map<String, Object>>) catalog().get("organizations");
        var selected = new LinkedHashSet<Long>();
        for (Object value : ids) {
          long oid = id(value);
          boolean authorized = false;
          for (var org : orgs)
            if (id(org.get("id")) == oid
                && text(org, "org_type").equals("公司") == field.equals("companyIds"))
              authorized = true;
          if (!authorized) AnalyticsAccess.deny();
          selected.add(oid);
          if (field.equals("departmentIds")) selected.addAll(access.departments(oid));
        }
        if (!selected.isEmpty())
          where +=
              " AND "
                  + AnalyticsAccess.in(
                      field.equals("companyIds") ? "s.company_id" : "s.department_id",
                      selected,
                      args);
      }
    String currency = text(widget, "currency");
    check(currency.isBlank() || currency.matches("[A-Z]{3}"), "币种无效");
    return new Query(metric, period, source, where, args, widget, top, currency);
  }

  private String timeField(Query q, Map<String, Object> filter) {
    if (q.metric.id().equals("fault_causes")
        || q.metric.id().equals("repair_trend") && text(filter, "series").equals("completed"))
      return "s.accepted_at";
    return "s.occurred_at";
  }

  private String periodWhere(Query q, List<Object> args, Map<String, Object> filter) {
    String field = timeField(q, filter);
    args.add(q.period.start());
    args.add(q.period.end());
    String where = q.where + " AND " + field + ">=? AND " + field + "<?";
    if (!Set.of("maintenance", "inspection").contains(q.metric.domain())) {
      where += " AND " + field + "<=?";
      args.add(q.period.asOf());
    }
    if (field.equals("s.accepted_at")) where += " AND s.status='已完成'";
    return where;
  }

  private static long count(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value instanceof Number n ? n.longValue() : 0;
  }

  private static BigDecimal amount(Object value) {
    return value instanceof BigDecimal d
        ? d
        : value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }

  private static Map<String, Object> one(List<Map<String, Object>> rows) {
    return rows.isEmpty() ? new LinkedHashMap<>() : rows.getFirst();
  }

  private Map<String, Object> result(Query q) {
    var out = new LinkedHashMap<String, Object>();
    out.put("id", text(q.input, "id"));
    out.put("metric", q.metric.id());
    out.put("definition", q.metric.definition());
    out.put("unit", q.metric.unit());
    out.put("exportable", access.exportable(q.metric));
    out.put("asOf", q.period.asOf().toString());
    out.put(
        "period",
        Map.of(
            "from",
            q.period.from().toString(),
            "to",
            q.period.to().toString(),
            "label",
            q.period.label(),
            "preset",
            q.period.preset()));
    var args = new ArrayList<>(q.args);
    String where = periodWhere(q, args, Map.of());
    var quality = new LinkedHashMap<String, Object>();
    var summary = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> rows;
    switch (q.metric.id()) {
      case "repair_ranking", "anomaly_ranking" -> {
        rows =
            repository.rows(
                q.source,
                "CAST(s.equipment_id AS CHAR) `key`,MAX(s.equipment_name)"
                    + " name,MAX(s.equipment_code) code,COUNT(*) value",
                where + " AND s.equipment_id IS NOT NULL",
                args,
                "GROUP BY s.equipment_id ORDER BY value DESC,code,s.equipment_id LIMIT " + q.topN);
        quality.putAll(
            one(
                repository.rows(
                    q.source,
                    "COUNT(*) total,SUM(s.equipment_id IS NULL) missingEquipment",
                    where,
                    args,
                    "")));
      }
      case "fault_types", "fault_causes" -> {
        boolean cause = q.metric.id().equals("fault_causes");
        String id = cause ? "fault_cause_id" : "fault_type_id",
            label = cause ? "fault_cause" : "fault_type";
        String key =
            "COALESCE(CAST(s."
                + id
                + " AS CHAR),IF(COALESCE(s."
                + label
                + ",'')='','__empty__','__unclassified__'))";
        String name =
            "CASE WHEN s."
                + id
                + " IS NOT NULL THEN s."
                + label
                + " WHEN COALESCE(s."
                + label
                + ",'')='' THEN '"
                + (cause ? "未填写原因" : "未填写类型")
                + "' ELSE '未归类（历史文本）' END";
        var all =
            repository.rows(
                q.source,
                key + " `key`,MAX(" + name + ") name,COUNT(*) value",
                where,
                args,
                "GROUP BY " + key + " ORDER BY value DESC,`key`");
        rows = new ArrayList<>(all.subList(0, Math.min(q.topN, all.size())));
        if (all.size() > q.topN)
          rows.add(
              new LinkedHashMap<>(
                  Map.of(
                      "key",
                      "__other__",
                      "name",
                      "其他",
                      "value",
                      all.subList(q.topN, all.size()).stream()
                          .mapToLong(r -> count(r, "value"))
                          .sum())));
        long total = all.stream().mapToLong(r -> count(r, "value")).sum();
        summary.put("total", total);
        for (var r : rows)
          r.put(
              "percentage",
              total == 0
                  ? null
                  : BigDecimal.valueOf(count(r, "value") * 100)
                      .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
        quality.put(
            "unclassified",
            all.stream()
                .filter(r -> text(r, "key").startsWith("__"))
                .mapToLong(r -> count(r, "value"))
                .sum());
        quality.putAll(repairQuality(q));
      }
      case "repair_trend" -> {
        var months = monthRows(q.period);
        Map<String, Map<String, Object>> by = new LinkedHashMap<>();
        for (var r : months) by.put(text(r, "key"), r);
        for (String series : List.of("reported", "completed")) {
          var p = new ArrayList<>(q.args);
          String w = periodWhere(q, p, Map.of("series", series));
          String date = series.equals("completed") ? "s.accepted_at" : "s.occurred_at";
          var values =
              repository.rows(
                  q.source,
                  "DATE_FORMAT(" + date + ",'%Y-%m') `key`,COUNT(*) value",
                  w,
                  p,
                  "GROUP BY `key` ORDER BY `key`");
          for (var v : values)
            if (by.containsKey(text(v, "key"))) by.get(text(v, "key")).put(series, v.get("value"));
        }
        rows = months;
        quality.putAll(repairQuality(q));
      }
      case "personnel" -> {
        rows = personnel(q);
        summary.put("unfinishedAsOf", q.period.asOf().toString());
        quality.putAll(repairQuality(q));
      }
      case "maintenance_execution", "maintenance_departments", "inspection_execution" -> {
        var aggregate =
            execution(
                q,
                where,
                args,
                q.metric.id().equals("maintenance_departments")
                    ? "department"
                    : q.metric.domain().equals("inspection") ? "kind" : "");
        if (q.metric.id().equals("maintenance_departments")
            || q.metric.domain().equals("inspection")) {
          rows = aggregate;
          for (var row : rows) executionRates(row);
          summary.putAll(one(execution(q, where, args, "")));
        } else {
          summary.putAll(one(aggregate));
          rows = executionRows(summary);
        }
        executionRates(summary);
        quality.put("missingSubmission", summary.getOrDefault("missingSubmission", 0));
        quality.put("missingDeadline", summary.getOrDefault("missingDeadline", 0));
        quality.put("missingAcceptanceTime", summary.getOrDefault("missingAcceptanceTime", 0));
      }
      case "material_trend", "material_ranking", "equipment_material_ranking" -> {
        var currencies =
            repository
                .rows(
                    q.source,
                    "DISTINCT s.currency code",
                    where + " AND s.currency IS NOT NULL",
                    args,
                    "ORDER BY code")
                .stream()
                .map(r -> text(r, "code"))
                .toList();
        String currency =
            q.currency.isBlank()
                ? (currencies.contains("CNY")
                    ? "CNY"
                    : currencies.isEmpty() ? "" : currencies.getFirst())
                : q.currency;
        out.put("currencies", currencies);
        out.put("currency", currency);
        quality.putAll(
            one(
                repository.rows(
                    q.source,
                    "SUM(s.amount IS NULL) missingCost,SUM(s.currency IS NULL)"
                        + " missingCurrency,COUNT(*) sourceRecords",
                    where,
                    args,
                    "")));
        var p = new ArrayList<>(args);
        p.add(currency);
        String w = where + " AND s.currency=? AND s.amount IS NOT NULL";
        summary.putAll(
            one(
                repository.rows(
                    q.source, "COALESCE(SUM(s.amount),0) totalAmount,COUNT(*) records", w, p, "")));
        if (q.metric.id().equals("material_trend")) {
          rows = monthRows(q.period);
          var values =
              repository.rows(
                  q.source,
                  "DATE_FORMAT(s.occurred_at,'%Y-%m') `key`,SUM(s.amount) value",
                  w,
                  p,
                  "GROUP BY `key` ORDER BY `key`");
          for (var row : rows) {
            row.put("value", BigDecimal.ZERO);
            values.stream()
                .filter(v -> text(v, "key").equals(text(row, "key")))
                .findFirst()
                .ifPresent(v -> row.put("value", v.get("value")));
          }
        } else if (q.metric.id().equals("material_ranking"))
          rows =
              repository.rows(
                  q.source,
                  "CAST(s.material_id AS CHAR) `key`,MAX(s.material_code) code,MAX(s.material_name)"
                      + " name,SUM(s.amount) value",
                  w,
                  p,
                  "GROUP BY s.material_id ORDER BY value DESC,code,s.material_id LIMIT " + q.topN);
        else {
          rows =
              repository.rows(
                  q.source,
                  "CAST(s.equipment_id AS CHAR) `key`,MAX(s.equipment_name)"
                      + " name,MAX(s.equipment_code) code,SUM(s.amount) value",
                  w + " AND s.equipment_id IS NOT NULL AND s.equipment_name IS NOT NULL",
                  p,
                  "GROUP BY s.equipment_id ORDER BY value DESC,code,s.equipment_id LIMIT "
                      + q.topN);
          summary.put(
              "shared",
              one(
                  repository.rows(
                      q.source,
                      "COALESCE(SUM(s.amount),0) amount,COUNT(*) records",
                      w + " AND s.attribution='shared'",
                      p,
                      "")));
          summary.put(
              "unassigned",
              one(
                  repository.rows(
                      q.source,
                      "COALESCE(SUM(s.amount),0) amount,COUNT(*) records",
                      w
                          + " AND s.attribution<>'shared' AND (s.equipment_id IS NULL OR"
                          + " s.equipment_name IS NULL)",
                      p,
                      "")));
        }
      }
      default -> throw new IllegalArgumentException("指标未实现");
    }
    out.put("rows", rows);
    out.put("summary", summary);
    out.put("quality", quality);
    boolean noFacts = rows.isEmpty();
    if (q.metric.id().equals("repair_trend"))
      noFacts =
          rows.stream().allMatch(r -> count(r, "reported") == 0 && count(r, "completed") == 0);
    if (q.metric.id().equals("material_trend")) noFacts = count(summary, "records") == 0;
    if (Set.of("maintenance", "inspection").contains(q.metric.domain()))
      noFacts = count(summary, "generated") == 0;
    boolean missing =
        noFacts
            && quality.entrySet().stream()
                .anyMatch(
                    e ->
                        e.getKey().startsWith("missing")
                            && e.getValue() instanceof Number n
                            && n.longValue() > 0);
    out.put("status", noFacts ? (missing ? "data_missing" : "empty") : "ok");
    return out;
  }

  private Map<String, Object> repairQuality(Query q) {
    return one(
        repository.rows(
            q.source,
            "SUM(s.status='已完成' AND s.accepted_at IS NULL) missingAcceptanceTime",
            q.where,
            q.args,
            ""));
  }

  private List<Map<String, Object>> monthRows(AnalyticsPeriod p) {
    var rows = new ArrayList<Map<String, Object>>();
    LocalDate current = p.from().withDayOfMonth(1),
        last = p.to().isAfter(p.asOf().toLocalDate()) ? p.asOf().toLocalDate() : p.to();
    while (!current.isAfter(last)) {
      String key = current.format(DateTimeFormatter.ofPattern("yyyy-MM"));
      rows.add(new LinkedHashMap<>(Map.of("key", key, "name", key, "reported", 0, "completed", 0)));
      current = current.plusMonths(1);
    }
    return rows;
  }

  private String effectiveSubmit(Query q) {
    return "s.submitted_at IS NOT NULL AND s.submitted_at<='" + q.period.asOf() + "'";
  }

  private String deadline(Query q) {
    return "s.deadline IS NOT NULL AND s.deadline<='" + q.period.asOf() + "'";
  }

  private List<Map<String, Object>> execution(
      Query q, String where, List<Object> args, String group) {
    String valid = "s.status NOT IN ('已作废','已取消')",
        done = "s.status IN ('已完成','已审核')",
        submit = effectiveSubmit(q),
        due = deadline(q),
        ontime = "(" + submit + " AND s.deadline IS NOT NULL AND s.submitted_at<=s.deadline)";
    String prefix =
        group.equals("department")
            ? "COALESCE(CAST(s.department_id AS CHAR),'__unknown__') `key`,MAX(s.department_name)"
                + " name,"
            : group.equals("kind") ? "s.kind `key`,IF(s.kind='daily','日常点检','周期巡检') name," : "";
    String select =
        prefix
            + "COUNT(*) `generated`,COALESCE(SUM("
            + valid
            + "),0) total,COALESCE(SUM(NOT("
            + valid
            + ")),0) cancelled,COALESCE(SUM("
            + valid
            + " AND "
            + done
            + "),0) completed,COALESCE(SUM("
            + valid
            + " AND s.status IN ('待验收','待审核')),0) pending,"
            + "COALESCE(SUM("
            + valid
            + " AND NOT("
            + done
            + ") AND ("
            + due
            + " OR s.status='过期作废')),0) overdue,"
            + "COALESCE(SUM("
            + valid
            + " AND s.deadline IS NOT NULL AND (NOT("
            + done
            + ") OR "
            + submit
            + ") AND ("
            + due
            + " OR "
            + submit
            + " OR s.status='过期作废')),0) evaluable,"
            + "COALESCE(SUM("
            + valid
            + " AND "
            + ontime
            + "),0) onTime,"
            + "COALESCE(SUM("
            + valid
            + " AND "
            + done
            + " AND "
            + ontime
            + "),0) completedOnTime,"
            + "COALESCE(SUM("
            + valid
            + " AND "
            + done
            + " AND "
            + submit
            + " AND s.deadline IS NOT NULL AND s.submitted_at>s.deadline),0) completedLate,"
            + "COALESCE(SUM("
            + valid
            + " AND "
            + done
            + " AND (s.submitted_at IS NULL OR s.deadline IS NULL)),0) missingSubmission,"
            + "COALESCE(SUM("
            + valid
            + " AND "
            + done
            + " AND s.accepted_at IS NULL),0) missingAcceptanceTime,"
            + "COALESCE(SUM("
            + valid
            + " AND NOT("
            + done
            + ") AND s.deadline>'"
            + q.period.asOf()
            + "' AND s.status<>'过期作废'),0) upcoming,"
            + "COALESCE(SUM("
            + valid
            + " AND NOT("
            + done
            + ") AND s.deadline IS NULL AND s.status<>'过期作废'),0) unknownDeadlineUnfinished,"
            + "COALESCE(SUM("
            + valid
            + " AND s.deadline IS NULL),0) missingDeadline";
    return repository.rows(
        q.source, select, where, args, group.isBlank() ? "" : "GROUP BY `key` ORDER BY `key`");
  }

  private void executionRates(Map<String, Object> row) {
    long total = count(row, "total"), evaluable = count(row, "evaluable");
    row.put(
        "completionRate",
        total == 0
            ? null
            : BigDecimal.valueOf(count(row, "completed") * 100)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
    row.put(
        "onTimeRate",
        evaluable == 0
            ? null
            : BigDecimal.valueOf(count(row, "onTime") * 100)
                .divide(BigDecimal.valueOf(evaluable), 2, RoundingMode.HALF_UP));
  }

  private List<Map<String, Object>> executionRows(Map<String, Object> s) {
    long total = count(s, "total"),
        completed = count(s, "completed"),
        pending = count(s, "pending"),
        cancelled = count(s, "cancelled");
    return List.of(
        new LinkedHashMap<>(Map.of("key", "completed", "name", "验收完成", "value", completed)),
        new LinkedHashMap<>(Map.of("key", "pending", "name", "待验收 / 待审核", "value", pending)),
        new LinkedHashMap<>(
            Map.of(
                "key",
                "unfinished",
                "name",
                "其余未完成",
                "value",
                Math.max(0, total - completed - pending))),
        new LinkedHashMap<>(Map.of("key", "cancelled", "name", "人工作废", "value", cancelled)));
  }

  private List<Map<String, Object>> personnel(Query q) {
    var p = new ArrayList<>(q.args);
    p.add(q.period.start());
    p.add(q.period.end());
    p.add(q.period.asOf());
    String inPeriod =
        "s.status='已完成' AND s.accepted_at>=? AND s.accepted_at<? AND s.accepted_at<=?";
    String query =
        """
, credits AS (
  SELECT s.owner_id pid,MAX(s.owner_name) name,
  SUM(CASE WHEN %s THEN 1 ELSE 0 END) completed,
  SUM(s.status NOT IN ('已完成','已撤销','已取消')) unfinished,
  SUM(CASE WHEN %s THEN COALESCE(s.score,0) ELSE 0 END) scoreTotal,
  SUM(CASE WHEN %s AND s.score IS NOT NULL THEN 1 ELSE 0 END) scoreCount,0 collaboration
  FROM base s WHERE %s AND s.owner_id IS NOT NULL GROUP BY s.owner_id
  UNION ALL
  SELECT c.uid pid,MAX(c.name) name,0,0,0,0,COUNT(DISTINCT s.source_id) collaboration FROM base s
  JOIN JSON_TABLE(COALESCE(s.collaborators,JSON_ARRAY()),'$[*]' COLUMNS(uid BIGINT PATH '$.id',name VARCHAR(100) PATH '$.name')) c
  WHERE %s AND %s AND c.uid<>COALESCE(s.owner_id,0) GROUP BY c.uid
) SELECT CAST(pid AS CHAR) `key`,MAX(name) name,SUM(completed) completed,SUM(unfinished) unfinished,
SUM(scoreCount) scoreCount,IF(SUM(scoreCount)=0,NULL,ROUND(SUM(scoreTotal)/SUM(scoreCount),2)) score,
SUM(collaboration) collaboration FROM credits %s GROUP BY pid HAVING SUM(completed)+SUM(unfinished)+SUM(collaboration)>0 ORDER BY completed DESC,pid
""";
    // Source predicates have placeholders twice in the two credit branches; bind in SQL order.
    p = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      p.add(q.period.start());
      p.add(q.period.end());
      p.add(q.period.asOf());
    }
    p.addAll(q.args);
    p.addAll(q.args);
    p.add(q.period.start());
    p.add(q.period.end());
    p.add(q.period.asOf());
    boolean personal = !text(q.input, "scope").equals("team");
    if (personal) p.add(auth.userId());
    query =
        query.formatted(
            inPeriod,
            inPeriod,
            inPeriod,
            q.where,
            q.where,
            inPeriod,
            personal ? "WHERE pid=?" : "");
    return repository.cte(q.source, query, p);
  }

  @Transactional(readOnly = true)
  public Object query(Map<String, Object> body) {
    var widgets = rows(body.get("widgets"));
    check(!widgets.isEmpty() && widgets.size() <= 12, "每批查询1至12个区块");
    var context = map(body.get("context"));
    var now = asOf(context);
    var result = new ArrayList<Map<String, Object>>();
    var ids = new HashSet<String>();
    for (var widget : widgets) {
      String wid = required(widget, "id", 64);
      check(ids.add(wid), "区块标识重复");
      try {
        result.add(result(prepare(widget, context, now)));
      } catch (ResponseStatusException e) {
        if (e.getStatusCode().value() != 403) throw e;
        result.add(
            new LinkedHashMap<>(
                Map.of(
                    "id",
                    wid,
                    "metric",
                    text(widget, "metric"),
                    "status",
                    "forbidden",
                    "message",
                    "没有该统计或数据范围的权限")));
      } catch (org.springframework.dao.DataAccessException e) {
        LOG.error("Analytics query failed for {}", text(widget, "metric"), e);
        result.add(
            new LinkedHashMap<>(
                Map.of(
                    "id",
                    wid,
                    "metric",
                    text(widget, "metric"),
                    "status",
                    "error",
                    "message",
                    "统计加载失败，请重试")));
      }
    }
    return Map.of("data", result, "asOf", now.toString());
  }

  private LocalDateTime asOf(Map<String, Object> context) {
    var now = LocalDateTime.now(AnalyticsPeriod.ZONE);
    String supplied = text(context, "asOf");
    if (supplied.isBlank()) return now;
    try {
      var value = LocalDateTime.parse(supplied);
      check(!value.isAfter(now), "统计截至时间不能晚于当前时间");
      return value;
    } catch (java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("统计截至时间无效");
    }
  }

  private record Detail(Query query, String where, List<Object> args, Map<String, Object> filter) {}

  private Detail detail(Map<String, Object> body) {
    var context = map(body.get("context"));
    var q = prepare(map(body.get("widget")), context, asOf(context));
    var f = map(body.get("filter"));
    var p = new ArrayList<>(q.args);
    String w = periodWhere(q, p, f);
    String group = text(f, "key");
    if (!group.isBlank()) {
      if (q.metric.id().equals("repair_trend") || q.metric.id().equals("material_trend")) {
        check(group.matches("[0-9]{4}-[0-9]{2}"), "月份无效");
        w += " AND DATE_FORMAT(" + timeField(q, f) + ",'%Y-%m')=?";
        p.add(group);
      } else if (Set.of(
              "repair_ranking", "anomaly_ranking", "equipment_material_ranking", "material_ranking")
          .contains(q.metric.id())) {
        if (group.equals("__shared__") && q.metric.id().equals("equipment_material_ranking"))
          w += " AND s.attribution='shared'";
        else if (group.equals("__unassigned__")
            && q.metric.id().equals("equipment_material_ranking"))
          w +=
              " AND s.attribution<>'shared' AND (s.equipment_id IS NULL OR s.equipment_name IS"
                  + " NULL)";
        else {
          long value = id(group);
          w +=
              " AND s."
                  + (q.metric.id().equals("material_ranking") ? "material_id" : "equipment_id")
                  + "=?";
          p.add(value);
        }
      } else if (Set.of("fault_types", "fault_causes").contains(q.metric.id())) {
        String id = q.metric.id().equals("fault_types") ? "fault_type_id" : "fault_cause_id",
            label = q.metric.id().equals("fault_types") ? "fault_type" : "fault_cause";
        if (group.equals("__empty__"))
          w += " AND s." + id + " IS NULL AND COALESCE(s." + label + ",'')=''";
        else if (group.equals("__unclassified__"))
          w += " AND s." + id + " IS NULL AND COALESCE(s." + label + ",'')<>''";
        else if (group.equals("__other__")) {
          var top =
              repository.rows(
                  q.source,
                  "COALESCE(CAST(s."
                      + id
                      + " AS CHAR),IF(COALESCE(s."
                      + label
                      + ",'')='','__empty__','__unclassified__')) `key`,COUNT(*) n",
                  w,
                  p,
                  "GROUP BY `key` ORDER BY n DESC,`key` LIMIT " + q.topN);
          for (var row : top) {
            w +=
                " AND COALESCE(CAST(s."
                    + id
                    + " AS CHAR),IF(COALESCE(s."
                    + label
                    + ",'')='','__empty__','__unclassified__'))<>?";
            p.add(row.get("key"));
          }
        } else {
          w += " AND s." + id + "=?";
          p.add(id(group));
        }
      } else if (q.metric.id().equals("personnel")) {
        long person = id(group);
        String series = text(f, "series");
        if (series.equals("unfinished")) {
          p = new ArrayList<>(q.args);
          w = q.where + " AND s.status NOT IN ('已完成','已撤销','已取消')";
        } else {
          p = new ArrayList<>(q.args);
          p.add(q.period.start());
          p.add(q.period.end());
          p.add(q.period.asOf());
          w =
              q.where
                  + " AND s.status='已完成' AND s.accepted_at>=? AND s.accepted_at<? AND"
                  + " s.accepted_at<=?";
        }
        if (series.equals("collaboration")) {
          w +=
              " AND EXISTS(SELECT 1 FROM JSON_TABLE(COALESCE(s.collaborators,JSON_ARRAY()),'$[*]'"
                  + " COLUMNS(uid BIGINT PATH '$.id')) co WHERE co.uid=?) AND"
                  + " COALESCE(s.owner_id,0)<>?";
          p.add(Long.toString(person));
          p.add(person);
        } else {
          w += " AND s.owner_id=?";
          p.add(person);
          if (series.equals("score")) w += " AND s.score IS NOT NULL";
        }
        if (!text(q.input, "scope").equals("team") && person != auth.userId())
          AnalyticsAccess.deny();
      } else if (q.metric.id().equals("maintenance_departments")) {
        w +=
            " AND "
                + (group.equals("__unknown__") ? "s.department_id IS NULL" : "s.department_id=?");
        if (!group.equals("__unknown__")) p.add(id(group));
      } else if (q.metric.id().equals("inspection_execution")
          && Set.of("daily", "periodic").contains(group)) {
        w += " AND s.kind=?";
        p.add(group);
      } else if (Set.of("maintenance_execution", "inspection_execution").contains(q.metric.id())) {
        w +=
            " AND "
                + switch (group) {
                  case "completed" -> "s.status IN ('已完成','已审核')";
                  case "pending" -> "s.status IN ('待验收','待审核')";
                  case "unfinished" -> "s.status NOT IN ('已完成','已审核','待验收','待审核','已作废','已取消')";
                  case "cancelled" -> "s.status IN ('已作废','已取消')";
                  default -> throw new IllegalArgumentException("分组无效");
                };
      }
    }
    if (Set.of("maintenance", "inspection").contains(q.metric.domain())) {
      String series = text(f, "series"),
          valid = "s.status NOT IN ('已作废','已取消')",
          done = "s.status IN ('已完成','已审核')",
          submitted = effectiveSubmit(q),
          due = deadline(q);
      w +=
          " AND "
              + switch (series) {
                case "total" -> valid;
                case "completed" -> valid + " AND " + done;
                case "pending" -> valid + " AND s.status IN ('待验收','待审核')";
                case "cancelled" -> "s.status IN ('已作废','已取消')";
                case "overdue" ->
                    valid + " AND NOT(" + done + ") AND (" + due + " OR s.status='过期作废')";
                case "upcoming" ->
                    valid
                        + " AND NOT("
                        + done
                        + ") AND s.deadline IS NOT NULL AND NOT("
                        + due
                        + ") AND s.status<>'过期作废'";
                case "onTime" ->
                    valid
                        + " AND "
                        + submitted
                        + " AND s.deadline IS NOT NULL AND s.submitted_at<=s.deadline";
                case "evaluable" ->
                    valid
                        + " AND s.deadline IS NOT NULL AND (NOT("
                        + done
                        + ") OR "
                        + submitted
                        + ") AND ("
                        + submitted
                        + " OR "
                        + due
                        + " OR s.status='过期作废')";
                case "" -> "1=1";
                default -> throw new IllegalArgumentException("明细维度无效");
              };
    }
    if (q.metric.money()) {
      var snapshot = result(q);
      String c = text(snapshot, "currency");
      w += " AND s.currency=? AND s.amount IS NOT NULL";
      p.add(c);
    }
    return new Detail(q, w, p, f);
  }

  private Map<String, Object> detailView(Map<String, Object> row) {
    var value = new LinkedHashMap<>(row);
    value.replaceAll((key, v) -> key.endsWith("_id") && v != null ? v.toString() : v);
    return value;
  }

  @Transactional(readOnly = true)
  public Object details(Map<String, Object> body) {
    Detail d = detail(body);
    int page = (int) id(body.getOrDefault("page", 1)),
        size = (int) id(body.getOrDefault("size", 20));
    check(page > 0 && size > 0 && size <= 100, "分页参数无效");
    var total = one(repository.rows(d.query.source, "COUNT(*) total", d.where, d.args, ""));
    var data =
        repository.rows(
            d.query.source,
            "s.*",
            d.where,
            d.args,
            "ORDER BY s.occurred_at DESC,s.source_id LIMIT "
                + size
                + " OFFSET "
                + ((long) (page - 1) * size));
    return Map.of(
        "data",
        data.stream().map(this::detailView).toList(),
        "total",
        total.getOrDefault("total", 0),
        "asOf",
        d.query.period.asOf().toString(),
        "definition",
        d.query.metric.definition(),
        "exportable",
        access.exportable(d.query.metric));
  }

  @Transactional(readOnly = true)
  public byte[] export(Map<String, Object> body) {
    Detail d = detail(body);
    if (!access.exportable(d.query.metric)) AnalyticsAccess.deny();
    long total =
        count(one(repository.rows(d.query.source, "COUNT(*) total", d.where, d.args, "")), "total");
    check(total <= 50000, "单次导出最多50000条，请缩小统计范围");
    var data =
        repository.rows(
            d.query.source, "s.*", d.where, d.args, "ORDER BY s.occurred_at DESC,s.source_id");
    var columns =
        d.query.metric.money()
            ? List.of(
                "code",
                "equipment_name",
                "material_code",
                "material_name",
                "quantity",
                "unit",
                "amount",
                "currency",
                "attribution",
                "occurred_at",
                "allocation_id",
                "cost_layer_id",
                "receipt_line_id")
            : List.of(
                "code",
                "equipment_name",
                "department_name",
                "owner_name",
                "status",
                "occurred_at",
                "submitted_at",
                "accepted_at");
    var csv = new StringBuilder("\ufeff").append("编号,设备,部门,负责人,状态,计划或报修时间,提交时间,验收时间");
    if (d.query.metric.money())
      csv = new StringBuilder("\ufeff出库单号,设备,料号,物料,数量,单位,金额,币种,归属,使用时间,分配ID,成本层ID,入库明细ID");
    csv.append('\n');
    for (var row : data) {
      csv.append(String.join(",", columns.stream().map(k -> csvCell(row.get(k))).toList()))
          .append('\n');
    }
    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  private static String csvCell(Object value) {
    String v = Objects.toString(value, "");
    if (v.matches("(?s)^[\\s]*[=+@-].*")) v = "'" + v;
    return "\"" + v.replace("\"", "\"\"") + "\"";
  }
}
