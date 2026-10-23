package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DailyInspectionScheduleTest {
  Map<String, Object> arrangement() {
    return new LinkedHashMap<>(Map.of("id", "a", "enabled", true, "frequency", "shift",
        "anchorDate", "2026-01-01", "slots", List.of(
            Map.of("id", "day", "name", "白班", "start", "08:00:00", "end", "16:00:00"),
            Map.of("id", "mid", "name", "中班", "start", "16:00:00", "end", "23:00:00"),
            Map.of("id", "night", "name", "晚班", "start", "23:00:00", "end", "08:00:00"))));
  }

  @Test void oneOccurrencePerShiftWithStableKeysAndOvernightStartDate() {
    var date = LocalDate.of(2026, 10, 25);
    var rows = DailyInspectionSchedule.on(Map.of(), arrangement(), date);
    assertEquals(3, rows.size());
    assertEquals(List.of("a:day", "a:mid", "a:night"), rows.stream().map(DailyInspectionSchedule.Occurrence::key).toList());
    assertEquals(date, rows.getLast().businessDate());
    assertEquals(date.plusDays(1).atTime(8, 0), rows.getLast().end());
    assertEquals(rows, DailyInspectionSchedule.on(Map.of(), arrangement(), date));
  }

  @Test void noShiftsMeansNoTasksAndOldFrequenciesNeverGenerate() {
    var a = arrangement();
    a.put("slots", List.of());
    assertTrue(DailyInspectionSchedule.on(Map.of(), a, LocalDate.of(2026, 10, 23)).isEmpty());
    for (String frequency : List.of("daily", "weekly", "monthly", "interval", "event")) {
      a = arrangement();
      a.put("frequency", frequency);
      assertTrue(DailyInspectionSchedule.on(Map.of(), a, LocalDate.of(2026, 10, 23)).isEmpty(), frequency);
    }
  }

  @Test void weeklyMondayAndWednesdayAndMonthlyFirstTenthFifteenthAreCombined() {
    var plan = Map.<String, Object>of("weeklyRestDays", List.of(1, 3), "monthlyRestDays", List.of(1, 10, 15));
    for (String date : List.of("2026-10-26", "2026-10-28", "2026-11-01", "2026-11-10", "2026-11-15"))
      assertTrue(DailyInspectionSchedule.on(plan, arrangement(), LocalDate.parse(date)).isEmpty(), date);
    assertEquals(3, DailyInspectionSchedule.on(plan, arrangement(), LocalDate.of(2026, 10, 23)).size());
  }

  @Test void monthly31DoesNotSpillIntoShortMonthsAndLeapDayWorks() {
    var plan = Map.<String, Object>of("monthlyRestDays", List.of(29, 31));
    assertEquals(3, DailyInspectionSchedule.on(plan, arrangement(), LocalDate.of(2027, 2, 28)).size());
    assertTrue(DailyInspectionSchedule.on(plan, arrangement(), LocalDate.of(2028, 2, 29)).isEmpty());
    assertEquals(3, DailyInspectionSchedule.on(plan, arrangement(), LocalDate.of(2026, 4, 30)).size());
  }

  @Test void holidaysSuppressAndExplicitWorkdaysOverrideButNeverInventShifts() {
    var date = LocalDate.of(2026, 10, 26);
    var p = new LinkedHashMap<String, Object>(Map.of("weeklyRestDays", List.of(1), "holidays", List.of(date.toString())));
    assertTrue(DailyInspectionSchedule.on(p, arrangement(), date).isEmpty());
    p.put("workdays", List.of(date.toString()));
    assertEquals(3, DailyInspectionSchedule.on(p, arrangement(), date).size());
    var a = arrangement(); a.put("slots", List.of());
    assertTrue(DailyInspectionSchedule.on(p, a, date).isEmpty());
  }

  @Test void overnightShiftIsNotSuppressedByTheFollowingDaysRestRule() {
    var plan = Map.<String, Object>of("weeklyRestDays", List.of(1));
    assertEquals(3, DailyInspectionSchedule.on(plan, arrangement(), LocalDate.of(2026, 10, 25)).size());
    assertTrue(DailyInspectionSchedule.on(plan, arrangement(), LocalDate.of(2026, 10, 26)).isEmpty());
  }

  @Test void disabledBeforeAnchorAndFinishedWindowsNeverGenerate() {
    var a = arrangement();
    a.put("enabled", false);
    assertTrue(DailyInspectionSchedule.on(Map.of(), a, LocalDate.of(2026, 10, 23)).isEmpty());
    a.put("enabled", true);
    assertTrue(DailyInspectionSchedule.on(Map.of("startDate", "2026-01-01"), a, LocalDate.of(2025, 12, 31)).isEmpty());
    a.put("effectiveFrom", "2026-10-23T16:00:00");
    assertEquals(2, DailyInspectionSchedule.on(Map.of(), a, LocalDate.of(2026, 10, 23)).size());
  }

  @Test void formerSingleCycleCalendarIsStillUnderstoodWhenNewFieldsAreAbsent() {
    var plan = Map.<String, Object>of("restCycle", "按月", "restDays", List.of(15));
    assertTrue(DailyInspectionSchedule.on(plan, arrangement(), LocalDate.of(2026, 10, 15)).isEmpty());
  }

  @Test void planDatesReplaceAnIndependentArrangementAnchor() {
    var a = arrangement();
    a.put("anchorDate", "2099-01-01");
    var p = Map.<String,Object>of("startDate", "2026-10-23", "endDate", "2026-10-25");
    assertEquals(3, DailyInspectionSchedule.on(p, a, LocalDate.of(2026, 10, 23)).size());
    assertTrue(DailyInspectionSchedule.on(p, a, LocalDate.of(2026, 10, 22)).isEmpty());
    assertTrue(DailyInspectionSchedule.on(p, a, LocalDate.of(2026, 10, 26)).isEmpty());
  }
}
