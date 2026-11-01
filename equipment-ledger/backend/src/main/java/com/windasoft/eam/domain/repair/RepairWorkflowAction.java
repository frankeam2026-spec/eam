package com.windasoft.eam.domain.repair;

import java.util.Arrays;

/** 维修工作流动作；保留原有请求编码和不允许执行的兼容动作。 */
public enum RepairWorkflowAction {
  DISPATCH("dispatch"), TRANSFER("transfer"), CLAIM("claim"), PROCESS("process"),
  SUSPEND("suspend"), RESUME("resume"), OUTSOURCE("outsource"),
  ACCEPT("accept"), REJECT("reject"), RETURN("return"),
  CANCEL_MATERIAL("cancelMaterial"), INSTALL("install"), REMOVE("remove"),
  MATERIAL("material"), ISSUE("issue"), CANCEL("cancel"), COMPLETE("complete");
  private final String code;
  RepairWorkflowAction(String code) { this.code = code; }
  public String code() { return code; }
  public static RepairWorkflowAction parse(String code) {
    return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst()
        .orElseThrow(() -> new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0025"));
  }
}
