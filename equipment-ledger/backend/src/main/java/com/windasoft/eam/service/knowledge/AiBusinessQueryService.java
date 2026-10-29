package com.windasoft.eam.service.knowledge;

import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.dto.knowledge.*;
import com.windasoft.eam.repository.common.*;
import com.windasoft.eam.repository.knowledge.AiBusinessQueryRepository;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiBusinessQueryService {
  private final AiBusinessQueryRepository repository;
  private final PermissionService permissions;
  private final AuthContext auth;
  private final ObjectMapper json;
  private final com.windasoft.eam.service.equipment.EquipmentQueryService equipment;
  private final com.windasoft.eam.service.repair.RepairQueryService repairs;
  private final com.windasoft.eam.service.maintenance.MaintenanceListService maintenance;
  private final com.windasoft.eam.service.inspection.InspectionListService inspection;
  private final com.windasoft.eam.service.inventory.InventoryQueryService stock;

  public AiBusinessQueryService(
      AiBusinessQueryRepository repository,
      PermissionService permissions,
      AuthContext auth,
      ObjectMapper json,
      com.windasoft.eam.service.equipment.EquipmentQueryService equipment,
      com.windasoft.eam.service.repair.RepairQueryService repairs,
      com.windasoft.eam.service.maintenance.MaintenanceListService maintenance,
      com.windasoft.eam.service.inspection.InspectionListService inspection,
      com.windasoft.eam.service.inventory.InventoryQueryService stock) {
    this.repository = repository;
    this.permissions = permissions;
    this.auth = auth;
    this.json = json;
    this.equipment = equipment;
    this.repairs = repairs;
    this.maintenance = maintenance;
    this.inspection = inspection;
    this.stock = stock;
  }

  public Object catalog() {
    var result = new LinkedHashMap<String, Object>();
    AiBusinessCatalog.ALL.forEach(
        (key, value) -> {
          if (permissions.allowed(value.menu(), "查看")) result.put(key, value);
        });
    return result;
  }

  public Map<String, Object> companyContext() {
    var user = auth.user();
    if (user == null) return Map.of();
    Long company = permissions.companyOfOrganization(user.getOrgId());
    return company == null ? Map.of() : repository.company(auth.tenantId(), company);
  }

  AssistantReadSource source(String domain) {
    return switch (domain) {
      case "equipment" -> equipment.assistantSource();
      case "repair" -> repairs.assistantSource();
      case "maintenanceTask" -> maintenance.assistantSource("tasks");
      case "maintenancePlan" -> maintenance.assistantSource("plans");
      case "dailyTask" -> inspection.assistantSource("daily-tasks");
      case "periodicTask" -> inspection.assistantSource("periodic-tasks");
      case "dailyPlan" -> inspection.assistantSource("daily-plans");
      case "periodicPlan" -> inspection.assistantSource("periodic-plans");
      case "stock" -> stock.assistantSource();
      default -> throw new IllegalArgumentException("业务查询未接入");
    };
  }

  @Transactional(readOnly = true)
  public Map<String, Object> query(JsonNode input, Long equipmentId, int page, int size) {
    permissions.require("knowledge", "查看");
    if (page < 1 || page > 1_000_000 || !Set.of(20, 50, 100).contains(size))
      throw new IllegalArgumentException("查询分页无效");
    var plan = AiBusinessPlan.parse(input);
    var domain = AiBusinessCatalog.ALL.get(plan.domain());
    permissions.require(domain.menu(), "查看");
    var source = source(plan.domain());
    var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0);
    var filter =
        com.windasoft.eam.repository.knowledge.AiBusinessFilters.compile(
            plan.where(), domain, now, auth.userId(),auth.tenantId());
    if (equipmentId != null) {
      equipment.get(equipmentId);
      filter = filter.and(repository.equipmentFilter(plan, auth.tenantId(), equipmentId));
    }
    var bounds = bounds(plan, now.toLocalDate());
    if (bounds != null)
      filter = filter.and(repository.timeFilter(plan.timeField(), bounds[0], bounds[1]));
    checkNames(plan.where(), domain, source, filter);
    var result = new LinkedHashMap<>(repository.execute(source, filter, plan, page, size));
    result.put("conditions", describe(plan.where(), domain));
    result.put(
        "valueLabel",
        plan.aggregate().equals("count")
            ? (plan.domain().equals("stock") ? "余额记录数" : "数量")
            : domain.fields().get(plan.measure()).label()
                + "（"
                + aggregateLabel(plan.aggregate())
                + "）");
    result.put("plan", plan);
    result.put("equipmentId", equipmentId == null ? "" : equipmentId.toString());
    result.put("asOf", now.toString().replace('T', ' '));
    result.put("title", domain.label());
    result.put(
        "timeField",
        plan.timeField().isEmpty() ? "" : domain.fields().get(plan.timeField()).label());
    result.put("range", bounds == null ? "未限定时间，按当前数据查询" : bounds[0] + " ~ " + bounds[1]);
    var identities = new LinkedHashMap<String, String>();
    domain
        .fields()
        .forEach(
            (key, field) -> {
              if (!field.identity().isEmpty()) identities.put(key, field.identity());
            });
    result.put("identities", identities);
    if (result.get("columns") instanceof List<?> columns)
      result.put(
          "columns",
          columns.stream()
              .map(
                  k ->
                      Map.of(
                          "key",
                          k.toString(),
                          "label",
                          k.equals("count")
                              ? (plan.domain().equals("stock") ? "余额记录数" : "数量")
                              : k.equals("percent")
                                  ? "记录占比（%）"
                                  : k.equals("value")
                                      ? domain.fields().get(plan.measure()).label()
                                          + "（"
                                          + aggregateLabel(plan.aggregate())
                                          + "）"
                                      : k.equals("period")
                                          ? "时间"
                                          : domain.fields().get(k.toString()).label()))
              .toList());
    if (plan.operation().equals("LIST"))
      for (var row : (List<Map<String, Object>>) result.get("data"))
        if (!domain.route().isEmpty()) row.put("url", domain.route().formatted(row.get("id")));
    return result;
  }

  private static String aggregateLabel(String value) {
    return switch (value) {
      case "sum" -> "合计";
      case "avg" -> "平均";
      case "min" -> "最小";
      case "max" -> "最大";
      case "distinct" -> "去重数量";
      default -> "数量";
    };
  }

  private static String describe(JsonNode node, AiBusinessCatalog.Domain domain) {
    if (node.isMissingNode() || node.isNull()) return "";
    String op = node.path("op").asText();
    if (node.has("children")) {
      var parts = new ArrayList<String>();
      for (var child : node.path("children")) parts.add(describe(child, domain));
      return (op.equals("NOT") ? "非" : "")
          + "（"
          + String.join(op.equals("OR") ? " 或 " : " 且 ", parts)
          + "）";
    }
    String operator =
        switch (op) {
          case "eq" -> " = ";
          case "ne" -> " ≠ ";
          case "contains" -> " 包含 ";
          case "in" -> " 属于 ";
          case "gte" -> " ≥ ";
          case "lte" -> " ≤ ";
          case "gt" -> " > ";
          case "lt" -> " < ";
          case "empty" -> " 未填写";
          case "notEmpty" -> " 已填写";
          default -> "";
        };
    var values = new ArrayList<String>();
    for (var value : node.path("values"))
      values.add(
          switch (value.asText()) {
            case "$me" -> "本人";
            case "$today" -> "今天";
            case "$now" -> "当前时间";
            default -> value.asText();
          });
    return domain.fields().get(node.path("field").asText()).label()
        + operator
        + String.join("、", values);
  }

  private void checkNames(
      JsonNode node,
      AiBusinessCatalog.Domain domain,
      AssistantReadSource source,
      SqlPredicate filter) {
    if (node.isMissingNode() || node.isNull()) return;
    if (node.has("children")) {
      for (var child : node.path("children")) checkNames(child, domain, source, filter);
      return;
    }
    var f = domain.fields().get(node.path("field").asText());
    if (node.path("op").asText().equals("eq")
        && f != null
        && !f.identity().isEmpty()
        && repository.ambiguousIdentity(
            source,
            filter,
            node.path("field").asText(),
            f.identity(),
            node.path("values").get(0).asText()))
      throw new IllegalArgumentException("所选" + f.label() + "存在同名对象，请补充公司、部门或编号限定，不能将同名对象合并");
  }

  static LocalDate[] bounds(AiBusinessPlan p, LocalDate today) {
    if (p.period().equals("all")) return null;
    LocalDate start = today, end = today;
    switch (p.period()) {
      case "today" -> {}
      case "yesterday" -> {
        start = today.minusDays(1);
        end = start;
      }
      case "week" -> {
        start = today.minusDays(today.getDayOfWeek().getValue() - 1);
        end = start.plusDays(6);
      }
      case "lastWeek" -> {
        start = today.minusDays(today.getDayOfWeek().getValue() - 1).minusWeeks(1);
        end = start.plusDays(6);
      }
      case "month" -> {
        start = today.withDayOfMonth(1);
        end = today.withDayOfMonth(today.lengthOfMonth());
      }
      case "lastMonth" -> {
        end = today.withDayOfMonth(1).minusDays(1);
        start = end.withDayOfMonth(1);
      }
      case "year" -> {
        start = today.withDayOfYear(1);
        end = start.plusYears(1).minusDays(1);
      }
      case "lastYear" -> {
        start = today.withDayOfYear(1).minusYears(1);
        end = start.plusYears(1).minusDays(1);
      }
      case "last7" -> start = today.minusDays(6);
      case "last30" -> start = today.minusDays(29);
      case "last90" -> start = today.minusDays(89);
      case "custom" -> {
        start = LocalDate.parse(p.from());
        end = LocalDate.parse(p.to());
      }
      default -> throw new IllegalArgumentException("周期无效");
    }
    return new LocalDate[] {start, end};
  }
}
