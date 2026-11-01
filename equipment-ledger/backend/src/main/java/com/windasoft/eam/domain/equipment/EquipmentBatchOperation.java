package com.windasoft.eam.domain.equipment;

import com.windasoft.eam.common.LocalizedArgumentException;
import java.util.Arrays;

/**
 * 设备批量操作的固定接口编码，不是设备的运行状态或生命周期状态。
 * 保留已有 location/category/department/owner/state/delete 请求值。
 */
public enum EquipmentBatchOperation {
  LOCATION("location"), CATEGORY("category"), DEPARTMENT("department"),
  OWNER("owner"), STATE("state"), DELETE("delete");

  private final String code;

  EquipmentBatchOperation(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /** 拒绝未知操作，避免非法输入在批量写入中产生部分修改。 */
  public static EquipmentBatchOperation parse(String code) {
    return Arrays.stream(values())
        .filter(operation -> operation.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new LocalizedArgumentException("server.equipment.message0039"));
  }
}
