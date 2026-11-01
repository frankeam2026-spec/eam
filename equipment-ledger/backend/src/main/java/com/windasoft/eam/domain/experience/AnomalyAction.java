package com.windasoft.eam.domain.experience;

import java.util.Arrays;

/**
 * 异常事项用户动作：直接消除、创建报修、关联现有工单或作废。
 * 用户权限动作与请求动作不是同一类型，不能混用。
 */
public enum AnomalyAction {
  RESOLVE("resolve"), REPORT("report"), LINK("link"), VOID("void");

  private final String code;

  AnomalyAction(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /** 未知动作保持既有非法参数错误，不自动执行任何变更。 */
  public static AnomalyAction parse(String code) {
    return Arrays.stream(values())
        .filter(action -> action.code.equals(code))
        .findFirst()
        .orElseThrow(
            () -> new com.windasoft.eam.common.LocalizedArgumentException(
                "server.experience.message0022"));
  }
}
