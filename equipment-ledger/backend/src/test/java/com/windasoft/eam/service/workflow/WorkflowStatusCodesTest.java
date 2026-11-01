package com.windasoft.eam.service.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.windasoft.eam.domain.workflow.WorkflowApprovalStatus;
import com.windasoft.eam.domain.workflow.WorkflowInstanceStatus;
import org.junit.jupiter.api.Test;

/** 保证流程实例与审批待办的状态编码保持原协议，不靠枚举序号持久化。 */
class WorkflowStatusCodesTest {
  @Test
  void instanceStatusesMatchExistingDatabaseCodes() {
    assertEquals("RUNNING", WorkflowInstanceStatus.RUNNING.code());
    assertEquals("APPROVED", WorkflowInstanceStatus.APPROVED.code());
    assertEquals("REJECTED", WorkflowInstanceStatus.REJECTED.code());
    assertEquals("WITHDRAWN", WorkflowInstanceStatus.WITHDRAWN.code());
  }

  @Test
  void approvalStatusesRemainDistinctFromInstanceStates() {
    assertEquals("PENDING", WorkflowApprovalStatus.PENDING.code());
    assertEquals("APPROVED", WorkflowApprovalStatus.APPROVED.code());
    assertEquals("REJECTED", WorkflowApprovalStatus.REJECTED.code());
  }
}
