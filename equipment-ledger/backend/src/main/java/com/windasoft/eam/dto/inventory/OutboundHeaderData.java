package com.windasoft.eam.dto.inventory;

import java.math.BigDecimal;
import java.util.List;

/** Transport only: values are stored in named columns or selection rows. */
public record OutboundHeaderData(
    String purpose,
    String remark,
    Long departmentId,
    Long recipientId,
    String recipientName,
    String taskKind,
    Long taskId,
    String legacyFingerprint,
    List<List<Selection>> lineSelections) {
  public record Selection(Long balanceId, BigDecimal quantity) {}
}
