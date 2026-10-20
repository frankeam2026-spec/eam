package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
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
    if (!yes(a.get("enabled"))
        || date.isBefore(LocalDate.parse(text(a, "anchorDate")))
        || text(a, "frequency").equals("event")) return List.of();
    boolean work = strings(plan.get("workdays")).contains(date.toString());
    boolean rest =
        strings(plan.get("holidays")).contains(date.toString())
            || ids(plan.get("restDays"))
                .contains(
                    (long)
                        (text(plan, "restCycle").equals("按月")
                            ? date.getDayOfMonth()
                            : date.getDayOfWeek().getValue()));
    if (rest && !work) return List.of();
    LocalDate anchor = LocalDate.parse(text(a, "anchorDate"));
    long n = number(a.get("intervalCount"));
    boolean matches =
        switch (text(a, "frequency")) {
          case "daily" -> ChronoUnit.DAYS.between(anchor, date) % n == 0;
          case "weekly" ->
              ChronoUnit.WEEKS.between(
                              anchor.with(java.time.DayOfWeek.MONDAY),
                              date.with(java.time.DayOfWeek.MONDAY))
                          % n
                      == 0
                  && ids(a.get("days")).contains((long) date.getDayOfWeek().getValue());
          case "monthly" ->
              ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(date)) % n == 0
                  && ids(a.get("days")).contains((long) date.getDayOfMonth());
          default -> true;
        };
    if (!matches) return List.of();
    var result = new ArrayList<Occurrence>();
    String prefix = text(a, "id");
    if (Set.of("shift", "slots").contains(text(a, "frequency"))) {
      for (var slot : rows(a.get("slots")))
        result.add(
            occurrence(
                prefix + ":" + text(slot, "id"),
                text(slot, "name"),
                date,
                text(slot, "start"),
                text(slot, "end"),
                text(a, "frequency").equals("shift") ? text(slot, "id") : ""));
    } else {
      var range =
          occurrence(prefix, text(a, "name"), date, text(a, "startTime"), text(a, "endTime"), "");
      if (text(a, "frequency").equals("interval")) {
        for (LocalDateTime start = range.start();
            start.isBefore(range.end());
            start = start.plusMinutes(n)) {
          LocalDateTime end = start.plusMinutes(number(a.get("windowMinutes")));
          if (end.isAfter(range.end())) end = range.end();
          result.add(
              new Occurrence(
                  prefix + ":" + start.toLocalTime(), text(a, "name"), date, start, end, ""));
        }
      } else result.add(range);
    }
    if (a.get("effectiveFrom") != null) {
      LocalDateTime effective = dateTime(a.get("effectiveFrom"));
      return result.stream().filter(o -> o.end().isAfter(effective)).toList();
    }
    return result;
  }

  private static Occurrence occurrence(
      String key, String name, LocalDate date, String start, String end, String shift) {
    LocalDateTime from = date.atTime(LocalTime.parse(start)),
        to = date.atTime(LocalTime.parse(end));
    if (!to.isAfter(from)) to = to.plusDays(1);
    return new Occurrence(key, name, date, from, to, shift);
  }
}
