package com.windasoft.eam.service.maintenance;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.maintenance.MaintenanceTaskAction;
import com.windasoft.eam.domain.maintenance.MaintenanceTaskStatus;
import org.junit.jupiter.api.Test;

/** 保护既有保养任务的接口动作和数据库状态编码，防止枚举化改变协议。 */
class MaintenanceTaskCodesTest {
  @Test
  void keepsPersistedTaskStatusCodes() {
    assertEquals("待执行", MaintenanceTaskStatus.PENDING.code());
    assertEquals("执行中", MaintenanceTaskStatus.IN_PROGRESS.code());
    assertEquals("待验收", MaintenanceTaskStatus.PENDING_ACCEPTANCE.code());
    assertEquals("挂起", MaintenanceTaskStatus.SUSPENDED.code());
    assertEquals("已完成", MaintenanceTaskStatus.COMPLETED.code());
    assertEquals("已作废", MaintenanceTaskStatus.CANCELLED.code());
    assertEquals("过期作废", MaintenanceTaskStatus.EXPIRED_CANCELLED.code());
  }

  @Test
  void parsesExistingActionsAndRejectsUnknownAction() {
    for (var action : MaintenanceTaskAction.values()) {
      assertEquals(action, MaintenanceTaskAction.parse(action.code()));
    }
    assertThrows(LocalizedArgumentException.class, () -> MaintenanceTaskAction.parse(null));
    assertThrows(LocalizedArgumentException.class, () -> MaintenanceTaskAction.parse("unknown"));
  }
}
