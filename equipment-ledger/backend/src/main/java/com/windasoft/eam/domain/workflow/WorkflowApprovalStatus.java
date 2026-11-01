package com.windasoft.eam.domain.workflow;

/** 单个审批待办的决定状态，不代表整条流程实例是否终结。 */
public enum WorkflowApprovalStatus {
  PENDING("PENDING"), APPROVED("APPROVED"), REJECTED("REJECTED");

  private final String code;

  WorkflowApprovalStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
