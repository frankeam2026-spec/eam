package com.windasoft.eam.domain.maintenance;

/** 类型、标准、计划共有的定义字段边界；项目字段保留各自的数据库约束。 */
public final class MaintenanceDefinitionLimits {
  public static final int NAME_MAX_LENGTH = 150;
  public static final int CODE_MAX_LENGTH = 64;
  public static final int PERIOD_MAX_COUNT = 10000;

  private MaintenanceDefinitionLimits() {}
}
