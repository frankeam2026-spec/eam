package com.windasoft.eam.domain.inspection;

import java.util.Set;

/**
 * 点巡检任务状态；编码沿用既有数据库中文值。
 * 不将任务状态与审核决定、通知类型或检查结果混为一谈。
 */
public enum InspectionTaskStatus {
  NOT_STARTED("未开始"), PENDING("待执行"), EXPIRED("已过期"),
  IN_PROGRESS("执行中"), SUSPENDED("挂起"), PENDING_REVIEW("待审核"),
  REVIEW_REJECTED("审核不通过"), REVIEWED("已审核"),
  COMPLETED("已完成"), CANCELLED("已作废"),
  EXPIRED_CANCELLED("过期作废"), EXEMPT("已免检");

  private static final Set<String> STARTABLE =
      Set.of(NOT_STARTED.code, PENDING.code, EXPIRED.code, REVIEW_REJECTED.code);
  private static final Set<String> TRANSFERABLE =
      Set.of(
          NOT_STARTED.code,
          PENDING.code,
          IN_PROGRESS.code,
          SUSPENDED.code,
          EXPIRED.code,
          REVIEW_REJECTED.code);
  private static final Set<String> TERMINAL =
      Set.of(COMPLETED.code, REVIEWED.code, CANCELLED.code, EXPIRED_CANCELLED.code, EXEMPT.code);
  private static final Set<String> RESULT_AMENDABLE = Set.of(COMPLETED.code, REVIEWED.code);
  private static final Set<String> UNEXECUTED_DAILY_CANDIDATES =
      Set.of(NOT_STARTED.code, PENDING.code, EXPIRED.code);

  private final String code;

  InspectionTaskStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static boolean canStart(String status) {
    return status != null && STARTABLE.contains(status);
  }

  public static boolean canTransfer(String status) {
    return status != null && TRANSFERABLE.contains(status);
  }

  public static boolean isTerminal(String status) {
    return status != null && TERMINAL.contains(status);
  }

  public static boolean canAmendResult(String status) {
    return status != null && RESULT_AMENDABLE.contains(status);
  }

  /** Status candidates only; execution evidence is checked by the task access policy. */
  public static boolean isUnexecutedDailyCandidate(String status) {
    return status != null && UNEXECUTED_DAILY_CANDIDATES.contains(status);
  }

  /**
   * Whether an action is valid for the current status. This is status eligibility only;
   * tenant, task kind, ownership, permission, timing, and execution evidence remain separate checks.
   */
  public static boolean allows(InspectionTaskAction action, String status) {
    if (action == null || !isKnown(status)) return false;
    return switch (action) {
      case START -> canStart(status);
      case SCAN, DRAFT, SUBMIT, SUSPEND -> IN_PROGRESS.code.equals(status);
      case RESUME -> SUSPENDED.code.equals(status);
      case REVIEW -> PENDING_REVIEW.code.equals(status);
      case TRANSFER -> canTransfer(status);
      case EXEMPT -> isUnexecutedDailyCandidate(status);
      case AMEND_RESULT -> canAmendResult(status);
      case VOID -> !isTerminal(status);
      // Periodic deletion is allowed for terminal tasks; daily deletion has extra evidence checks.
      case DELETE -> true;
    };
  }

  private static boolean isKnown(String status) {
    if (status == null) return false;
    for (var candidate : values()) if (candidate.code.equals(status)) return true;
    return false;
  }
}
