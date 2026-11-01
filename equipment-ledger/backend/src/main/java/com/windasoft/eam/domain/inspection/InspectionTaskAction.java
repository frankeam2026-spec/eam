package com.windasoft.eam.domain.inspection;

import java.util.Arrays;

/**
 * 点巡检任务操作的既有 API 编码，不改变 daily/periodic 任务适用的业务规则。
 * 每项操作能否执行仍由任务类别、权限和状态共同校验。
 */
public enum InspectionTaskAction {
  START("start"), SCAN("scan"), DRAFT("draft"), SUBMIT("submit"),
  AMEND_RESULT("amend-result"), REVIEW("review"), SUSPEND("suspend"),
  RESUME("resume"), TRANSFER("transfer"), EXEMPT("exempt"),
  VOID("void"), DELETE("delete");

  private final String code;

  InspectionTaskAction(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /** 未知动作按原接口错误处理，不能默认为提交或删除。 */
  public static InspectionTaskAction parse(String code) {
    return Arrays.stream(values())
        .filter(action -> action.code.equals(code))
        .findFirst()
        .orElseThrow(
            () -> new com.windasoft.eam.common.LocalizedArgumentException(
                "server.inspection.message0011"));
  }
}
