package com.windasoft.eam.common.inspection;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        : new BigDecimal(value.toString()).longValueExact();
  }

  public static boolean yes(Object v) {
    return Boolean.TRUE.equals(v) || "true".equals(Objects.toString(v));
  }

  public static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  public static void deny() {
    throw new LocalizedStatusException(HttpStatus.FORBIDDEN, "server.inspection.message0007");
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

  /**
   * 创建和后续维护共用的待执行通知资格。
   *
   * <p>补生成的任务即使暂存为待执行，超过截止且不可补检时也不能催执行；已免检、已作废和未来任务不生成此待办。
   *
   * <p>超期跟进等其他提醒仍按各自规则处理，执行权限继续由业务入口校验。
   */
  public static boolean executionTodoEligible(
      String status,
      LocalDateTime due,
      LocalDateTime expires,
      boolean allowLate,
      LocalDateTime at) {
    return (InspectionTaskStatus.PENDING.code().equals(status)
            || (InspectionTaskStatus.EXPIRED.code().equals(status) && allowLate))
        && !due.isAfter(at)
        && (allowLate || !at.isAfter(expires));
  }

  public static LocalDateTime dateTime(Object value) {
    return value instanceof Timestamp t
        ? t.toLocalDateTime()
        : LocalDateTime.parse(value.toString().replace(' ', 'T'));
  }

  /** Old periodic clients send dates; their end date includes the whole day. */
  public static LocalDateTime periodicBoundary(Object value, boolean end) {
    String text = Objects.toString(value, "").trim();
    if (text.length() == 10) {
      LocalDate day = LocalDate.parse(text);
      return day.atTime(end ? LocalTime.of(23, 59, 59) : LocalTime.MIDNIGHT);
    }
    LocalDateTime result = dateTime(text);
    require(
        result.getNano() == 0, MessageContext.text("server.inspection.message0017", "planTime"));
    return result;
  }

  public static Long nullable(Object value) {
    return value == null ? null : number(value);
  }

  public static void range(Map<String, Object> data, String key, long min, long max) {
    long value = number(data.get(key));
    require(
        value >= min && value <= max, MessageContext.text("server.inspection.message0017", key));
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
      default -> throw new LocalizedArgumentException("server.inspection.message0008");
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
          default -> throw new LocalizedArgumentException("server.inspection.message0008");
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
