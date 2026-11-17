package com.windasoft.eam.dto.workflow;

import java.time.LocalDateTime;

/** 历史轮次查询投影；实例版本为BIGINT，不缩窄为int。 */
public record WorkflowHistoryHeader(
    long id,
    int roundNo,
    String state,
    long versionId,
    long rowVersion,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    String templateName) {}
