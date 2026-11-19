package com.windasoft.eam.dto.inventory;

/** 任务和人员选择器的既有字段，ID保持字符串以避免浏览器丢失精度。 */
public final class InventorySelectorRow {
  private InventorySelectorRow() {}

  public record Task(String id, String kind, String code, String name, String companyId) {}

  public record User(String id, String name, String account, String department, String companyId) {}
}
