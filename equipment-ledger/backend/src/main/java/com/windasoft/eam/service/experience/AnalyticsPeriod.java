package com.windasoft.eam.service.experience;

import static com.windasoft.eam.common.experience.ExperienceSupport.*;

import java.time.*;
import java.util.*;

public record AnalyticsPeriod(LocalDate from, LocalDate to, LocalDateTime asOf, String preset) {
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  public static final Set<String> PRESETS =
      Set.of(
          "yesterday",
          "last7",
          "last90",
          "month",
          "lastMonth",
          "quarter",
          "year",
          "custom",
          "last12months");

  public static AnalyticsPeriod resolve(
      Map<String, Object> local, Map<String, Object> global, LocalDateTime now) {
    String p = text(local, "preset");
    Map<String, Object> source = local;
    if (p.isBlank() || p.equals("follow")) {
      source = global;
      p = text(global, "preset");
    }
    if (p.isBlank()) p = "month";
    check(PRESETS.contains(p), "统计周期无效");
    LocalDate today = now.toLocalDate(), from = today, to = today;
    switch (p) {
      case "yesterday" -> {
        from = today.minusDays(1);
        to = from;
      }
      case "last7" -> from = today.minusDays(6);
      case "last90" -> from = today.minusDays(89);
      case "month" -> {
        from = today.withDayOfMonth(1);
        to = today.withDayOfMonth(today.lengthOfMonth());
      }
      case "lastMonth" -> {
        from = today.minusMonths(1).withDayOfMonth(1);
        to = from.withDayOfMonth(from.lengthOfMonth());
      }
      case "quarter" -> {
        from = LocalDate.of(today.getYear(), ((today.getMonthValue() - 1) / 3) * 3 + 1, 1);
        to = from.plusMonths(3).minusDays(1);
      }
      case "year" -> {
        from = LocalDate.of(today.getYear(), 1, 1);
        to = LocalDate.of(today.getYear(), 12, 31);
      }
      case "last12months" -> {
        from = today.minusMonths(11).withDayOfMonth(1);
        to = today;
      }
      case "custom" -> {
        try {
          from = LocalDate.parse(text(source, "from"));
          to = LocalDate.parse(text(source, "to"));
        } catch (Exception e) {
          throw new IllegalArgumentException("请选择完整的日期区间");
        }
      }
    }
    check(!from.isAfter(to), "开始日期不能晚于结束日期");
    check(
        !to.isAfter(today.plusYears(1))
            && !from.isBefore(today.minusYears(10))
            && !to.isAfter(from.plusYears(5)),
        "统计区间最多5年，起始时间不得早于10年前");
    return new AnalyticsPeriod(from, to, now, p);
  }

  public LocalDateTime start() {
    return from.atStartOfDay();
  }

  public LocalDateTime end() {
    return to.plusDays(1).atStartOfDay();
  }

  public String label() {
    String name =
        switch (preset) {
          case "last90" -> "最近90天";
          case "last12months" -> "最近12个自然月";
          case "last7" -> "最近7天";
          case "month" -> "本月";
          case "lastMonth" -> "上月";
          case "yesterday" -> "昨日";
          case "quarter" -> "本季度";
          case "year" -> "本年";
          default -> "自定义";
        };
    return name + " · " + from + " 至 " + to + (!to.isBefore(asOf.toLocalDate()) ? "（截至当前）" : "");
  }
}
