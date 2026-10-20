package com.windasoft.eam.service.business;

import java.util.*;

/** Registered business objects: identifiers and permissions never come from client input. */
public final class BusinessObjects {
  private BusinessObjects() {}

  public record Definition(String name, String menu, List<String> actions) {}

  public static final Map<String, Definition> ALL;

  static {
    var objects = new LinkedHashMap<String, Definition>();
    objects.put("equipment", new Definition("设备台账", "equipment", List.of("查看", "新增", "编辑")));
    objects.put("repair_report", new Definition("维修报修", "repair", List.of("查看", "报修", "编辑")));
    objects.put("repair_process", new Definition("维修处理", "repair", List.of("查看", "处理")));
    objects.put(
        "inventory_outbound",
        new Definition("出库单", "inventoryOutbound", List.of("查看", "新增直接出库", "编辑草稿", "确认出库")));
    objects.put(
        "inventory_receipt", new Definition("入库单", "inventoryReceipt", List.of("查看", "新增", "编辑")));
    objects.put(
        "maintenance_plan", new Definition("保养计划", "maintenancePlan", List.of("查看", "新增", "编辑")));
    objects.put("maintenance_task", new Definition("保养任务", "maintenanceTask", List.of("查看", "处理")));
    objects.put(
        "inspection_daily_plan",
        new Definition("点检计划", "inspectionDailyPlan", List.of("查看", "新增", "编辑")));
    objects.put(
        "inspection_daily_task",
        new Definition("点检任务", "inspectionDailyTask", List.of("查看", "执行")));
    objects.put(
        "inspection_periodic_plan",
        new Definition("巡检计划", "inspectionPeriodicPlan", List.of("查看", "新增", "编辑")));
    objects.put(
        "inspection_periodic_task",
        new Definition("巡检任务", "inspectionPeriodicTask", List.of("查看", "执行")));
    ALL = Collections.unmodifiableMap(objects);
  }

  public static Definition get(String key) {
    var object = ALL.get(key);
    if (object == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0006");
    return object;
  }

  public static String inspection(String kind) {
    return switch (kind) {
      case "daily-plans" -> "inspection_daily_plan";
      case "periodic-plans" -> "inspection_periodic_plan";
      case "daily", "daily-tasks" -> "inspection_daily_task";
      case "periodic", "periodic-tasks" -> "inspection_periodic_task";
      default -> null;
    };
  }
}
