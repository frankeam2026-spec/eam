package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import java.time.*;
import java.util.*;

/** Pure assignment resolution shared by preview, generation and pending-task refresh. */
public final class DailyStaffing {
  private DailyStaffing() {}

  public static List<Long> resolve(
      Map<String, Object> a, LocalDate businessDate, String slot, LocalDateTime at) {
    var overrides =
        rows(a.get("dateRoster")).stream()
            .filter(
                r ->
                    matches(r, slot, at)
                        && text(r, "date")
                            .equals(coveredDate(r, businessDate, slot, at).toString()))
            .toList();
    if (!overrides.isEmpty()) return people(overrides.getFirst());
    var rules =
        rows(a.get("regularRoster")).stream()
            .filter(r -> matches(r, slot, at))
            .filter(
                r ->
                    number(r.get("weekday")) == 0
                        || number(r.get("weekday"))
                            == coveredDate(r, businessDate, slot, at).getDayOfWeek().getValue())
            .sorted(
                Comparator.comparingLong((Map<String, Object> r) -> number(r.get("weekday")))
                    .reversed())
            .toList();
    return rules.isEmpty() ? List.of() : people(rules.getFirst());
  }

  private static List<Long> people(Map<String, Object> r) {
    return yes(r.get("unassigned")) ? List.of() : ids(r.get("users"));
  }

  private static boolean matches(Map<String, Object> r, String slot, LocalDateTime at) {
    if (!slot.isBlank()) return text(r, "slotId").equals(slot);
    if (!text(r, "slotId").isBlank()) return false;
    LocalTime start = LocalTime.parse(text(r, "start")),
        end = LocalTime.parse(text(r, "end")),
        t = at.toLocalTime();
    if (start.equals(end)) return true;
    return end.isAfter(start)
        ? !t.isBefore(start) && t.isBefore(end)
        : !t.isBefore(start) || t.isBefore(end);
  }

  private static LocalDate coveredDate(
      Map<String, Object> r, LocalDate businessDate, String slot, LocalDateTime at) {
    if (!slot.isBlank()) return businessDate;
    LocalTime start = LocalTime.parse(text(r, "start")), end = LocalTime.parse(text(r, "end"));
    return end.isBefore(start) && at.toLocalTime().isBefore(end)
        ? at.toLocalDate().minusDays(1)
        : at.toLocalDate();
  }

  public static boolean overlaps(Map<String, Object> left, Map<String, Object> right) {
    if (!text(left, "slotId").equals(text(right, "slotId"))) return false;
    if (!text(left, "slotId").isBlank()) return true;
    for (int[] a : segments(left))
      for (int[] b : segments(right)) if (Math.max(a[0], b[0]) < Math.min(a[1], b[1])) return true;
    return false;
  }

  public static boolean conflict(Map<String, Object> a, Map<String, Object> b, boolean dates) {
    if (!text(a, "slotId").equals(text(b, "slotId"))) return false;
    boolean same =
        dates
            ? text(a, "date").equals(text(b, "date"))
            : number(a.get("weekday")) == number(b.get("weekday"));
    if (!text(a, "slotId").isBlank()) return same;
    if (!dates && (number(a.get("weekday")) == 0 || number(b.get("weekday")) == 0))
      return same && overlaps(a, b);
    long dayA = dates ? LocalDate.parse(text(a, "date")).toEpochDay() : number(a.get("weekday"));
    long dayB = dates ? LocalDate.parse(text(b, "date")).toEpochDay() : number(b.get("weekday"));
    long[] ra = absoluteWindow(a, dayA), rb = absoluteWindow(b, dayB);
    for (long shift : dates ? new long[] {0} : new long[] {-604800, 0, 604800})
      if (Math.max(ra[0], rb[0] + shift) < Math.min(ra[1], rb[1] + shift)) return true;
    return false;
  }

  private static long[] absoluteWindow(Map<String, Object> r, long day) {
    int start = LocalTime.parse(text(r, "start")).toSecondOfDay(),
        end = LocalTime.parse(text(r, "end")).toSecondOfDay();
    if (start == end) return new long[] {day * 86400, day * 86400 + 86400};
    return new long[] {day * 86400 + start, day * 86400 + end + (end < start ? 86400 : 0)};
  }

  private static List<int[]> segments(Map<String, Object> r) {
    int start = LocalTime.parse(text(r, "start")).toSecondOfDay(),
        end = LocalTime.parse(text(r, "end")).toSecondOfDay();
    if (start == end) return List.of(new int[] {0, 86400});
    return start < end
        ? List.of(new int[] {start, end})
        : List.of(new int[] {start, 86400}, new int[] {0, end});
  }
}
