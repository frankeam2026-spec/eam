package com.windasoft.eam.domain.system;

/**
 * 常见授权菜单的既有权限键；非完整菜单注册表。
 * 动态扩展菜单和存量权限配置仍可以使用 PermissionService 的字符串入口。
 */
public enum PermissionMenu {
  REPAIR("repair"), ROLE("role"), USER("user"), EQUIPMENT("equipment"),
  INVENTORY_MATERIAL("inventoryMaterial"), INVENTORY_WAREHOUSE("inventoryWarehouse"),
  INVENTORY_RECEIPT("inventoryReceipt"), INVENTORY_OUTBOUND("inventoryOutbound"),
  INVENTORY_REQUISITION("inventoryRequisition"), INVENTORY_RETURN("inventoryReturn"),
  INVENTORY_TRANSFER("inventoryTransfer"), INVENTORY_COUNT("inventoryCount"),
  INVENTORY_OPENING("inventoryOpening"), INVENTORY_REVERSAL("inventoryReversal"),
  INVENTORY_QUERY("inventoryQuery"), MAINTENANCE_TASK("maintenanceTask"),
  INSPECTION_DAILY_TASK("inspectionDailyTask"),
  INSPECTION_PERIODIC_TASK("inspectionPeriodicTask");

  private final String code;

  PermissionMenu(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
