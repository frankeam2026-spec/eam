package com.windasoft.eam.domain.repair;

/**
 * 维修事件审计中历史沿用的中文标签。
 * 审计标签和待办 kind 是不同字段，分别维护以保护历史记录和通知协议。
 */
public enum RepairEventLabel {
  APPROVAL_APPROVED("报修审批通过"),
  APPROVAL_RESUBMITTED("重新提交报修审批");

  private final String code;
  RepairEventLabel(String code) { this.code = code; }
  public String code() { return code; }
}
