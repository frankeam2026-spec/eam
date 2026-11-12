package com.windasoft.eam.repository.maintenance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.windasoft.eam.entity.maintenance.MaintenanceEvent;
import com.windasoft.eam.repository.common.TaskSourceRelationsRepository;
import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MaintenanceRepositoryTest {
  @Test
  void automatedGenerationPreservesNullActor() {
    var mapper = mock(MaintenanceEventMapper.class);
    var repository = new MaintenanceEventRepository(mapper);
    var event = new MaintenanceEvent();
    event.setId(1L);
    event.setTenantId(10001L);
    event.setTaskId(2L);
    event.setActorId(null);
    event.setActorName("系统");
    event.setAction("生成任务");
    event.setDetails("{}");
    assertDoesNotThrow(() -> repository.insert(event));
    verify(mapper).insert(event);
    assertNull(event.getActorId());
  }

  @Test
  void dynamicReferenceColumnHasWhitelist() {
    var db = mock(JdbcTemplate.class);
    var repository =
        new MaintenanceRepository(
            db,
            mock(NumberCounterMapper.class),
            mock(MaintenanceDefinitionStorage.class),
            mock(MaintenanceExecutionStorage.class),
            mock(TaskSourceRelationsRepository.class));
    assertThrows(
        IllegalArgumentException.class,
        () -> repository.countScheduleReferences(10001L, 1L, "id OR 1=1"));
    verifyNoInteractions(db);
  }
}
