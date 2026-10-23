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

  @Test void expiredStatusDoesNotErasePreviousExecution() {
    for (String result : List.of("{\"startedAt\":\"2026-10-23T08:00:00\"}",
        "{\"finishedAt\":\"2026-10-23T08:01:00\"}", "{\"items\":[{\"id\":\"check\"}]}",
        "{\"scannedEquipmentIds\":[\"1\"]}"))
      assertFalse(access.unexecutedDailyTask(Map.of("status", "已过期", "result", result)), result);
  }
}
