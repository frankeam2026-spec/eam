package com.windasoft.eam.repository.inspection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class DailyArrangementRepositoryStatusTest {
  @Test
  void exemptionCandidateLockAndReadQueriesBindTheSameEligibleStatuses() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
    var repository = new DailyArrangementRepository(db);
    String start = "2026-11-05T08:00";
    String end = "2026-11-05T17:00";

    repository.exemptionCandidates(7L, 99L, 11L, start, end);

    var sql = ArgumentCaptor.forClass(String.class);
    var args = ArgumentCaptor.forClass(Object[].class);
    verify(db, times(2)).queryForList(sql.capture(), args.capture());
    for (String statement : sql.getAllValues())
      assertTrue(statement.contains("status IN (?,?,?)"));
    var expected =
        new Object[] {
          7L,
          99L,
          11L,
          start.replace('T', ' '),
          end.replace('T', ' '),
          InspectionTaskStatus.NOT_STARTED.code(),
          InspectionTaskStatus.PENDING.code(),
          InspectionTaskStatus.EXPIRED.code()
        };
    for (Object[] values : args.getAllValues()) assertArrayEquals(expected, values);
  }
}
