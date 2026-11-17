package com.windasoft.eam.dto.workflow;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/** 审批历史的固定响应；保留旧字段名、ID字符串与数据库毫秒时间。 */
public record WorkflowHistoryRound(
    @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
    int roundNo,
    String state,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Long versionId,
    long rowVersion,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    String templateName,
    List<Approval> approvals,
    List<Event> events,
    List<WorkflowDefinition.Node> nodes) {
  public record Approval(
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
      @JsonProperty("tenant_id") @JsonFormat(shape = JsonFormat.Shape.STRING) Long tenantId,
      @JsonProperty("instance_id") @JsonFormat(shape = JsonFormat.Shape.STRING) Long instanceId,
      @JsonProperty("node_key") String nodeKey,
      @JsonProperty("engine_task_id") String engineTaskId,
      @JsonProperty("user_id") @JsonFormat(shape = JsonFormat.Shape.STRING) Long userId,
      String state,
      String opinion,
      @JsonProperty("acted_at") LocalDateTime actedAt,
      @JsonProperty("user_name") String userName,
      @JsonProperty("node_name") String nodeName) {}

  public record Event(String type, String nodeKey, String description, LocalDateTime createdAt) {}
}
