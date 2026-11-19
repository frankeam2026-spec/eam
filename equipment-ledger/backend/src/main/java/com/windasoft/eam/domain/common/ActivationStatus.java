package com.windasoft.eam.domain.common;

/** 二态启停标记。持久化和写接口使用 0/1，旧列表与导出继续使用原中文状态值。 */
public enum ActivationStatus {
  ENABLED(1, "启用"),
  DISABLED(0, "停用");

  private final int code;
  private final String label;

  ActivationStatus(int code, String label) {
    this.code = code;
    this.label = label;
  }

  public int code() {
    return code;
  }

  public String label() {
    return label;
  }

  /** 读取时沿用旧口径：只有 1 表示启用；调用方仍按各接口契约校验写入值。 */
  public static ActivationStatus fromFlag(Integer flag) {
    return fromEnabled(isEnabled(flag));
  }

  public static boolean isEnabled(Integer flag) {
    return flag != null && flag == ENABLED.code;
  }

  public static ActivationStatus fromEnabled(boolean enabled) {
    return enabled ? ENABLED : DISABLED;
  }
}
