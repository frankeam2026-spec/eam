package com.windasoft.eam.domain.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class InspectionTaskStatusTest {
  @Test
  void startableStatusesAreExplicitAndExhaustive() {
    assertGroup(
        Set.of(
            InspectionTaskStatus.NOT_STARTED,
            InspectionTaskStatus.PENDING,
            InspectionTaskStatus.EXPIRED,
            InspectionTaskStatus.REVIEW_REJECTED),
        status -> InspectionTaskStatus.canStart(status));
  }

  @Test
  void transferableStatusesAreExplicitAndExhaustive() {
    assertGroup(
        Set.of(
            InspectionTaskStatus.NOT_STARTED,
            InspectionTaskStatus.PENDING,
            InspectionTaskStatus.IN_PROGRESS,
            InspectionTaskStatus.SUSPENDED,
            InspectionTaskStatus.EXPIRED,
            InspectionTaskStatus.REVIEW_REJECTED),
        status -> InspectionTaskStatus.canTransfer(status));
  }

  @Test
  void terminalStatusesAreExplicitAndExhaustive() {
    assertGroup(
        Set.of(
            InspectionTaskStatus.COMPLETED,
            InspectionTaskStatus.REVIEWED,
            InspectionTaskStatus.CANCELLED,
            InspectionTaskStatus.EXPIRED_CANCELLED,
            InspectionTaskStatus.EXEMPT),
        status -> InspectionTaskStatus.isTerminal(status));
  }

  @Test
  void resultAmendmentStatusesAreExplicitAndExhaustive() {
    assertGroup(
        Set.of(InspectionTaskStatus.COMPLETED, InspectionTaskStatus.REVIEWED),
        status -> InspectionTaskStatus.canAmendResult(status));
  }

  @Test
  void unexecutedDailyCandidatesAreExplicitAndExhaustive() {
    assertGroup(
        Set.of(
            InspectionTaskStatus.NOT_STARTED,
            InspectionTaskStatus.PENDING,
            InspectionTaskStatus.EXPIRED),
        status -> InspectionTaskStatus.isUnexecutedDailyCandidate(status));
  }

  @Test
  void unknownStatusIsNeverAcceptedByAStatusGroup() {
    assertFalse(InspectionTaskStatus.canStart("unknown"));
    assertFalse(InspectionTaskStatus.canTransfer("unknown"));
    assertFalse(InspectionTaskStatus.isTerminal("unknown"));
    assertFalse(InspectionTaskStatus.canAmendResult("unknown"));
    assertFalse(InspectionTaskStatus.isUnexecutedDailyCandidate("unknown"));
  }

  private void assertGroup(
      Set<InspectionTaskStatus> expected, Predicate<String> actual) {
    for (var status : InspectionTaskStatus.values())
      assertEquals(expected.contains(status), actual.test(status.code()), status.name());
  }
}
