package com.windasoft.eam.domain.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.EnumSet;
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

  @Test
  void everyTaskActionHasAnExplicitAllowedSourceStatusMatrix() {
    var terminal =
        Set.of(
            InspectionTaskStatus.COMPLETED,
            InspectionTaskStatus.REVIEWED,
            InspectionTaskStatus.CANCELLED,
            InspectionTaskStatus.EXPIRED_CANCELLED,
            InspectionTaskStatus.EXEMPT);
    for (var action : InspectionTaskAction.values()) {
      for (var status : InspectionTaskStatus.values()) {
        boolean expected =
            switch (action) {
              case START ->
                  Set.of(
                          InspectionTaskStatus.NOT_STARTED,
                          InspectionTaskStatus.PENDING,
                          InspectionTaskStatus.EXPIRED,
                          InspectionTaskStatus.REVIEW_REJECTED)
                      .contains(status);
              case SCAN, DRAFT, SUBMIT, SUSPEND -> status == InspectionTaskStatus.IN_PROGRESS;
              case RESUME -> status == InspectionTaskStatus.SUSPENDED;
              case REVIEW -> status == InspectionTaskStatus.PENDING_REVIEW;
              case TRANSFER ->
                  Set.of(
                          InspectionTaskStatus.NOT_STARTED,
                          InspectionTaskStatus.PENDING,
                          InspectionTaskStatus.IN_PROGRESS,
                          InspectionTaskStatus.SUSPENDED,
                          InspectionTaskStatus.EXPIRED,
                          InspectionTaskStatus.REVIEW_REJECTED)
                      .contains(status);
              case EXEMPT ->
                  Set.of(
                          InspectionTaskStatus.NOT_STARTED,
                          InspectionTaskStatus.PENDING,
                          InspectionTaskStatus.EXPIRED)
                      .contains(status);
              case AMEND_RESULT ->
                  status == InspectionTaskStatus.COMPLETED
                      || status == InspectionTaskStatus.REVIEWED;
              case VOID -> !terminal.contains(status);
              case DELETE -> true;
            };
        assertEquals(
            expected,
            InspectionTaskStatus.allows(action, status.code()),
            action + " from " + status);
      }
    }
    assertFalse(InspectionTaskStatus.allows(InspectionTaskAction.DELETE, "unknown"));
    assertFalse(InspectionTaskStatus.allows(null, InspectionTaskStatus.PENDING.code()));
  }

  private void assertGroup(
      Set<InspectionTaskStatus> expected, Predicate<String> actual) {
    for (var status : InspectionTaskStatus.values())
      assertEquals(expected.contains(status), actual.test(status.code()), status.name());
  }
}
