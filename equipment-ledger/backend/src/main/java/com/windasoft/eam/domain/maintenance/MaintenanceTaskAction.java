package com.windasoft.eam.domain.maintenance;

import java.util.Arrays;

/**
 * 保养任务动作的现有 HTTP 请求编码；只表达用户发起的操作，不承担状态流转规则。
 * 不改变客户端已发送的 start/process/accept 等英文动作值。
 */
public enum MaintenanceTaskAction {
  START("start"), PROCESS("process"), ACCEPT("accept"), SUSPEND("suspend"),
  RESUME("resume"), TRANSFER("transfer"), VOID("void");

  private final String code;

  MaintenanceTaskAction(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /** 未知动作继续沿用现有参数错误码，不静默映射为其他动作。 */
  public static MaintenanceTaskAction parse(String code) {
    return Arrays.stream(values())
        .filter(value -> value.code.equals(code))
        .findFirst()
        .orElseThrow(
            () -> new com.windasoft.eam.common.LocalizedArgumentException(
                "server.inspection.message0011"));
  }
}
