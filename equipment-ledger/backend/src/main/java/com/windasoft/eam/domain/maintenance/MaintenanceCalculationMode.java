package com.windasoft.eam.domain.maintenance;

/** 保养下次日期计算口径；编码沿用既有存储和接口，不以枚举序号持久化。 */
public enum MaintenanceCalculationMode {
  FIXED_INTERVAL("固定间隔"),
  COMPLETION("完成时间");
  private final String code;

  MaintenanceCalculationMode(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
