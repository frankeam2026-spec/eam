package com.windasoft.eam.domain.repair;

/** 报修审批门禁；LEGACY 只用于兼容历史工单，不作为新报修的审批配置。 */
public enum RepairApprovalGate {
  REQUIRED("REQUIRED"), EXEMPT("EXEMPT"), LEGACY("LEGACY");
  private final String code;
  RepairApprovalGate(String code) { this.code = code; }
  public String code() { return code; }
}
