package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import java.time.*;
import java.util.*;

/** Pure calendar calculation shared by preview and persisted task generation. */
public final class DailyInspectionSchedule {
  private DailyInspectionSchedule() {}

  public record Occurrence(
      String key,
      String name,
      LocalDate businessDate,
      LocalDateTime start,
      LocalDateTime end,
      String rosterShiftId) {}

  public static List<Occurrence> on(
      Map<String, Object> plan, Map<String, Object> a, LocalDate date) {
    if ((!text(plan, "startDate").isBlank() && date.isBefore(LocalDate.parse(text(plan, "startDate"))))
        || (!text(plan, "endDate").isBlank() && date.isAfter(LocalDate.parse(text(plan, "endDate"))))
        || !Set.of("shift", "slots").contains(text(a, "frequency"))
        || rest(plan, date)) return List.of();
    var result = new ArrayList<Occurrence>();
    String prefix = text(a, "id");
    for (var slot : rows(a.get("slots")))
        result.add(
            occurrence(
                prefix + ":" + text(slot, "id"),
                text(slot, "name"),
                date,
                text(slot, "start"),
                text(slot, "end"),
                text(slot, "id")));
    if (a.get("effectiveFrom") != null) {
      LocalDateTime effective = dateTime(a.get("effectiveFrom"));
      return result.stream().filter(o -> o.end().isAfter(effective)).toList();
    }
    return result;
  }

  /** Rest rules use the shift's start date. An explicit workday overrides rest rules. */
  public static boolean rest(Map<String, Object> plan, LocalDate date) {
    if (strings(plan.get("workdays")).contains(date.toString())) return false;
    boolean weekly = ids(plan.get("weeklyRestDays")).contains((long) date.getDayOfWeek().getValue());
    boolean monthly = ids(plan.get("monthlyRestDays")).contains((long) date.getDayOfMonth());
    // Old request clients can still submit the former single-cycle calendar.
    boolean legacy = !plan.containsKey("weeklyRestDays") && !plan.containsKey("monthlyRestDays")
        && ids(plan.get("restDays")).contains((long) (text(plan, "restCycle").equals("按月")
            ? date.getDayOfMonth() : date.getDayOfWeek().getValue()));
    return weekly || monthly || legacy || strings(plan.get("holidays")).contains(date.toString());
  }

  private static Occurrence occurrence(
      String key, String name, LocalDate date, String start, String end, String shift) {
    LocalDateTime from = date.atTime(LocalTime.parse(start)),
        to = date.atTime(LocalTime.parse(end));
    if (!to.isAfter(from)) to = to.plusDays(1);
    return new Occurrence(key, name, date, from, to, shift);
  }
}
