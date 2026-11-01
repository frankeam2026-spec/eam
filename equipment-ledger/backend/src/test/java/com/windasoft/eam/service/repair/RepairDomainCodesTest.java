package com.windasoft.eam.service.repair;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.domain.repair.*;
import org.junit.jupiter.api.Test;

/** 防止领域类型化时意外改变现有数据库状态或前端动作编码。 */
class RepairDomainCodesTest {
  @Test
  void persistsExistingRepairStatusAndApprovalGateCodes() {
    assertEquals("待派工", RepairOrderStatus.PENDING_DISPATCH.code());
    assertEquals("处理中", RepairOrderStatus.PROCESSING.code());
    assertEquals("待验收", RepairOrderStatus.PENDING_ACCEPTANCE.code());
    assertEquals("待提交", RepairOrderStatus.PENDING_SUBMISSION.code());
    assertEquals("REQUIRED", RepairApprovalGate.REQUIRED.code());
    assertEquals("EXEMPT", RepairApprovalGate.EXEMPT.code());
    assertEquals("LEGACY", RepairApprovalGate.LEGACY.code());
  }

  @Test
  void preservesWorkflowActionAndQueryParameters() {
    assertEquals(RepairWorkflowAction.CANCEL_MATERIAL,
        RepairWorkflowAction.parse("cancelMaterial"));
    assertEquals(RepairWorkflowAction.DISPATCH,
        RepairWorkflowAction.parse("dispatch"));
    assertEquals(RepairWorkflowAction.TRANSFER,
        RepairWorkflowAction.parse("transfer"));
    assertEquals(RepairQueue.ACCEPT, RepairQueue.parse("accept"));
    assertEquals(RepairQueue.ALL, RepairQueue.parse("all"));
    assertThrows(IllegalArgumentException.class, () -> RepairWorkflowAction.parse("invalid"));
    assertThrows(IllegalArgumentException.class, () -> RepairQueue.parse("invalid"));
  }

  @Test
  void keepsRepairNoticeAndMethodCodesStable() {
    assertEquals("报修待修改", RepairNoticeKind.REPORT_REVISION.code());
    assertEquals("报修审批通过", RepairNoticeKind.APPROVAL_APPROVED.code());
    assertEquals("自修", RepairMethod.INTERNAL.code());
    assertEquals("外协", RepairMethod.EXTERNAL.code());
  }
}
