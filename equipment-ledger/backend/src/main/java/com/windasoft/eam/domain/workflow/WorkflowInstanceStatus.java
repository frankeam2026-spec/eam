package com.windasoft.eam.domain.workflow;

/**
 * 审批流程实例的持久化状态；不与审批参与人任务的决定状态混用。
 * 保持现有英文编码以兼容流程引擎与数据库记录。
 */
public enum WorkflowInstanceStatus {
  RUNNING("RUNNING"), APPROVED("APPROVED"),
  REJECTED("REJECTED"), WITHDRAWN("WITHDRAWN");

  private final String code;

  WorkflowInstanceStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
