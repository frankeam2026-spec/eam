package com.windasoft.eam.common.inspection;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;

/** Shared value and calendar rules, independent of Spring business services. */
public final class InspectionSupport {
  private InspectionSupport() {}

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  public static String text(Map<String, Object> m, String key) {
    return Objects.toString(m.get(key), "").trim();
  }

  public static long number(Object value) {
    return value == null || value.toString().isBlank()
        ? 0
        : new java.math.BigDecimal(value.toString()).longValueExact();
  }

  public static boolean yes(Object v) {
    return Boolean.TRUE.equals(v) || "true".equals(Objects.toString(v));
  }

  public static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  public static void deny() {
    throw new com.windasoft.eam.common.LocalizedStatusException(
        HttpStatus.FORBIDDEN, "server.inspection.message0007");
  }

  public static List<Map<String, Object>> rows(Object value) {
    return value instanceof List<?> a ? (List<Map<String, Object>>) a : List.of();
  }

  public static List<String> strings(Object value) {
    return value instanceof List<?> a
        ? a.stream().map(Object::toString).filter(s -> !s.isBlank()).distinct().toList()
        : List.of();
  }

  public static List<Long> ids(Object value) {
    return strings(value).stream().map(InspectionSupport::number).filter(v -> v > 0).toList();
  }

  public static LocalDateTime now() {
    return LocalDateTime.now(ZONE);
  }

  public static LocalDateTime dateTime(Object value) {
    return value instanceof java.sql.Timestamp t
        ? t.toLocalDateTime()
        : LocalDateTime.parse(value.toString().replace(' ', 'T'));
  }

  public static Long nullable(Object value) {
    return value == null ? null : number(value);
  }

  public static void range(Map<String, Object> data, String key, long min, long max) {
    long value = number(data.get(key));
    require(
        value >= min && value <= max,
        com.windasoft.eam.common.MessageContext.text("server.inspection.message0017", key));
  }

  public static LocalDateTime shiftDuration(
      LocalDateTime base, Map<String, Object> data, String key, String unitKey, int direction) {
    long amount = number(data.get(key)) * direction;
    String unit = text(data, unitKey);
    return switch (unit.isBlank() ? "时" : unit) {
      case "时" -> base.plusHours(amount);
      case "日" -> base.plusDays(amount);
      case "周" -> base.plusWeeks(amount);
      case "月" -> base.plusMonths(amount);
      default ->
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.inspection.message0008");
    };
  }

  public static void duration(
      Map<String, Object> data, String key, String unitKey, long min, int maxYears) {
    if (text(data, unitKey).isBlank()) data.put(unitKey, "时");
    String unit = text(data, unitKey);
    long max =
        switch (unit) {
          case "时" -> 8760L * maxYears;
          case "日" -> 365L * maxYears;
          case "周" -> 52L * maxYears;
          case "月" -> 12L * maxYears;
          default ->
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.inspection.message0008");
        };
    range(data, key, min, max);
  }

  public static LocalDateTime generationDeadline(Map<String, Object> data) {
    LocalDateTime advance = shiftDuration(now(), data, "advanceHours", "advanceUnit", 1);
    LocalDateTime reminder = shiftDuration(now(), data, "remindHours", "remindUnit", 1);
    // The search horizon includes month-end clamping; eligibility is checked against each due date.
    if (text(data, "advanceUnit").equals("月") && number(data.get("advanceHours")) > 0)
      advance = advance.plusDays(3);
    if (text(data, "remindUnit").equals("月") && number(data.get("remindHours")) > 0)
      reminder = reminder.plusDays(3);
    return advance.isAfter(reminder) ? advance : reminder;
  }

  public static boolean readyForGeneration(LocalDateTime due, Map<String, Object> data) {
    var now = now();
    return !now.isBefore(shiftDuration(due, data, "advanceHours", "advanceUnit", -1))
        || !now.isBefore(shiftDuration(due, data, "remindHours", "remindUnit", -1));
  }
}
