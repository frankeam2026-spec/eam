package com.windasoft.eam.dto.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 业务字段能力目录，不接受模型提供数据库列名或表达式。 */
public final class AiBusinessCatalog {
  private AiBusinessCatalog() {}

  public record Field(String label, String type, String identity) {}

  public record Domain(
      String label,
      String menu,
      String route,
      String timeField,
      Map<String, Field> fields,
      List<String> columns) {}

  public static final Map<String, Domain> ALL;

  static {
    var domains = new LinkedHashMap<String, Domain>();
    var equipment = new LinkedHashMap<String, Field>();
    for (var field : com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Field.values()) {
      equipment.put(
          field.key(),
          new Field(
              field.label(),
              field.kind().name(),
              field.identity() == null ? "" : field.identity().key()));
    }
    domains.put(
        "equipment",
        new Domain(
            "设备台账",
            "equipment",
            "/equipment/%s/history",
            "createdAt",
            equipment,
            com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.LIST_FIELDS.stream()
                .map(com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Field::key)
                .toList()));
    var repair =
        fields(
            "id:ID:ID,code:工单编号:TEXT,equipmentCode:设备编号:TEXT,equipmentName:设备名称:TEXT,equipmentId:设备ID:ID,status:工单状态:TEXT,severity:紧急程度:TEXT,department:部门:TEXT,departmentId:部门ID:ID,company:公司:TEXT,companyId:公司ID:ID,owner:维修负责人:TEXT,ownerId:负责人ID:ID,reporter:报修人:TEXT,reporterId:报修人ID:ID,faultType:故障类型:TEXT,faultTypeId:类型ID:ID,faultCause:故障原因:TEXT,faultCauseId:原因ID:ID,reportedAt:报修时间:DATETIME,claimedAt:接单时间:DATETIME,submittedAt:提交解决时间:DATETIME,acceptedAt:验收完成时间:DATETIME,downtimeMinutes:人工记录停机分钟:DISPLAY");
    domains.put(
        "repair",
        new Domain(
            "维修工单（数量为工单数，不等于独立故障事件数）",
            "repair",
            "/repairs/%s",
            "reportedAt",
            repair,
            List.of("code", "equipmentName", "status", "department", "owner", "reportedAt")));
    for (String kind : List.of("maintenanceTask", "dailyTask", "periodicTask")) {
      var task =
          fields(
              "id:ID:ID,code:任务编号:TEXT,department:计划所属部门:TEXT,departmentId:部门ID:ID,createdAt:生成时间:DATETIME,equipmentName:设备名称:TEXT,planName:计划名称:TEXT,planId:计划ID:ID,equipmentId:设备ID:ID,owner:当前负责人:TEXT,ownerId:负责人ID:ID,company:公司:TEXT,companyId:公司ID:ID,status:任务状态:TEXT");
      boolean maintenance = kind.equals("maintenanceTask");
      task.putAll(
          maintenance
              ? fields(
                  "equipmentCode:设备编号:TEXT,dueDate:计划保养日:DATE,expiresDate:最后有效日期:DATE,actualStartAt:实际开始时间:DATETIME,actualEndAt:实际结束时间:DATETIME")
              : fields(
                  "shiftName:班次:TEXT,dueAt:计划开始时间:DATETIME,expiresAt:截止时间:DATETIME,startedAt:实际开始时间:DATETIME,finishedAt:实际完成时间:DATETIME,abnormal:异常项目数:NUMBER,checked:已检项目数:NUMBER,missing:未检项目数:NUMBER,expected:应检项目数:NUMBER"));
      domains.put(
          kind,
          new Domain(
              maintenance ? "保养任务" : kind.equals("dailyTask") ? "日常点检任务" : "周期巡检任务",
              maintenance
                  ? "maintenanceTask"
                  : kind.equals("dailyTask") ? "inspectionDailyTask" : "inspectionPeriodicTask",
              maintenance ? "/maintenance/task/%s" : "/inspection/task/%s",
              maintenance ? "dueDate" : "dueAt",
              task,
              maintenance
                  ? List.of("code", "equipmentName", "status", "department", "owner", "dueDate")
                  : List.of("code", "planName", "equipmentName", "shiftName", "status", "dueAt")));
    }
    for (String kind : List.of("maintenancePlan", "dailyPlan", "periodicPlan")) {
      domains.put(
          kind,
          new Domain(
              kind.equals("maintenancePlan")
                  ? "保养计划"
                  : kind.equals("dailyPlan") ? "日常点检计划" : "周期巡检计划",
              kind.equals("maintenancePlan")
                  ? "maintenancePlan"
                  : kind.equals("dailyPlan") ? "inspectionDailyPlan" : "inspectionPeriodicPlan",
              kind.equals("maintenancePlan") ? "/maintenance/plans/%s" : "",
              "createdAt",
              fields(
                  "id:ID:ID,code:计划编号:TEXT,name:计划名称:TEXT,department:所属部门:TEXT,departmentId:部门ID:ID,status:启用状态:TEXT,createdAt:创建时间:DATETIME"),
              List.of("code", "name", "status", "department", "createdAt")));
    }
    domains.put(
        "stock",
        new Domain(
            "库存余额（COUNT为余额记录数；数量汇总按单位分开）",
            "inventoryQuery",
            "",
            "",
            fields(
                "id:ID:ID,code:物料编号:TEXT,name:物料名称:TEXT,model:规格型号:TEXT,unit:计量单位:TEXT,company:公司:TEXT,companyId:公司ID:ID,warehouse:仓库:TEXT,warehouseId:仓库ID:ID,bin:库位:TEXT,batch:批次:TEXT,status:库存状态:TEXT,quantity:现存数量:NUMBER,reserved:预留数量:NUMBER,available:可用数量:NUMBER,expiryDate:有效期:DATE"),
            List.of("code", "name", "warehouse", "quantity", "reserved", "available", "unit")));
    for (var domain : domains.values()) {
      for (String key : List.of("equipmentName", "equipmentCode", "planName")) {
        String identity = key.startsWith("equipment") ? "equipmentId" : "planId";
        if (domain.fields().containsKey(key) && domain.fields().containsKey(identity)) {
          var field = domain.fields().get(key);
          domain.fields().put(key, new Field(field.label(), field.type(), identity));
        }
      }
    }
    var stockName = domains.get("stock").fields().get("name");
    domains
        .get("stock")
        .fields()
        .put("name", new Field(stockName.label(), stockName.type(), "code"));
    domains.replaceAll(
        (key, domain) ->
            new Domain(
                domain.label(),
                domain.menu(),
                domain.route(),
                domain.timeField(),
                Collections.unmodifiableMap(domain.fields()),
                domain.columns()));
    ALL = Collections.unmodifiableMap(domains);
  }

  private static LinkedHashMap<String, Field> fields(String declaration) {
    var result = new LinkedHashMap<String, Field>();
    for (String part : declaration.split(",")) {
      String[] f = part.split(":");
      result.put(f[0], new Field(f[1], f[2], ""));
    }
    for (String name : new ArrayList<>(result.keySet()))
      if (result.containsKey(name + "Id")) {
        var f = result.get(name);
        result.put(name, new Field(f.label(), f.type(), name + "Id"));
      }
    if (result.containsKey("name") && result.containsKey("id")) {
      var f = result.get("name");
      result.put("name", new Field(f.label(), f.type(), "id"));
    }
    return result;
  }
}
