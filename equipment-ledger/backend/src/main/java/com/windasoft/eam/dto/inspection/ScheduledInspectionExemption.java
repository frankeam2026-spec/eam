package com.windasoft.eam.dto.inspection;

import java.time.LocalDateTime;

/** 已批准免检的只读投影，用于判断任务发生及复制批准依据，不承担免检申请写入。 */
public record ScheduledInspectionExemption(
    long equipmentId,
    LocalDateTime startAt,
    LocalDateTime endAt,
    String reason,
    long approvedBy,
    LocalDateTime approvedAt) {}
