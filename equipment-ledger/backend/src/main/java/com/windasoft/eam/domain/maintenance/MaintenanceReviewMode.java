package com.windasoft.eam.domain.maintenance;

/** 保养整机验收方式；未知历史值仍由原业务入口按需验收处理。 */
public enum MaintenanceReviewMode {
  NONE("无需验收"),
  EQUIPMENT("整机验收");
  private final String code;

  MaintenanceReviewMode(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
