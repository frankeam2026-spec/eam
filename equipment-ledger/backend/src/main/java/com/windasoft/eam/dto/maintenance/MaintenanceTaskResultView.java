package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** 无执行记录时仍输出{}；已有记录的日期缺失沿用空串及六位小数秒，不改变历史HTTP协议。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MaintenanceTaskResultView(
    String description,
    String startedAt,
    String startAt,
    String endAt,
    List<MaintenanceExecutionResult.Item> items,
    List<MaintenanceFileReference> attachments,
    Acceptance acceptance,
    List<String> collaborators) {
  public record Acceptance(
      String decision,
      String description,
      BigDecimal score,
      String reviewerId,
      String reviewer,
      String reviewedAt,
      List<MaintenanceFileReference> attachments) {}
}
