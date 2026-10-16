package com.windasoft.eam.repository.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MaintenanceRepositoryTest {
  @Test
  void automatedGenerationPreservesNullActor() {
    var repository =
        new MaintenanceRepository(mock(JdbcTemplate.class), mock(NumberCounterMapper.class));
    assertDoesNotThrow(() -> repository.insertTaskEvent(1L, 10001L, 2L, null, "系统", "生成任务", "{}"));
  }

  @Test
  void dynamicReferenceColumnHasWhitelist() {
    var db = mock(JdbcTemplate.class);
    var repository = new MaintenanceRepository(db, mock(NumberCounterMapper.class));
    assertThrows(
        IllegalArgumentException.class,
        () -> repository.countScheduleReferences(10001L, 1L, "id OR 1=1"));
    verifyNoInteractions(db);
  }
}
