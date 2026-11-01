package com.windasoft.eam.domain.repair;

import java.util.Arrays;

/** 维修列表队列筛选；编码为前端既有 query 参数，不是工单状态。 */
public enum RepairQueue {
  PENDING("pending"), OWNED("owned"), MINE("mine"),
  ACCEPT("accept"), CREATED("created"), DISPATCH("dispatch"), ALL("all");
  private final String code;
  RepairQueue(String code) { this.code = code; }
  public String code() { return code; }
  public static RepairQueue parse(String code) {
    return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst()
        .orElseThrow(() -> new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0008"));
  }
}
