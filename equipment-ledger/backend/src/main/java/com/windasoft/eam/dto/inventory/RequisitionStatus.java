package com.windasoft.eam.dto.inventory;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/** Existing database/wire codes; enum ordinals are never persisted. */
public enum RequisitionStatus {
  DRAFT("草稿"),
  APPROVING("审批中"),
  AWAITING_RESERVATION("待预留"),
  PARTIALLY_RESERVED("部分预留"),
  RESERVED("已预留"),
  PARTIALLY_ISSUED("部分发料"),
  COMPLETED("已完成"),
  CANCELLED("已取消"),
  CLOSED("已关闭");
  @EnumValue private final String value;

  RequisitionStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }
}
