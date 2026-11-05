package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class InspectionTaskDeletePolicyTest {
  final InspectionTaskAccessService access = new InspectionTaskAccessService(
      new ObjectMapper(), mock(AuthContext.class), mock(PermissionService.class));

  @Test void onlyPendingStatesWithoutExecutionEvidenceCanBeDeleted() {
    for (String state : List.of("未开始", "待执行", "已过期"))
      assertTrue(access.unexecutedDailyTask(Map.of("status", state)), state);
    for (String state : List.of("执行中", "挂起", "待审核", "已完成", "已审核", "审核不通过", "已免检", "已作废", "过期作废"))
      assertFalse(access.unexecutedDailyTask(Map.of("status", state)), state);
  }

  @Test
  void exemptionRequiresTheSameNoExecutionEvidenceAsDeletion() {
    var auth = mock(AuthContext.class);
    var permissions = mock(PermissionService.class);
    when(permissions.inspection("daily", "免检"))
        .thenReturn(new com.windasoft.eam.dto.system.PermissionScope(true, null, Set.of(), false));
    var policy = new InspectionTaskAccessService(new ObjectMapper(), auth, permissions);
    var row = Map.<String, Object>of("kind", "daily", "status", "待执行");

    assertTrue(policy.canExempt(row, Map.of()));
    assertFalse(policy.canExempt(row, Map.of("startedAt", "2026-10-23T08:00:00")));
    assertFalse(policy.canExempt(row, Map.of("finishedAt", "2026-10-23T08:01:00")));
    assertFalse(policy.canExempt(row, Map.of("submittedAt", "2026-10-23T08:01:00")));
    assertFalse(policy.canExempt(row, Map.of("items", List.of(Map.of("id", "check")))));
    assertFalse(policy.canExempt(row, Map.of("scannedEquipmentIds", List.of("1"))));
    assertFalse(
        policy.canExempt(
            Map.of("kind", "daily", "status", "执行中"), Map.of()));
  }

  @Test void expiredStatusDoesNotErasePreviousExecution() {
    for (String result : List.of("{\"startedAt\":\"2026-10-23T08:00:00\"}",
        "{\"finishedAt\":\"2026-10-23T08:01:00\"}", "{\"items\":[{\"id\":\"check\"}]}",
        "{\"scannedEquipmentIds\":[\"1\"]}"))
      assertFalse(access.unexecutedDailyTask(Map.of("status", "已过期", "result", result)), result);
  }
}
