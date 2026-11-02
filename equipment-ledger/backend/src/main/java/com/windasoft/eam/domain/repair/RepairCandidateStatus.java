package com.windasoft.eam.domain.repair;

/** 派工候选人的接单结果，不与工单状态或通知类型混用。 编码保持历史存储与接口兼容。 */
public enum RepairCandidateStatus {
  PENDING_CLAIM("待接单"),
  CLAIMED("已接单"),
  CLAIMED_BY_OTHER("已由他人接单");
  private final String code;

  RepairCandidateStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
