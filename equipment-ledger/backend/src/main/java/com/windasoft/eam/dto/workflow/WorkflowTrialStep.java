package com.windasoft.eam.dto.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 节点、分支与脚本试算记录的固定可选字段，缺失字段保持旧协议省略。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowTrialStep(
    String node, String name, String type, String branch, Boolean matched, String value) {
  public static WorkflowTrialStep node(String key, String name, String type) {
    return new WorkflowTrialStep(key, name, type, null, null, null);
  }

  public static WorkflowTrialStep branch(String key, String name, boolean matched) {
    return new WorkflowTrialStep(null, name, null, key, matched, null);
  }

  public static WorkflowTrialStep script(String key, String name, String value) {
    return new WorkflowTrialStep(key, name, "SCRIPT_RESULT", null, null, value);
  }
}
