package com.windasoft.eam.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.windasoft.eam.dto.inventory.RequisitionStatus;
import org.junit.jupiter.api.Test;

/** 领料状态类型化后必须与既有数据库和 API 编码保持一致。 */
class RequisitionStatusCodesTest {
  @Test
  void keepsApprovalAndFulfillmentCodesUnchanged() {
    assertEquals("审批中", RequisitionStatus.APPROVING.value());
    assertEquals("待预留", RequisitionStatus.AWAITING_RESERVATION.value());
    assertEquals("部分预留", RequisitionStatus.PARTIALLY_RESERVED.value());
    assertEquals("已预留", RequisitionStatus.RESERVED.value());
    assertEquals("部分发料", RequisitionStatus.PARTIALLY_ISSUED.value());
    assertEquals("已完成", RequisitionStatus.COMPLETED.value());
  }
}
