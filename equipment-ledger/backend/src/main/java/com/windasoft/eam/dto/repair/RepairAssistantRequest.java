package com.windasoft.eam.dto.repair;

import com.windasoft.eam.dto.knowledge.AiBusinessCatalog;
import com.windasoft.eam.dto.knowledge.AiBusinessPlan;
import com.windasoft.eam.dto.knowledge.AiQueryLimits;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 内部只读查询计划，不允许传入SQL或自定授权范围；日期为含首尾的自然日。 */
public record RepairAssistantRequest(
    AiBusinessPlan plan,
    Long equipmentId,
    int page,
    int size,
    LocalDateTime evaluatedAt,
    LocalDate start,
    LocalDate end) {
  public RepairAssistantRequest {
    if (plan == null
        || !AiBusinessCatalog.REPAIR_DOMAIN.equals(plan.domain())
        || !AiQueryLimits.acceptsPage(page, size)
        || evaluatedAt == null
        || (start == null) != (end == null)
        || (start != null && (start.isAfter(end) || plan.timeField().isBlank()))) {
      throw new IllegalArgumentException("维修查询计划无效");
    }
  }
}
