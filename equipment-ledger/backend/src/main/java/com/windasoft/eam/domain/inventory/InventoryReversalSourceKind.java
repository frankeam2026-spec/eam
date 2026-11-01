package com.windasoft.eam.domain.inventory;

import com.windasoft.eam.common.LocalizedArgumentException;
import java.util.Arrays;

/**
 * 库存冲销单允许引用的原始单据类型。
 * receipt/issue 属于历史独立单据，其余类型来自统一库存操作视图；编码兼容现有接口与 JSON。
 */
public enum InventoryReversalSourceKind {
  RECEIPT("receipt"), ISSUE("issue"),
  RETURN("returns"), TRANSFER("transfers"), COUNT("counts"), OPENING("openings");

  private final String code;

  InventoryReversalSourceKind(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /** 未知来源不得降级为收货单或任意操作视图，防止冲销错误的原始单据。 */
  public static InventoryReversalSourceKind parse(String code) {
    return Arrays.stream(values())
        .filter(value -> value.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new LocalizedArgumentException("server.inventory.message0102"));
  }
}
