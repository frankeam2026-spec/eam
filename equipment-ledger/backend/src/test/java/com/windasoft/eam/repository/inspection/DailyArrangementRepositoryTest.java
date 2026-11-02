package com.windasoft.eam.repository.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class DailyArrangementRepositoryTest {
  @Test
  void operationTeamsExcludeSoftDeletedUsers() {
    var db = mock(JdbcTemplate.class);
    var repository = new DailyArrangementRepository(db);
    when(db.queryForList(anyString(), eq(Long.class), eq(10001L), eq(88001L)))
        .thenReturn(List.of(73001L));

    assertEquals(List.of(73001L), repository.teamMembers(10001L, 88001L));

    var sql = ArgumentCaptor.forClass(String.class);
    verify(db).queryForList(sql.capture(), eq(Long.class), eq(10001L), eq(88001L));
    assertTrue(sql.getValue().contains("u.status=1 AND u.deleted=0"));
  }
}
