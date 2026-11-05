package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class InspectionTaskTransferStatusTest {
  private static final LocalDateTime DUE = LocalDateTime.parse("2026-11-05T08:00:00");
  private static final LocalDateTime EXPIRES = LocalDateTime.parse("2026-11-05T10:00:00");

  @Test
  void transferredTaskKeepsTheCorrectTimeBasedLifecycleState() {
    assertEquals(
        InspectionTaskStatus.NOT_STARTED.code(),
        InspectionTaskService.transferredStatus(DUE.minusSeconds(1), DUE, EXPIRES));
    assertEquals(
        InspectionTaskStatus.PENDING.code(),
        InspectionTaskService.transferredStatus(DUE, DUE, EXPIRES));
    assertEquals(
        InspectionTaskStatus.PENDING.code(),
        InspectionTaskService.transferredStatus(DUE.plusHours(1), DUE, EXPIRES));
    assertEquals(
        InspectionTaskStatus.PENDING.code(),
        InspectionTaskService.transferredStatus(EXPIRES, DUE, EXPIRES));
    assertEquals(
        InspectionTaskStatus.EXPIRED.code(),
        InspectionTaskService.transferredStatus(EXPIRES.plusNanos(1), DUE, EXPIRES));
  }
}
