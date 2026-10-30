package com.windasoft.eam.service.experience;

import static com.windasoft.eam.common.experience.ExperienceSupport.*;

import java.time.*;
import java.util.*;
import java.util.function.Function;

public final class OverviewLayoutSchema {
  private OverviewLayoutSchema() {}

  public static final Set<String> LEGACY =
      Set.of(
          "work",
          "anomalies",
          "shortcuts",
          "recent",
          "surplus",
          "stats",
          "load",
          "inventory",
          "created");

  public static Map<String, Object> period(Object raw, boolean independent) {
    var value = HomeAnalyticsService.map(raw);
    String preset = text(value, "preset");
    if (preset.isBlank()) preset = independent ? "follow" : "month";
    check(
        AnalyticsPeriod.PRESETS.contains(preset) || independent && preset.equals("follow"),
        "统计周期无效");
    if (preset.equals("custom"))
      AnalyticsPeriod.resolve(value, Map.of(), LocalDateTime.now(AnalyticsPeriod.ZONE));
    var out = new LinkedHashMap<String, Object>();
    out.put("preset", preset);
    if (preset.equals("custom")) {
      out.put("from", text(value, "from"));
      out.put("to", text(value, "to"));
    }
    return out;
  }

  public static Map<String, Object> normalize(
      Map<String, Object> input,
      Set<String> available,
      boolean team,
      Function<Map<String, Object>, Map<String, Object>> legacy) {
    var source = rows(input.get("blocks"));
    check(!source.isEmpty() && source.size() <= 12, "首页保留1至12个区块");
    var ids = new HashSet<String>();
    var singleton = new HashSet<String>();
    var result = new ArrayList<Map<String, Object>>();
    for (var original : source) {
      String type = text(original, "type"), column = text(original, "column");
      boolean analytics = type.startsWith("analytics:");
      check(available.contains(type), "区块不可用，请重新加载首页");
      check(analytics || singleton.add(type), "执行类区块只能添加一次");
      check(Set.of("main", "side").contains(column), "区块位置无效");
      check(!type.equals("work") || column.equals("main"), "我的工作只能放在主栏");
      var candidate = new LinkedHashMap<>(original);
      Map<String, Object> normalized;
      if (analytics) {
        String id = required(original, "id", 64);
        check(id.matches("[a-zA-Z0-9_-]+"), "区块标识无效");
        String scope = text(original, "scope");
        if (scope.isBlank()) scope = "personal";
        check(
            Set.of("personal", "team").contains(scope) && (!scope.equals("team") || team),
            "没有组织范围统计权限");
        String metric = type.substring(10);
        AnalyticsCatalog.require(metric);
        int top = (int) id(original.getOrDefault("topN", 10));
        check(Set.of(5, 10, 20).contains(top), "排名数量无效");
        String currency = text(original, "currency");
        check(currency.isBlank() || currency.matches("[A-Z]{3}"), "币种无效");
        normalized =
            new LinkedHashMap<>(
                Map.of(
                    "id",
                    id,
                    "type",
                    type,
                    "title",
                    required(original, "title", 80),
                    "scope",
                    scope,
                    "width",
                    12,
                    "limit",
                    5,
                    "topN",
                    top));
        normalized.put("period", period(original.get("period"), true));
        normalized.put("currency", currency);
        for (String key : List.of("companyIds", "departmentIds")) {
          Object values = original.getOrDefault(key, List.of());
          check(values instanceof List<?> && ((List<?>) values).size() <= 100, "组织筛选无效");
          normalized.put(
              key, ((List<?>) values).stream().map(v -> Long.toString(id(v))).distinct().toList());
        }
      } else {
        check(LEGACY.contains(type), "未知区块");
        candidate.put("type", type.equals("work") ? "todo" : type);
        candidate.put("width", 12);
        normalized =
            new LinkedHashMap<>(
                rows(legacy.apply(Map.of("blocks", List.of(candidate))).get("blocks")).getFirst());
        check(!type.equals("work") || !text(normalized, "scope").equals("team"), "我的工作只展示个人事项");
        normalized.put("type", type);
      }
      check(ids.add(text(normalized, "id")), "区块标识重复");
      normalized.put("column", column);
      result.add(normalized);
    }
    var raw = input.getOrDefault("metrics", List.of("pending", "active", "today", "notification"));
    check(raw instanceof List<?>, "工作指标配置无效");
    var metrics = (List<?>) raw;
    check(
        metrics.size() <= 4
            && metrics.stream()
                .allMatch(
                    value ->
                        value instanceof String metric
                            && Set.of("pending", "active", "today", "notification")
                                .contains(metric))
            && new HashSet<>(metrics).size() == metrics.size(),
        "工作指标配置无效");
    String mode = text(input, "layoutMode");
    if (mode.isBlank()) mode = "work";
    check(Set.of("work", "analysis").contains(mode), "布局类型无效");
    var out = new LinkedHashMap<String, Object>();
    out.put("schemaVersion", 2);
    out.put("layoutMode", mode);
    out.put("period", period(input.get("period"), false));
    out.put("metrics", metrics);
    out.put("blocks", result);
    for (String key : List.of("companyIds", "departmentIds")) {
      Object idsRaw = input.getOrDefault(key, List.of());
      check(idsRaw instanceof List<?> && ((List<?>) idsRaw).size() <= 100, "组织范围无效");
      out.put(
          key,
          ((List<?>) idsRaw)
              .stream()
                  .map(
                      v -> {
                        check(id(v) > 0, "组织ID无效");
                        return Long.toString(id(v));
                      })
                  .distinct()
                  .toList());
    }
    return out;
  }

  public static List<Map<String, Object>> presets(boolean team, Set<String> available) {
    var out = new ArrayList<Map<String, Object>>();
    var specs =
        List.of(
            new String[] {
              "设备主管分析",
              "repair_trend",
              "repair_ranking",
              "maintenance_execution",
              "personnel",
              "anomaly_ranking"
            },
            new String[] {
              "公司与集团管理分析",
              "repair_trend",
              "repair_ranking",
              "maintenance_execution",
              "equipment_material_ranking",
              "material_trend",
              "anomaly_ranking"
            },
            new String[] {
              "仓库分析", "material_trend", "material_ranking", "equipment_material_ranking"
            });
    for (var spec : specs) {
      var blocks = new ArrayList<Map<String, Object>>();
      for (int i = 1; i < spec.length; i++) {
        String type = "analytics:" + spec[i];
        if (!available.contains(type)) continue;
        var metric = AnalyticsCatalog.require(spec[i]);
        blocks.add(
            new LinkedHashMap<>(
                Map.of(
                    "id",
                    "preset-" + spec[i],
                    "type",
                    type,
                    "title",
                    metric.name(),
                    "column",
                    i % 2 == 1 ? "main" : "side",
                    "scope",
                    team ? "team" : "personal",
                    "width",
                    12,
                    "limit",
                    5,
                    "topN",
                    10,
                    "period",
                    Map.of("preset", metric.period()))));
      }
      if (!blocks.isEmpty())
        out.add(
            Map.of(
                "name",
                spec[0],
                "layout",
                Map.of(
                    "schemaVersion",
                    2,
                    "layoutMode",
                    "analysis",
                    "period",
                    Map.of("preset", "month"),
                    "metrics",
                    List.of(),
                    "blocks",
                    blocks)));
    }
    return out;
  }
}
