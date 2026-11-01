package com.windasoft.eam.domain.experience;

/**
 * 异常事项持久化状态，独立于维修工单和异常处理计划状态。
 * 维持既有中文数据库值，不通过枚举 ordinal 保存。
 */
public enum AnomalyStatus {
  PENDING("待处理"), ARRANGED("已安排"), IN_PROGRESS("处理中"),
  RESOLVED("已消除"), VOIDED("已作废");

  private final String code;

  AnomalyStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
