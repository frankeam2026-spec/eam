package com.windasoft.eam.repository.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import com.windasoft.eam.repository.common.JdbcListRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class InspectionListRepositoryStatusTest {
  @Test
  void deviceMatchBindsAllTaskStatusesFromTheDomainEnum() {
    var repository =
        new InspectionListRepository(mock(JdbcListRepository.class), new ObjectMapper());

    var predicate = repository.deviceMatch("42", "");

    assertTrue(predicate.sql().contains("t.status NOT IN (?,?,?)"));
    assertTrue(predicate.sql().contains("t.status IN (?,?,?,?,?)"));
    assertEquals(
        List.of(
            "42",
            InspectionTaskStatus.CANCELLED.code(),
            InspectionTaskStatus.EXPIRED_CANCELLED.code(),
            InspectionTaskStatus.EXEMPT.code(),
            InspectionTaskStatus.PENDING.code(),
            InspectionTaskStatus.IN_PROGRESS.code(),
            InspectionTaskStatus.SUSPENDED.code(),
            InspectionTaskStatus.EXPIRED.code(),
            InspectionTaskStatus.REVIEW_REJECTED.code()),
        predicate.arguments());
  }
}
