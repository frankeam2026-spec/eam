package com.windasoft.eam.service.experience;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AnalyticsPeriodTest {
  private final LocalDateTime now = LocalDateTime.of(2026, 5, 6, 10, 30);

  @Test
  void aprilCohortRetainsAprilAndIncludesEndDay() {
    var p = AnalyticsPeriod.resolve(Map.of("preset", "lastMonth"), Map.of(), now);
    assertEquals(LocalDate.of(2026, 4, 1), p.from());
    assertEquals(LocalDateTime.of(2026, 5, 1, 0, 0), p.end());
  }

  @Test
  void followingUsesGlobalPeriod() {
    var p = AnalyticsPeriod.resolve(Map.of("preset", "follow"), Map.of("preset", "last7"), now);
    assertEquals(LocalDate.of(2026, 4, 30), p.from());
    assertEquals(LocalDate.of(2026, 5, 6), p.to());
  }

  @Test
  void twelveMonthsIncludesCurrentPartialMonth() {
    var p = AnalyticsPeriod.resolve(Map.of("preset", "last12months"), Map.of(), now);
    assertEquals(LocalDate.of(2025, 6, 1), p.from());
    assertEquals(now.toLocalDate(), p.to());
  }

  @Test
  void badRangeRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AnalyticsPeriod.resolve(
                Map.of("preset", "custom", "from", "2026-05-02", "to", "2026-05-01"),
                Map.of(),
                now));
    assertThrows(
        IllegalArgumentException.class,
        () -> AnalyticsPeriod.resolve(Map.of("preset", "custom"), Map.of(), now));
  }

  @Test
  void quarterAndYearBoundaries() {
    assertEquals(
        LocalDate.of(2026, 4, 1),
        AnalyticsPeriod.resolve(Map.of("preset", "quarter"), Map.of(), now).from());
    assertEquals(
        LocalDate.of(2026, 1, 1),
        AnalyticsPeriod.resolve(Map.of("preset", "year"), Map.of(), now).from());
  }
}
