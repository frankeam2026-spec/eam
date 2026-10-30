package com.windasoft.eam.service.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.system.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RepairApprovalReminderTest {
  @Test
  void waitStartsAtApprovalRatherThanReportAndPendingDoesNotRemind() {
    var repo = mock(ReminderRepository.class);
    var notifications = mock(NotificationRepository.class);
    var service = new ReminderService(repo, notifications, new ObjectMapper());
    var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    var row =
        new HashMap<String, Object>(
            Map.of(
                "status",
                "待派工",
                "reported_at",
                now.minusHours(2),
                "approval_ready_at",
                now.minusMinutes(20),
                "deleted",
                0,
                "reminder_excluded",
                0));
    when(repo.source("repair", 10001, 9, true)).thenReturn(row);
    when(notifications.effectivePolicy(10001, "repair", null))
        .thenReturn(Map.of("id", 1L, "row_version", 1, "effective_at", now.minusDays(1)));
    when(notifications.rules(10001, 1))
        .thenReturn(
            List.of(
                Map.of(
                    "id",
                    2L,
                    "scene",
                    "WAIT_DISPATCH",
                    "value",
                    30,
                    "unit",
                    "MINUTE",
                    "enabled",
                    1,
                    "effectiveAt",
                    now.minusDays(1))));
    when(notifications.people(10001, 1))
        .thenReturn(List.of(Map.of("ruleId", 2L, "userId", 3L, "recipientKind", "TO")));
    when(notifications.activeUser(10001, 3)).thenReturn(true);
    service.scan("repair", 10001, 9);
    verify(repo, never())
        .message(
            anyLong(),
            anyString(),
            anyString(),
            anyLong(),
            anyLong(),
            anyInt(),
            anyString(),
            anyString(),
            anyString(),
            anyLong(),
            anyString(),
            any());
    row.put("approval_ready_at", now.minusMinutes(40));
    service.scan("repair", 10001, 9);
    verify(repo)
        .message(
            eq(10001L),
            anyString(),
            eq("repair"),
            eq(9L),
            eq(2L),
            eq(1),
            eq("WAIT_DISPATCH"),
            anyString(),
            anyString(),
            eq(3L),
            eq("TO"),
            eq(now.minusMinutes(10)));
    clearInvocations(repo);
    row.put("status", "审批中");
    row.remove("approval_ready_at");
    service.scan("repair", 10001, 9);
    verify(repo, never())
        .message(
            anyLong(),
            anyString(),
            anyString(),
            anyLong(),
            anyLong(),
            anyInt(),
            anyString(),
            anyString(),
            anyString(),
            anyLong(),
            anyString(),
            any());
  }
}
