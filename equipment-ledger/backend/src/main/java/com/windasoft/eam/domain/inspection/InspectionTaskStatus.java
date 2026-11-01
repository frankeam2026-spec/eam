package com.windasoft.eam.domain.inspection;

/**
 * 点巡检任务状态；编码沿用既有数据库中文值。
 * 不将任务状态与审核决定、通知类型或检查结果混为一谈。
 */
public enum InspectionTaskStatus {
  NOT_STARTED("未开始"), PENDING("待执行"), EXPIRED("已过期"),
  IN_PROGRESS("执行中"), SUSPENDED("挂起"), PENDING_REVIEW("待审核"),
  REVIEW_REJECTED("审核不通过"), REVIEWED("已审核"),
  COMPLETED("已完成"), CANCELLED("已作废"),
  EXPIRED_CANCELLED("过期作废"), EXEMPT("已免检");

  private final String code;

  InspectionTaskStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
