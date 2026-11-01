package com.windasoft.eam.domain.repair;

/**
 * 维修待办及消息的既有 kind 编码，供消息落库与前端兼容显示。
 * 不与工单状态或持久化审计事件标识混用。
 */
public enum RepairNoticeKind {
  APPROVAL_APPROVED("报修审批通过"), REPORT_REVISION("报修待修改"),
  PENDING_CLAIM("待接单"), PENDING_PROCESS("待处理"), CLAIMED("已接单"),
  PENDING_ACCEPTANCE("待验收"), ACCEPTED("验收通过"),
  ACCEPTANCE_RETURNED("验收打回"), PENDING_ISSUE("待出库");
  private final String code;
  RepairNoticeKind(String code) { this.code = code; }
  public String code() { return code; }
}
