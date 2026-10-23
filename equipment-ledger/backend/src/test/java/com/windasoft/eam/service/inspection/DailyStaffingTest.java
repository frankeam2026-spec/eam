package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DailyStaffingTest {
  Map<String, Object> rule(int weekday, String slot, String start, String end, long user) {
    return new LinkedHashMap<>(
        Map.of(
            "weekday",
            weekday,
            "slotId",
            slot,
            "start",
            start,
            "end",
            end,
            "users",
            user == 0 ? List.of() : List.of(user),
            "unassigned",
            user == 0));
  }

  List<Long> resolve(Map<String, Object> a, String day, String slot, String at) {
    return DailyStaffing.resolve(a, LocalDate.parse(day), slot, LocalDateTime.parse(at));
  }

  @Test
  void datesOverrideWeekdaysAndExplicitUnstaffedDoesNotFallBack() {
    var daily = rule(0, "day", "00:00:00", "00:00:00", 11);
    var friday = rule(5, "day", "00:00:00", "00:00:00", 12);
    var date = rule(0, "day", "00:00:00", "00:00:00", 13);
    date.put("date", "2026-10-23");
    var a =
        new HashMap<String, Object>(
            Map.of("regularRoster", List.of(daily, friday), "dateRoster", List.of(date)));
    assertEquals(List.of(13L), resolve(a, "2026-10-23", "day", "2026-10-23T08:00:00"));
    date.put("unassigned", true);
    date.put("users", List.of());
    assertEquals(List.of(), resolve(a, "2026-10-23", "day", "2026-10-23T08:00:00"));
    a.put("dateRoster", List.of());
    assertEquals(List.of(12L), resolve(a, "2026-10-23", "day", "2026-10-23T08:00:00"));
    assertEquals(List.of(11L), resolve(a, "2026-10-24", "day", "2026-10-24T08:00:00"));
    assertEquals(List.of(), resolve(a, "2026-10-24", "night", "2026-10-24T20:00:00"));
  }

  @Test
  void overnightShiftUsesBusinessDateAndDoesNotMixOtherArrangements() {
    var night = rule(5, "night", "00:00:00", "00:00:00", 11);
    var a = Map.<String, Object>of("regularRoster", List.of(night));
    assertEquals(List.of(11L), resolve(a, "2026-10-23", "night", "2026-10-24T02:00:00"));
    assertEquals(List.of(), resolve(Map.of(), "2026-10-23", "night", "2026-10-24T02:00:00"));
  }

  @Test
  void intervalAndEventMatchCoverageStartDateAndExclusiveEnd() {
    var night = rule(5, "", "20:00:00", "08:00:00", 11);
    var a = Map.<String, Object>of("regularRoster", List.of(night));
    assertEquals(List.of(11L), resolve(a, "2026-10-24", "", "2026-10-24T02:00:00"));
    assertEquals(List.of(), resolve(a, "2026-10-24", "", "2026-10-24T08:00:00"));
    assertEquals(List.of(), resolve(a, "2026-10-25", "", "2026-10-25T02:00:00"));
  }

  @Test
  void overlappingCoverageAcrossDatesAndWeekBoundariesIsRejected() {
    var friday = rule(5, "", "20:00:00", "08:00:00", 11);
    var saturday = rule(6, "", "00:00:00", "12:00:00", 12);
    assertTrue(DailyStaffing.conflict(friday, saturday, false));
    assertFalse(DailyStaffing.conflict(friday, rule(6, "", "08:00:00", "12:00:00", 12), false));
    assertTrue(
        DailyStaffing.conflict(
            rule(7, "", "20:00:00", "08:00:00", 11),
            rule(1, "", "00:00:00", "08:00:00", 12),
            false));
    friday.put("date", "2026-10-23");
    saturday.put("date", "2026-10-24");
    assertTrue(DailyStaffing.conflict(friday, saturday, true));
    assertFalse(
        DailyStaffing.conflict(
            rule(0, "day", "00:00:00", "00:00:00", 11),
            rule(5, "day", "00:00:00", "00:00:00", 12),
            false));
  }
}
