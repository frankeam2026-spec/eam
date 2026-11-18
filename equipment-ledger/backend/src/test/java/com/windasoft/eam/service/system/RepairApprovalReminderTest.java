package com.windasoft.eam.service.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.NotificationViews;
import com.windasoft.eam.dto.system.PlanReminderBinding;
import com.windasoft.eam.entity.system.NotifyPolicy;
import com.windasoft.eam.entity.system.NotifyRule;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.system.*;
import java.time.*;
import java.util.*;
import java.util.stream.LongStream;
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
    var recipient = new SystemUser();
    recipient.setId(3L);
    when(users.activeIdentities(10001, List.of(3L))).thenReturn(List.of(recipient));
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

  @Test
  void sharedRecipientsAndPlanAreReadOnceAcrossRules() {
    var repo = mock(ReminderRepository.class);
    var notifications = mock(NotificationRepository.class);
    var policies = mock(NotificationPolicyService.class);
    var rules = mock(NotifyRuleRepository.class);
    var people = mock(NotifyRulePersonRepository.class);
    var users = mock(SystemUserRepository.class);
    var service =
        new ReminderService(
            repo, notifications, new ObjectMapper(), policies, rules, people, users);
    var due = LocalDateTime.of(2020, 1, 2, 0, 0);
    var binding = new PlanReminderBinding("DEFAULT", null, due.minusDays(1), null);
    when(repo.source("inspection", 7, 9, true))
        .thenReturn(
            Map.of(
                "kind", "daily", "plan_id", 8L, "status", "已过期", "deleted", 0, "expires_at", due));
    when(policies.planBinding(7, "daily", 8)).thenReturn(binding);
    var policy = new NotifyPolicy();
    policy.setId(10L);
    policy.setRowVersion(2);
    policy.setEffectiveAt(due.minusDays(2));
    when(policies.effectivePolicy(7, "daily", binding)).thenReturn(policy);
    var first = overdueRule(20, due);
    var second = overdueRule(21, due);
    when(rules.list(7, 10)).thenReturn(List.of(first, second));
    var ids = LongStream.rangeClosed(100, 139).boxed().toList();
    var recipients = new ArrayList<NotificationViews.Person>();
    for (long rule : List.of(20L, 21L))
      for (long user : ids)
        recipients.add(
            new NotificationViews.Person(Long.toString(rule), Long.toString(user), "TO", "测试人员"));
    when(people.list(7, 10)).thenReturn(recipients);
    var active = new SystemUser();
    active.setId(100L);
    when(users.activeIdentities(7, ids)).thenReturn(List.of(active));
    service.scan("inspection", 7, 9);
    verify(policies).planBinding(7, "daily", 8);
    verify(users).activeIdentities(7, ids);
    verifyNoMoreInteractions(users);
    verify(notifications).deliveries(7, "reminder:inspection:9:20:100");
    verify(notifications).deliveries(7, "reminder:inspection:9:21:100");
    verifyNoMoreInteractions(notifications);
  }

  private NotifyRule overdueRule(long id, LocalDateTime due) {
    var rule = new NotifyRule();
    rule.setId(id);
    rule.setScene("AFTER_DUE");
    rule.setEnabled(1);
    rule.setOffsetValue(1);
    rule.setOffsetUnit("MINUTE");
    rule.setEffectiveAt(due.minusDays(1));
    return rule;
  }
}
