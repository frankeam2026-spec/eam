package com.windasoft.eam.domain.repair;

/** 旧维修领料状态，仅用于历史领料兼容路径。 编码保持历史存储与接口兼容。 */
public enum LegacyRepairMaterialStatus {
  PENDING_ISSUE("待出库"),
  ISSUED("已出库"),
  WITHDRAWN("已撤回");
  private final String code;

  LegacyRepairMaterialStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
