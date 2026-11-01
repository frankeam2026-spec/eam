package com.windasoft.eam.domain.repair;

/** 维修工单状态；编码与已有数据库、接口中的中文值保持一致。 */
public enum RepairOrderStatus {
  PENDING_SUBMISSION("待提交"), PENDING_APPROVAL("审批中"),
  PENDING_DISPATCH("待派工"), PENDING_CLAIM("待接单"),
  PROCESSING("处理中"), SUSPENDED("挂起"),
  PENDING_ACCEPTANCE("待验收"), COMPLETED("已完成"), CANCELLED("已撤销");

  private final String code;
  RepairOrderStatus(String code) { this.code = code; }
  public String code() { return code; }
}
