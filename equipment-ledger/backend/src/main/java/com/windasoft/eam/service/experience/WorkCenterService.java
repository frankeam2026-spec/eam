package com.windasoft.eam.service.experience;

import static com.windasoft.eam.common.experience.ExperienceSupport.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.service.inspection.InspectionService;
import com.windasoft.eam.service.maintenance.MaintenanceService;
import com.windasoft.eam.service.repair.RepairWorkflowService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.service.system.WorkInboxService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkCenterService {
  final com.windasoft.eam.repository.experience.WorkCenterRepository repository;
  final com.fasterxml.jackson.databind.ObjectMapper json;
  final AuthContext auth;
  final PermissionService permissions;
  final RepairWorkflowService workflow;
  final MaintenanceService maintenance;
  final InspectionService inspection;
  final WorkInboxService inbox;
  final MaterialCustodyService custody;
  final AnomalyService anomalies;

  public WorkCenterService(
      com.windasoft.eam.repository.experience.WorkCenterRepository repository,
      com.fasterxml.jackson.databind.ObjectMapper json,
      AuthContext auth,
      PermissionService permissions,
      RepairWorkflowService workflow,
      MaintenanceService maintenance,
      InspectionService inspection,
      WorkInboxService inbox,
      MaterialCustodyService custody,
      AnomalyService anomalies) {
    this.repository = repository;
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
    this.workflow = workflow;
    this.maintenance = maintenance;
    this.inspection = inspection;
    this.inbox = inbox;
    this.custody = custody;
    this.anomalies = anomalies;
  }

  public Object catalog() {
    var actions = permissions.menuActions();
    return Map.of(
        "data",
        HomeService.TYPES.stream()
            .filter(
                t ->
                    switch (t) {
                      case "load" -> permissions.allowed("workspace", "团队工作查看");
                      case "inventory" ->
                          actions.keySet().stream().anyMatch(k -> k.startsWith("inventory"));
                      case "anomalies" ->
                          actions.containsKey("anomaly")
                              || actions.containsKey("maintenanceTask")
                              || actions.containsKey("inspectionDailyTask")
                              || actions.containsKey("inspectionPeriodicTask");
                      default -> true;
                    })
            .sorted()
            .toList());
  }

  boolean accessible(Runnable action) {
    try {
      action.run();
      return true;
    } catch (ResponseStatusException denied) {
      if (denied.getStatusCode().value() != 403) throw denied;
      return false;
    }
  }

  List<Map<String, Object>> tasks(boolean team) {
    var out = new ArrayList<Map<String, Object>>();
    var q =
        new LambdaQueryWrapper<RepairOrder>()
            .eq(RepairOrder::getTenantId, auth.tenantId())
            .eq(RepairOrder::getDeleted, 0)
            .orderByDesc(RepairOrder::getReportedAt);
    if (!team)
      q.and(
          w ->
              w.eq(RepairOrder::getReporterUserId, auth.userId())
                  .or()
                  .eq(RepairOrder::getAssigneeUserId, auth.userId()));
    else if (permissions.allowed("repair", "查看")) permissions.filterRepair(q);
    else q.eq(RepairOrder::getId, -1);
    for (var r : repository.findRepairs(q)) {
      if (!accessible(() -> workflow.viewAllowed(r))) continue;
      var v = new LinkedHashMap<String, Object>();
      v.put("id", r.getId().toString());
      v.put("module", "repair");
      v.put("code", r.getOrderNo());
      v.put("name", r.getEquipmentName());
      v.put("status", r.getStatus());
      v.put("owner", Objects.toString(r.getAssignee(), ""));
      v.put("creatorId", Objects.toString(r.getReporterUserId(), ""));
      v.put("ownerId", Objects.toString(r.getAssigneeUserId(), ""));
      v.put("time", Objects.toString(r.getReportedAt(), ""));
      v.put("due", "");
      v.put("departmentId", Objects.toString(r.getDepartmentOrgId(), ""));
      v.put(
          "companyId",
          Objects.toString(permissions.companyOfOrganization(r.getDepartmentOrgId()), ""));
      v.put("url", "/repairs/" + r.getId());
      out.add(v);
    }
    append(out, maintenance.tasks(), "maintenance", team);
    append(out, inspection.tasks(), "inspection", team);
    return out;
  }

  void append(
      List<Map<String, Object>> out,
      List<Map<String, Object>> values,
      String module,
      boolean team) {
    for (var t : values) {
      String owner = text(t, "ownerId");
      var v = new LinkedHashMap<String, Object>();
      v.put("id", t.get("id"));
      v.put("module", module);
      v.put("code", t.get("code"));
      v.put("name", text(t, "equipmentName"));
      v.put("status", t.get("status"));
      var ownerRows =
          owner.isBlank()
              ? List.<Map<String, Object>>of()
              : repository.findUserName(auth.tenantId(), id(owner));
      v.put("owner", ownerRows.isEmpty() ? "" : ownerRows.getFirst().get("display_name"));
      v.put("ownerId", owner);
      var origin = repository.findTaskCreator(auth.tenantId(), id(t.get("id")), module);
      v.put(
          "creatorId",
          origin.isEmpty() ? "" : Objects.toString(origin.getFirst().get("creator_id"), ""));
      if (!team
          && !owner.equals(Long.toString(auth.userId()))
          && !text(v, "creatorId").equals(Long.toString(auth.userId()))
          && !contains(t.get("owners"), auth.userId())
          && !contains(t.get("reviewers"), auth.userId())) continue;
      v.put("due", text(t, module.equals("maintenance") ? "dueDate" : "dueAt"));
      v.put("time", text(t, "createdAt"));
      v.put("companyId", text(t, "companyId"));
      v.put("departmentId", text(t, "departmentId"));
      v.put("url", "/" + module + "/task/" + t.get("id"));
      out.add(v);
    }
  }

  boolean contains(Object x, long id) {
    return x instanceof List<?> l
        && l.stream().anyMatch(v -> v.toString().equals(Long.toString(id)));
  }

  public Object queue(
      String state,
      String module,
      String search,
      int page,
      int size,
      String status,
      String companyId,
      String departmentId) {
    check(Set.of("active", "created", "team", "today").contains(state), "未知工作队列");
    boolean team = state.equals("team");
    if (team) permissions.require("workspace", "团队工作查看");
    var source =
        tasks(team).stream()
            .filter(
                r ->
                    state.equals("created")
                        ? text(r, "creatorId").equals(Long.toString(auth.userId()))
                        : state.equals("today")
                            ? text(r, "due")
                                .startsWith(LocalDate.now(ZoneId.of("Asia/Shanghai")).toString())
                            : state.equals("active")
                                ? Set.of("处理中", "执行中", "挂起").contains(r.get("status"))
                                : !Set.of("已完成", "已撤销", "已作废", "已取消").contains(r.get("status")))
            .filter(
                r ->
                    search.isBlank()
                        || text(r, "code").contains(search)
                        || text(r, "name").contains(search)
                        || text(r, "owner").contains(search))
            .filter(r -> status.isBlank() || status.equals(text(r, "status")))
            .filter(r -> companyId.isBlank() || companyId.equals(text(r, "companyId")))
            .filter(r -> departmentId.isBlank() || departmentId.equals(text(r, "departmentId")))
            .sorted(Comparator.comparing((Map<String, Object> r) -> text(r, "time")).reversed())
            .toList();
    var counts = new LinkedHashMap<String, Long>();
    for (var r : source) counts.merge(text(r, "module"), 1L, Long::sum);
    var filtered =
        source.stream().filter(r -> module.isBlank() || module.equals(r.get("module"))).toList();
    var values =
        filtered.stream()
            .skip((long) (Math.max(1, page) - 1) * Math.min(100, Math.max(1, size)))
            .limit(Math.min(100, Math.max(1, size)))
            .map(
                r -> {
                  var v = new LinkedHashMap<>(r);
                  v.put("orderId", r.get("id"));
                  v.put("orderNo", r.get("code"));
                  v.put("equipmentName", r.get("name"));
                  v.put("kind", r.get("status"));
                  v.put("title", r.get("name"));
                  v.put("todo", 0);
                  v.put("done", 0);
                  v.put("isRead", 1);
                  return v;
                })
            .toList();
    return Map.of("data", values, "total", filtered.size(), "moduleCounts", counts);
  }

  public Object filterChoices(boolean team) {
    if (team) permissions.require("workspace", "团队工作查看");
    var values = tasks(team);
    var companies = new TreeMap<String, Map<String, Object>>();
    var departments = new TreeMap<String, Map<String, Object>>();
    for (var row : values)
      for (var key : List.of("companyId", "departmentId")) {
        String value = text(row, key);
        if (value.isBlank()) continue;
        var units = repository.findOrganizationName(auth.tenantId(), id(value));
        if (!units.isEmpty())
          (key.equals("companyId") ? companies : departments)
              .put(value, Map.of("id", value, "name", units.getFirst().get("name")));
      }
    return Map.of("companies", companies.values(), "departments", departments.values());
  }

  public Object block(Map<String, Object> layout) {
    var config = rows(layout.get("blocks")).getFirst();
    String type = text(config, "type"), module = text(config, "module");
    boolean team = text(config, "scope").equals("team");
    if (team) permissions.require("workspace", "团队工作查看");
    int limit = (int) id(config.get("limit"));
    if (type.equals("todo") || type.equals("inventory")) {
      var data =
          (Map<String, Object>)
              inbox.list(
                  "todo",
                  "pending",
                  type.equals("inventory") ? "inventory" : module,
                  "",
                  "",
                  1,
                  limit);
      var output = new ArrayList<Map<String, Object>>();
      for (var row : rows(data.get("data"))) {
        var v = new LinkedHashMap<>(row);
        v.put("code", row.get("orderNo"));
        v.put("name", row.get("title"));
        v.put(
            "url",
            text(row, "module").equals("anomaly")
                ? "/anomalies/" + row.get("orderId")
                : text(row, "module").equals("repairPlan")
                    ? "/repair-plans/" + row.get("orderId")
                    : text(row, "module").equals("repair")
                        ? "/repairs/" + row.get("orderId")
                        : text(row, "module").equals("maintenance")
                            ? "/maintenance/task/" + row.get("orderId")
                            : text(row, "module").equals("inspection")
                                ? "/inspection/task/" + row.get("orderId")
                                : text(row, "module").equals("inventory")
                                    ? "/inventory/outbounds/new?requests=" + row.get("orderId")
                                    : "/inventory/outbounds/" + row.get("orderId"));
        output.add(v);
      }
      return Map.of(
          "data",
          output,
          "total",
          data.get("total"),
          "url",
          "/workspace/inbox?tab=pending" + (module.isBlank() ? "" : "&module=" + module));
    }
    if (type.equals("surplus")) return custody.list(!team, "", 1, limit);
    if (type.equals("anomalies"))
      return anomalies.list(
          "",
          null,
          1,
          limit,
          !team,
          text(config, "status").isBlank()
              ? ""
              : encode(
                  json,
                  List.of(
                      Map.of(
                          "field",
                          "status",
                          "operator",
                          "eq",
                          "values",
                          List.of(text(config, "status"))))));
    if (type.equals("shortcuts")) return Map.of("data", shortcuts(), "total", shortcuts().size());
    if (type.equals("recent")) {
      var pref = repository.loadRecent(auth.tenantId(), auth.userId());
      var recent =
          pref.isEmpty()
              ? List.<Map<String, Object>>of()
              : rows(decode(json, pref.getFirst().get("recent_json")));
      var safe =
          recent.stream()
              .map(v -> recentTarget(text(v, "url")))
              .filter(Objects::nonNull)
              .limit(limit)
              .toList();
      return Map.of("data", safe, "total", safe.size());
    }
    var taskRows =
        tasks(team).stream()
            .filter(v -> module.isBlank() || module.equals(v.get("module")))
            .filter(
                v ->
                    text(config, "status").isBlank()
                        || text(config, "status").equals(v.get("status")))
            .filter(
                v ->
                    text(config, "companyId").isBlank()
                        || text(config, "companyId").equals(v.get("companyId")))
            .filter(
                v ->
                    text(config, "departmentId").isBlank()
                        || text(config, "departmentId").equals(v.get("departmentId")))
            .toList();
    if (type.equals("stats")) {
      var counts = new LinkedHashMap<String, Long>();
      for (var t : taskRows) counts.merge(text(t, "status"), 1L, Long::sum);
      return Map.of("counts", counts, "total", taskRows.size(), "data", List.of());
    }
    if (type.equals("load")) {
      permissions.require("workspace", "团队工作查看");
      var counts = new LinkedHashMap<String, Integer>();
      var names = new LinkedHashMap<String, String>();
      for (var t : taskRows)
        if (!Set.of("已完成", "已撤销", "已作废", "已关闭").contains(t.get("status"))) {
          String key = text(t, "ownerId").isBlank() ? "unassigned" : text(t, "ownerId");
          names.put(key, text(t, "owner").isBlank() ? "尚未接单" : text(t, "owner"));
          counts.merge(key, 1, Integer::sum);
        }
      return Map.of(
          "data",
          counts.entrySet().stream()
              .map(
                  e ->
                      Map.of(
                          "id", e.getKey(), "name", names.get(e.getKey()), "count", e.getValue()))
              .limit(limit)
              .toList(),
          "total",
          counts.size());
    }
    var selected =
        taskRows.stream()
            .filter(
                t ->
                    type.equals("created")
                        ? text(t, "creatorId").equals(Long.toString(auth.userId()))
                        : type.equals("today")
                            ? text(t, "due")
                                .startsWith(LocalDate.now(ZoneId.of("Asia/Shanghai")).toString())
                            : Set.of("处理中", "执行中", "挂起").contains(t.get("status")))
            .sorted(Comparator.comparing((Map<String, Object> t) -> text(t, "time")).reversed())
            .toList();
    return Map.of(
        "data",
        selected.stream().limit(limit).toList(),
        "total",
        selected.size(),
        "url",
        "/workspace/inbox?tab="
            + (type.equals("created") ? "created" : type.equals("today") ? "today" : "active")
            + "&module="
            + module
            + "&status="
            + java.net.URLEncoder.encode(
                text(config, "status"), java.nio.charset.StandardCharsets.UTF_8)
            + "&companyId="
            + text(config, "companyId")
            + "&departmentId="
            + text(config, "departmentId"));
  }

  List<Map<String, Object>> shortcuts() {
    var values = new ArrayList<Map<String, Object>>();
    for (var entry :
        List.of(
            new String[] {"equipment", "设备台账", "/equipment"},
            new String[] {"repair", "维修工单", "/repairs"},
            new String[] {"anomaly", "异常库", "/anomalies"},
            new String[] {"repairPlan", "维修计划", "/repair-plans"},
            new String[] {"maintenanceTask", "保养任务", "/maintenance/tasks"},
            new String[] {"inspectionDailyTask", "日常点检", "/inspection/daily-tasks"},
            new String[] {"inventoryOutbound", "出库管理", "/inventory/outbounds"},
            new String[] {"org", "组织架构", "/organization"},
            new String[] {"user", "用户管理", "/users"},
            new String[] {"role", "角色权限", "/roles"})) {
      if (permissions.menuActions().getOrDefault(entry[0], Set.of()).contains("查看"))
        values.add(Map.of("name", entry[1], "url", entry[2], "id", entry[0]));
    }
    return values;
  }

  Map<String, Object> recentTarget(String url) {
    if (url.contains("?") || url.contains("#") || url.length() > 180) return null;
    for (var entry : shortcuts())
      if (url.equals(entry.get("url")))
        return Map.of("id", url, "name", entry.get("name"), "url", url);
    try {
      if (url.matches("/repairs/[0-9]+")) {
        var r = workflow.require(Long.parseLong(url.substring(url.lastIndexOf('/') + 1)));
        workflow.viewAllowed(r);
        return Map.of("id", url, "name", r.getOrderNo() + " · " + r.getEquipmentName(), "url", url);
      }
      if (url.matches("/(maintenance|inspection)/task/[0-9]+")) {
        long key = Long.parseLong(url.substring(url.lastIndexOf('/') + 1));
        var t = url.startsWith("/maintenance") ? maintenance.task(key) : inspection.task(key);
        return Map.of(
            "id", url, "name", text(t, "code") + " · " + text(t, "equipmentName"), "url", url);
      }
      if (url.matches("/anomalies/[0-9]+")) {
        long key = Long.parseLong(url.substring(url.lastIndexOf('/') + 1));
        var t = (Map<String, Object>) anomalies.detail(key);
        return Map.of("id", url, "name", text(t, "code"), "url", url);
      }
    } catch (IllegalArgumentException ignored) {
      return null;
    } catch (ResponseStatusException denied) {
      if (denied.getStatusCode().value() != 403) throw denied;
      return null;
    }
    return null;
  }

  @org.springframework.transaction.annotation.Transactional
  public Object recent(String url) {
    var target = recentTarget(url);
    check(target != null, "访问记录不属于当前授权页面");
    repository.ensurePreference(auth.tenantId(), auth.userId());
    var row = repository.lockRecent(auth.tenantId(), auth.userId());
    var recent = new ArrayList<Map<String, Object>>();
    recent.add(Map.of("url", url));
    for (var old : rows(decode(json, row.get("recent_json"))))
      if (!url.equals(text(old, "url")) && recent.size() < 20)
        recent.add(Map.of("url", text(old, "url")));
    repository.updateRecent(encode(json, recent), auth.tenantId(), auth.userId());
    return Map.of("success", true);
  }
}
