package com.windasoft.eam.common.maintenance;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;

/** Shared value and calendar rules, independent of Spring business services. */
public final class MaintenanceSupport {
  private MaintenanceSupport() {}

  public static String text(Map<String, Object> m, String key) {
    return Objects.toString(m.get(key), "").trim();
  }

  public static long number(Object value) {
    return value == null || value.toString().isBlank() ? 0 : Long.parseLong(value.toString());
  }

  public static boolean yes(Object v) {
    return Boolean.TRUE.equals(v) || "true".equals(Objects.toString(v));
  }

  public static List<Map<String, Object>> rows(Object value) {
    return value instanceof List<?> a ? (List<Map<String, Object>>) a : List.of();
  }

  public static List<Long> ids(Object value) {
    return value instanceof List<?> a
        ? a.stream().map(MaintenanceSupport::number).filter(x -> x > 0).distinct().toList()
        : List.of();
  }

  public static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  public static void deny() {
    throw new com.windasoft.eam.common.LocalizedStatusException(
        HttpStatus.FORBIDDEN, "server.maintenance.message0007");
  }

  public static List<String> strings(Object v) {
    return v instanceof List<?> a ? a.stream().map(Object::toString).toList() : List.of();
  }

  public static LocalDate advance(LocalDate date, long period, String unit) {
    return switch (unit) {
      case "日" -> date.plusDays(period);
      case "周" -> date.plusWeeks(period);
      case "月" -> date.plusMonths(period);
      case "年" -> date.plusYears(period);
      default ->
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.maintenance.message0008");
    };
  }
}
