package com.windasoft.eam.service.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.NotificationViews;
import com.windasoft.eam.entity.system.NotifyPolicy;
import com.windasoft.eam.entity.system.NotifyRule;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.system.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RepairApprovalReminderTest {
  @Test
  void waitStartsAtApprovalRatherThanReportAndPendingDoesNotRemind() {
    var repo = mock(ReminderRepository.class);
    var notifications = mock(NotificationRepository.class);
    var policies = mock(NotificationPolicyService.class);
    var rules = mock(NotifyRuleRepository.class);
    var people = mock(NotifyRulePersonRepository.class);
    var users = mock(SystemUserRepository.class);
    var service =
        new ReminderService(
            repo, notifications, new ObjectMapper(), policies, rules, people, users);
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
    var policy = new NotifyPolicy();
    policy.setId(1L);
    policy.setRowVersion(1);
    policy.setEffectiveAt(now.minusDays(1));
    when(policies.effectivePolicy(10001, "repair", null)).thenReturn(policy);
    var rule = new NotifyRule();
    rule.setId(2L);
    rule.setScene("WAIT_DISPATCH");
    rule.setOffsetValue(30);
    rule.setOffsetUnit("MINUTE");
    rule.setEnabled(1);
    rule.setEffectiveAt(now.minusDays(1));
    when(rules.list(10001, 1)).thenReturn(List.of(rule));
    when(people.list(10001, 1))
        .thenReturn(List.of(new NotificationViews.Person("2", "3", "TO", "审批人")));
    when(users.activeUser(10001, 3)).thenReturn(new SystemUser());
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
