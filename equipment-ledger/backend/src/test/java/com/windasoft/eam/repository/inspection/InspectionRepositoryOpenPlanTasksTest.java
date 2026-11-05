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
import com.windasoft.eam.repository.common.TaskSourceRelationsRepository;
import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class InspectionRepositoryOpenPlanTasksTest {
  @Test
  void openPlanTaskLockExcludesEveryTerminalStatusIncludingExempt() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
    var repository =
        new InspectionRepository(
            db,
            mock(NumberCounterMapper.class),
            mock(InspectionDefinitionStorage.class),
            mock(InspectionExecutionStorage.class),
            mock(TaskSourceRelationsRepository.class));

    repository.lockOpenPlanTasks(7L, 99L);

    var sql = ArgumentCaptor.forClass(String.class);
    var args = ArgumentCaptor.forClass(Object[].class);
    verify(db, times(2)).queryForList(sql.capture(), args.capture());
    for (String statement : sql.getAllValues()) {
      assertTrue(statement.contains("status NOT IN (?,?,?,?,?)"));
    }
    var expected =
        new Object[] {
          7L,
          99L,
          InspectionTaskStatus.COMPLETED.code(),
          InspectionTaskStatus.REVIEWED.code(),
          InspectionTaskStatus.CANCELLED.code(),
          InspectionTaskStatus.EXPIRED_CANCELLED.code(),
          InspectionTaskStatus.EXEMPT.code()
        };
    for (Object[] values : args.getAllValues()) assertArrayEquals(expected, values);
  }
}
