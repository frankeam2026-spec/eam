package com.windasoft.eam.repository.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.windasoft.eam.repository.system.NotificationRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class InspectionTaskJournalRepositoryTest {
  @Test
  void taskEventsAreReadWithinTheTenantAndTaskBoundary() {
    var db = mock(JdbcTemplate.class);
    var notifications = mock(NotificationRepository.class);
    var expected = List.<Map<String, Object>>of(Map.of("action", "开始点检"));
    when(db.queryForList(anyString(), eq(7L), eq(9L))).thenReturn(expected);

    var repository = new InspectionTaskJournalRepository(db, notifications);

    assertEquals(expected, repository.listTaskEvents(7L, 9L));

    var sql = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(db).queryForList(sql.capture(), eq(7L), eq(9L));
    assertTrue(sql.getValue().contains("tenant_id=?"));
    assertTrue(sql.getValue().contains("task_id=?"));
    assertTrue(sql.getValue().contains("ORDER BY created_at,id"));
  }

  @Test
  void actionLookupUsesTenantTaskAndActionAsBoundParameters() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForObject(
            anyString(), eq(Long.class), eq(7L), eq(9L), eq("转派")))
        .thenReturn(1L);

    var repository =
        new InspectionTaskJournalRepository(db, mock(NotificationRepository.class));

    assertTrue(repository.hasAction(7L, 9L, "转派"));
    verify(db)
        .queryForObject(
            org.mockito.ArgumentMatchers.contains("tenant_id=? AND task_id=? AND action=?"),
            eq(Long.class),
            eq(7L),
            eq(9L),
            eq("转派"));
  }

  @Test
  void inboxSourceIsCapturedOnlyAfterMessageAndInboxRowsAreWritten() {
    var db = mock(JdbcTemplate.class);
    var notifications = mock(NotificationRepository.class);
    when(db.update(anyString(), any(Object[].class))).thenReturn(1);

    var repository = new InspectionTaskJournalRepository(db, notifications);

    assertEquals(
        1, repository.insertInbox(11L, 7L, 9L, 13L, "点巡检待执行", "设备 A", true));

    var order = inOrder(db, notifications);
    order.verify(db).update(
        org.mockito.ArgumentMatchers.startsWith("INSERT INTO eam_inspection_inbox"),
        any(Object[].class));
    order.verify(db).update(
        org.mockito.ArgumentMatchers.startsWith("UPDATE eam_inspection_inbox SET message_key"),
        any(Object[].class));
    order.verify(notifications).captureSource("eam_inspection_inbox", 7L, 11L);
  }
}
