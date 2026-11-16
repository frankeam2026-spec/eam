package com.windasoft.eam.domain.maintenance;

/** 保养人员关系的稳定存储编码，角色范围由各业务用例决定。 */
public enum MaintenancePersonRole {
  OWNER("owner"),
  REVIEWER("reviewer"),
  COLLABORATOR("collaborator");
  private final String code;

  MaintenancePersonRole(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
