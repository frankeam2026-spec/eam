package com.windasoft.eam.dto.repair;

import java.time.LocalDateTime;
import java.util.List;

/** 助手仅使用表格所需的字段，不携带维修表单或扩展字段。 */
public record RepairRecordPage(List<Row> data, long total, Criteria criteria, String dateField) {
  public record Row(
      String id,
      String orderNo,
      String equipmentName,
      String equipmentCode,
      String status,
      LocalDateTime reportedAt,
      LocalDateTime completedAt) {}

  public record Criteria(
      String from, String to, String relation, boolean completed, String equipmentId) {}
}
