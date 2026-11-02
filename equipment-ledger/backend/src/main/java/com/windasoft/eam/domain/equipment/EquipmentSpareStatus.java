package com.windasoft.eam.domain.equipment;

/** 设备历史备件安装状态，不代表库存可用状态。 编码保持历史存储与接口兼容。 */
public enum EquipmentSpareStatus {
  INSTALLED("在用"),
  REMOVED("已换下");
  private final String code;

  EquipmentSpareStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
