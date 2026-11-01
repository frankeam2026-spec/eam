package com.windasoft.eam.domain.repair;

/** 维修处理方式；保持原有存储值，不与工作流的委外动作混淆。 */
public enum RepairMethod {
  INTERNAL("自修"), EXTERNAL("外协");
  private final String code;
  RepairMethod(String code) { this.code = code; }
  public String code() { return code; }
}
