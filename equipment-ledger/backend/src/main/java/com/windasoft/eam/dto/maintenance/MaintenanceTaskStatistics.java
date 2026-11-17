package com.windasoft.eam.dto.maintenance;

import java.math.BigDecimal;
import java.util.List;

/** 预填结果不能证明实际提交；无法确认的历史完成记录沿用null计数和可靠性标记。 */
public record MaintenanceTaskStatistics(
    int total,
    Integer completed,
    Integer abnormal,
    BigDecimal completionRate,
    List<String> completedItemIds,
    List<String> abnormalItemIds,
    boolean submitted,
    boolean reliable) {}
