package com.windasoft.eam.domain.maintenance;

/** 保养任务生命周期状态；中文编码为既有数据库及接口契约，不使用枚举序号持久化。 */
public enum MaintenanceTaskStatus {
  PENDING("待执行"),
  IN_PROGRESS("执行中"),
  PENDING_ACCEPTANCE("待验收"),
  SUSPENDED("挂起"),
  COMPLETED("已完成"),
  CANCELLED("已作废"),
  EXPIRED_CANCELLED("过期作废");

  private final String code;
  MaintenanceTaskStatus(String code) { this.code = code; }
  public String code() { return code; }
}
