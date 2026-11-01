package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.inspection.InspectionTaskAction;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import org.junit.jupiter.api.Test;

/** 点巡检动作与状态类型化必须保持原有 API/数据库编码，包含日常和周期任务使用的状态。 */
class InspectionTaskCodesTest {
  @Test
  void keepsExistingTaskStatuses() {
    assertEquals("未开始", InspectionTaskStatus.NOT_STARTED.code());
    assertEquals("待执行", InspectionTaskStatus.PENDING.code());
    assertEquals("已过期", InspectionTaskStatus.EXPIRED.code());
    assertEquals("执行中", InspectionTaskStatus.IN_PROGRESS.code());
    assertEquals("挂起", InspectionTaskStatus.SUSPENDED.code());
    assertEquals("待审核", InspectionTaskStatus.PENDING_REVIEW.code());
    assertEquals("审核不通过", InspectionTaskStatus.REVIEW_REJECTED.code());
    assertEquals("已审核", InspectionTaskStatus.REVIEWED.code());
    assertEquals("已完成", InspectionTaskStatus.COMPLETED.code());
    assertEquals("已作废", InspectionTaskStatus.CANCELLED.code());
    assertEquals("过期作废", InspectionTaskStatus.EXPIRED_CANCELLED.code());
    assertEquals("已免检", InspectionTaskStatus.EXEMPT.code());
  }

  @Test
  void parsesAllExistingActionsAndRejectsUnknownActions() {
    for (var action : InspectionTaskAction.values())
      assertEquals(action, InspectionTaskAction.parse(action.code()));
    assertEquals(InspectionTaskAction.AMEND_RESULT, InspectionTaskAction.parse("amend-result"));
    assertThrows(LocalizedArgumentException.class, () -> InspectionTaskAction.parse(null));
    assertThrows(LocalizedArgumentException.class, () -> InspectionTaskAction.parse("unknown"));
  }
}
