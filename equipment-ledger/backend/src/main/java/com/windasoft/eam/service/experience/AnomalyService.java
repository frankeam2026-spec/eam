package com.windasoft.eam.service.experience;

import static com.windasoft.eam.common.experience.ExperienceSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.experience.ExperienceSupport;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.event.experience.ExperienceEvents;
import com.windasoft.eam.repository.experience.AnomalyRepository;
import com.windasoft.eam.service.inspection.InspectionService;
import com.windasoft.eam.service.maintenance.MaintenanceService;
import com.windasoft.eam.service.repair.RepairWorkflowService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnomalyService {
  final AnomalyRepository repository;
  final AuthContext auth;
  final PermissionService permissions;
  final ObjectMapper json;
  final com.windasoft.eam.service.repair.RepairOrderService repairs;
  final RepairWorkflowService workflow;
  final MaintenanceService maintenance;
  final InspectionService inspection;

  public AnomalyService(
      AnomalyRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json,
      com.windasoft.eam.service.repair.RepairOrderService repairs,
      RepairWorkflowService workflow,
      MaintenanceService maintenance,
      InspectionService inspection) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
    this.repairs = repairs;
    this.workflow = workflow;
    this.maintenance = maintenance;
    this.inspection = inspection;
  }

  Equipment eq(long id) {
    var e = repository.findEquipment(id);
    check(
        e != null
            && Objects.equals(e.getTenantId(), auth.tenantId())
            && !Integer.valueOf(1).equals(e.getDeleted()),
        "设备不存在");
    return e;
  }

  boolean scope(String menu, String action, Equipment e, Long owner) {
    try {
      return permissions
          .scope(menu, action)
          .matches(e.getCompanyOrgId(), e.getDepartmentOrgId(), owner, auth.userId());
    } catch (ResponseStatusException denied) {
      if (denied.getStatusCode().value() != 403) throw denied;
      return false;
    }
  }

  Map<String, Object> source(String kind, long id) {
    check(Set.of("保养", "点巡检").contains(kind), "异常来源类型无效");
    return kind.equals("保养") ? maintenance.task(id) : inspection.task(id);
  }

  public boolean canView(Map<String, Object> r) {
    var e = eq(id(r.get("equipment_id")));
    if (scope("anomaly", "查看", e, id(r.get("discovered_by")))) return true;
    if (r.get("source_task_id") != null) {
      try {
        source(text(r, "source_kind"), id(r.get("source_task_id")));
        return true;
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() != 403) throw denied;
      }
    }
    if (r.get("repair_id") != null) {
      try {
        workflow.viewAllowed(workflow.require(id(r.get("repair_id"))));
        return true;
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() != 403) throw denied;
      }
    }
    return false;
  }

  void checkAction(Map<String, Object> r, String action) {
    permissions.require("anomaly", action);
    if (!scope("anomaly", action, eq(id(r.get("equipment_id"))), id(r.get("discovered_by"))))
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "没有此异常的操作权限");
  }

  String number(String prefix) {
    String month =
        LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMM"));
    repository.nextMonthlyNumber(prefix + "-" + auth.tenantId(), month);
    return prefix + "-" + month + "-" + String.format("%04d", repository.currentNumber());
  }

  void event(String kind, long id, String action, Object details) {
    if (kind.equals("anomaly")) repository.captureOrganization(auth.tenantId(), id);
    repository.insertEvent(
        IdWorker.getId(),
        auth.tenantId(),
        kind,
        id,
        action,
        auth.userId(),
        auth.user().getDisplayName(),
        encode(json, details));
    if (kind.equals("anomaly")) {
      var row = repository.loadAnomalyState(auth.tenantId(), id);
      if (text(row, "status").equals("待处理"))
        notify(
            "anomaly",
            id,
            ExperienceSupport.id(row.get("discovered_by")),
            "异常待处理",
            text(row, "description"),
            true);
      repository.syncAnomalyTodos(text(row, "status").equals("待处理") ? 0 : 1, auth.tenantId(), id);
    } else if (kind.equals("repairPlan")) {
      var row = repository.loadPlanState(auth.tenantId(), id);
      if (text(row, "status").equals("待生成"))
        notify(
            "repairPlan",
            id,
            ExperienceSupport.id(row.get("owner_id")),
            "计划维修跟进",
            text(row, "description"),
            true);
      repository.syncPlanTodos(
          auth.tenantId(), id, ExperienceSupport.id(row.get("owner_id")), text(row, "status"));
      if (text(row, "status").equals("已生成"))
        notify(
            "repairPlan",
            id,
            ExperienceSupport.id(row.get("owner_id")),
            "维修工单已生成",
            text(row, "description"),
            false);
    }
  }

  void notify(String kind, long id, long user, String label, String title, boolean todo) {
    repository.insertInbox(
        IdWorker.getId(),
        auth.tenantId(),
        kind,
        id,
        user,
        label,
        title.length() > 250 ? title.substring(0, 250) : title,
        todo ? 1 : 0);
  }

  public Map<String, Object> raw(long id, boolean lock) {
    var rs = repository.loadAnomaly(auth.tenantId(), id, lock);
    check(rs.size() == 1, "异常不存在");
    return rs.getFirst();
  }

  Map<String, Object> viewAnomaly(Map<String, Object> r) {
    var v = view(r);
    var e = eq(id(r.get("equipment_id")));
    v.put("equipmentCode", e.getEquipmentCode());
    v.put("equipmentName", e.getName());
    v.put("description", r.get("description"));
    v.put(
        "attachments",
        rows(decode(json, r.get("attachments"))).stream()
            .map(
                f -> {
                  var ref = new LinkedHashMap<>(f);
                  ref.put("url", "/api/v1/anomalies/" + r.get("id") + "/files/" + f.get("id"));
                  return ref;
                })
            .toList());
    v.put("rowVersion", r.get("row_version"));
    v.put("url", "/anomalies/" + r.get("id"));
    v.put("name", r.get("description"));
    return v;
  }

  public Map<String, Object> list(
      String q, Long equipmentId, int page, int size, boolean personal) {
    return list(q, equipmentId, page, size, personal, "");
  }

  public Map<String, Object> list(
      String q, Long equipmentId, int page, int size, boolean personal, String rawFilters) {
    var condition =
        filters(
            json,
            rawFilters,
            Set.of(
                "code",
                "equipmentCode",
                "equipmentName",
                "description",
                "status",
                "severity",
                "source_kind",
                "discovered_name",
                "discovered_at"));
    page = Math.max(1, page);
    size = Math.min(100, Math.max(1, size));
    var values =
        repository.searchAnomalies(auth.tenantId(), equipmentId).stream()
            .filter(this::canView)
            .filter(r -> !personal || id(r.get("discovered_by")) == auth.userId())
            .map(this::viewAnomaly)
            .filter(condition)
            .filter(
                r ->
                    q.isBlank()
                        || text(r, "code").contains(q)
                        || text(r, "equipmentName").contains(q)
                        || text(r, "description").contains(q))
            .toList();
    return Map.of(
        "data",
        values.stream().skip((long) (page - 1) * size).limit(size).toList(),
        "total",
        values.size(),
        "url",
        "/anomalies");
  }

  public Object sourceAnomalies(String kind, long task) {
    var items = new ArrayList<Map<String, Object>>();
    if (kind.equals("维修")) {
      workflow.viewAllowed(workflow.require(task));
    } else {
      var source = source(kind, task);
      long maintenanceEq =
          kind.equals("保养")
              ? id(repository.findMaintenanceEquipment(auth.tenantId(), task).get("equipment_id"))
              : 0;
      for (var item : rows(source.get("items")))
        items.add(
            Map.of(
                "id",
                item.get("id"),
                "name",
                text(item, "name"),
                "equipmentId",
                kind.equals("保养") ? Long.toString(maintenanceEq) : text(item, "equipmentId")));
    }
    return Map.of(
        "data",
        repository.sourceAnomalies(auth.tenantId(), kind, task).stream()
            .filter(this::canView)
            .map(this::viewAnomaly)
            .toList(),
        "items",
        items);
  }

  public Object detail(long id) {
    var r = raw(id, false);
    if (!canView(r))
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "没有查看此异常的权限");
    var v = viewAnomaly(r);
    v.put("events", events("anomaly", id));
    v.put(
        "actions",
        List.of("消除", "报修", "安排维修", "作废").stream()
            .filter(
                a ->
                    scope(
                        "anomaly",
                        a,
                        eq(ExperienceSupport.id(r.get("equipment_id"))),
                        ExperienceSupport.id(r.get("discovered_by"))))
            .toList());
    return v;
  }

  List<Map<String, Object>> events(String kind, long id) {
    return repository.listEvents(auth.tenantId(), kind, id).stream()
        .map(
            r -> {
              r.put("details", decode(json, r.get("details")));
              return r;
            })
        .toList();
  }

  public String fileSource(Map<String, Object> f, Map<String, Object> a) {
    String source = text(f, "source");
    if (source.equals("anomalies")) return source;
    return text(a, "source_kind").equals("保养") ? "maintenance" : "inspection";
  }

  List<Map<String, Object>> ownFiles(Object value) {
    var out = new ArrayList<Map<String, Object>>();
    for (var f : rows(value)) {
      String fid = text(f, "id");
      check(fid.matches("[0-9a-f-]{36}"), "附件编号无效");
      var rs = repository.findAttachment(auth.tenantId(), auth.userId(), fid);
      check(rs.size() == 1, "附件未上传或不属于当前用户");
      var ref = new LinkedHashMap<>(rs.getFirst());
      ref.put("source", "anomalies");
      out.add(ref);
    }
    check(out.size() <= 30, "最多30个附件");
    return out;
  }

  public void checkRepairAttachment(long aid, String fid) {
    var a = raw(aid, false);
    check(
        canView(a)
            || scope("anomaly", "报修", eq(id(a.get("equipment_id"))), id(a.get("discovered_by"))),
        "来源异常不可访问");
    check(
        rows(decode(json, a.get("attachments"))).stream().anyMatch(f -> fid.equals(text(f, "id"))),
        "来源附件不属于异常");
  }

  @Transactional
  public Object create(Map<String, Object> b) {
    long eqId = id(b.get("equipmentId"));
    var e = eq(eqId);
    String kind = text(b, "sourceKind");
    long taskId = id(b.get("sourceTaskId"));
    String itemId = text(b, "sourceItemId");
    List<Map<String, Object>> files = List.of();
    if (kind.isBlank() || kind.equals("人工")) {
      kind = "人工";
      check(scope("anomaly", "新增", e, auth.userId()), "没有新增异常权限");
      permissions.checkEquipment(e, "查看");
      taskId = 0;
      itemId = "";
    } else {
      var s = source(kind, taskId);
      boolean participates = Boolean.TRUE.equals(s.get("canExecute"));
      check(participates || scope("anomaly", "新增", e, auth.userId()), "仅任务执行人或有权限者能登记异常");
      var items = rows(s.get("items"));
      final String sourceKey = itemId;
      var item =
          items.stream()
              .filter(i -> text(i, "id").equals(sourceKey))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("来源项目不存在"));
      long sourceEq =
          kind.equals("保养")
              ? id(repository.findCaptureEquipment(auth.tenantId(), taskId).get("equipment_id"))
              : id(item.get("equipmentId"));
      check(sourceEq == eqId, "异常设备不属于来源项目");
      var result = (Map<String, Object>) s.getOrDefault("result", Map.of());
      files =
          rows(result.get("items")).stream()
              .filter(i -> text(i, "id").equals(sourceKey))
              .flatMap(i -> rows(i.get("images")).stream())
              .toList();
    }
    var combinedFiles = new ArrayList<Map<String, Object>>(files);
    combinedFiles.addAll(ownFiles(b.get("attachments")));
    files = combinedFiles;
    String description = required(b, "description", 4000), severity = text(b, "severity");
    check(severity.length() <= 40, "等级名称过长");
    long aid = IdWorker.getId();
    try {
      repository.insertAnomaly(
          aid,
          auth.tenantId(),
          number("YC"),
          eqId,
          kind,
          taskId == 0 ? null : taskId,
          itemId.isBlank() ? null : itemId,
          description,
          severity,
          encode(json, files),
          auth.userId(),
          auth.user().getDisplayName(),
          LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
      event("anomaly", aid, "登记异常", Map.of("source", kind));
    } catch (org.springframework.dao.DuplicateKeyException duplicate) {
      var old = repository.findAnomalyBySource(auth.tenantId(), kind, taskId, eqId, itemId);
      return detail(id(old.get("id")));
    }
    return detail(aid);
  }

  @Transactional
  public Object action(long aid, Map<String, Object> b) {
    var r = raw(aid, true);
    String action = required(b, "action", 30);
    String grant =
        switch (action) {
          case "resolve" -> "消除";
          case "report", "link" -> "报修";
          case "void" -> "作废";
          default -> throw new IllegalArgumentException("未知异常操作");
        };
    checkAction(r, grant);
    if (id(r.get("row_version")) != id(b.get("rowVersion"))) conflict();
    check(!Set.of("已消除", "已作废").contains(r.get("status")), "异常已结束");
    check(r.get("repair_id") == null && r.get("plan_id") == null, "异常已有处理安排，请先在关联单据处理");
    if (action.equals("resolve") || action.equals("void")) {
      String reason = required(b, "reason", 4000);
      var evidence = new ArrayList<>(rows(decode(json, r.get("attachments"))));
      evidence.addAll(ownFiles(b.get("attachments")));
      repository.updateEvidence(encode(json, evidence), auth.tenantId(), aid);
      repository.resolveAnomaly(
          action.equals("resolve") ? "已消除" : "已作废",
          reason,
          LocalDateTime.now(ZoneId.of("Asia/Shanghai")),
          auth.tenantId(),
          aid);
      event("anomaly", aid, action.equals("resolve") ? "直接消除" : "作废异常", Map.of("reason", reason));
    } else {
      long repair;
      if (action.equals("link")) {
        repair = id(b.get("repairId"));
        var order = workflow.require(repair);
        workflow.viewAllowed(order);
        check(
            order.getEquipmentCode().equals(eq(id(r.get("equipment_id"))).getEquipmentCode())
                && !Set.of("已完成", "已撤销").contains(order.getStatus()),
            "只能关联同设备的有效维修工单");
      } else {
        repair = createRepair(r, b);
      }
      repository.linkRepair(repair, auth.tenantId(), aid);
      event(
          "anomaly",
          aid,
          action.equals("link") ? "关联维修工单" : "发起维修",
          Map.of("repairId", Long.toString(repair)));
    }
    return detail(aid);
  }

  long createRepair(Map<String, Object> r, Map<String, Object> b) {
    var e = eq(id(r.get("equipment_id")));
    var result =
        repairs.create(
            new com.windasoft.eam.dto.repair.RepairForm(
                e.getEquipmentCode(),
                e.getName(),
                e.getDepartment(),
                required(b, "faultType", 150),
                required(b, "severity", 40),
                text(r, "description"),
                auth.user().getDisplayName(),
                null,
                null,
                null,
                json.valueToTree(
                    r.containsKey("source_kind")
                        ? viewAnomaly(r).get("attachments")
                        : repository.listPlanAnomalies(auth.tenantId(), r.get("id")).stream()
                            .flatMap(a -> rows(viewAnomaly(a).get("attachments")).stream())
                            .toList())));
    return id(((Map<String, Object>) result.get("data")).get("id"));
  }

  @EventListener
  @Transactional
  public void repairChanged(ExperienceEvents.RepairState ev) {
    String status =
        ev.status().equals("已完成")
            ? "已消除"
            : ev.status().equals("已撤销")
                ? "待处理"
                : Set.of("处理中", "挂起", "待验收").contains(ev.status()) ? "处理中" : "已安排";
    var rows = repository.lockRepairAnomalies(auth.tenantId(), ev.repairId());
    for (var r : rows) {
      repository.updateRepairAnomaly(
          status,
          status.equals("已消除") ? LocalDateTime.now(ZoneId.of("Asia/Shanghai")) : null,
          status.equals("待处理") ? null : ev.repairId(),
          status,
          status,
          auth.tenantId(),
          id(r.get("id")));
      event(
          "anomaly",
          id(r.get("id")),
          "维修状态同步",
          Map.of("repairId", Long.toString(ev.repairId()), "repairStatus", ev.status()));
    }
  }

  @EventListener
  @Transactional
  public void taskResults(ExperienceEvents.TaskResult ev) {
    capture(ev.kind(), ev.taskId());
  }

  public void capture(String kind, long task) {
    capture(kind, task, false);
  }

  public void capture(String kind, long task, boolean historical) {
    var s = source(kind, task);
    var sourceEvents =
        repository.findSourceActor(
            auth.tenantId(), task, kind.equals("保养") ? "提交保养" : "提交检查结果", kind);
    if (historical && sourceEvents.isEmpty()) return;
    long discoveredBy =
        sourceEvents.isEmpty() ? auth.userId() : id(sourceEvents.getFirst().get("actor_id"));
    String discoveredName =
        sourceEvents.isEmpty()
            ? auth.user().getDisplayName()
            : text(sourceEvents.getFirst(), "actor_name");
    Object discoveredAt =
        sourceEvents.isEmpty()
            ? LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
            : sourceEvents.getFirst().get("created_at");
    var result = (Map<String, Object>) s.getOrDefault("result", Map.of());
    for (var answer : rows(result.get("items"))) {
      if (!Boolean.TRUE.equals(answer.get("abnormal"))) continue;
      String key = text(answer, "id");
      var item =
          rows(s.get("items")).stream()
              .filter(i -> text(i, "id").equals(key))
              .findFirst()
              .orElse(null);
      if (item == null) continue;
      long eid =
          kind.equals("保养")
              ? id(repository.findBackfillEquipment(auth.tenantId(), task).get("equipment_id"))
              : id(item.get("equipmentId"));
      long aid = IdWorker.getId();
      int inserted =
          repository.insertCapturedAnomaly(
              aid,
              auth.tenantId(),
              number("YC"),
              eid,
              kind,
              task,
              key,
              text(item, "name") + "：" + text(answer, "value"),
              encode(json, rows(answer.get("images"))),
              discoveredBy,
              discoveredName,
              discoveredAt,
              Boolean.TRUE.equals(answer.get("resolved")) ? "已消除" : "待处理",
              text(answer, "measure"),
              Boolean.TRUE.equals(answer.get("resolved")) ? discoveredAt : null);
      if (inserted == 1)
        event("anomaly", aid, "发现异常", Map.of("taskId", Long.toString(task), "itemId", key));
    }
  }

  @Transactional
  public Object backfill() {
    permissions.require("anomaly", "新增");
    for (var t : maintenance.tasks()) capture("保养", id(t.get("id")), true);
    for (var t : inspection.tasks()) capture("点巡检", id(t.get("id")), true);
    return Map.of("success", true);
  }

  Map<String, Object> planRaw(long id, boolean lock) {
    var r = repository.loadPlan(auth.tenantId(), id, lock);
    check(r.size() == 1, "维修计划不存在");
    return r.getFirst();
  }

  void planAccess(Map<String, Object> r, String action) {
    check(
        action.equals("查看")
                && (id(r.get("owner_id")) == auth.userId()
                    || id(r.get("creator_id")) == auth.userId())
            || scope("repairPlan", action, eq(id(r.get("equipment_id"))), id(r.get("creator_id"))),
        "没有该维修计划权限");
  }

  public Object planList(String q, int page, int size) {
    return planList(q, page, size, "");
  }

  public Object planList(String q, int page, int size, String rawFilters) {
    var condition =
        filters(
            json,
            rawFilters,
            Set.of(
                "code",
                "equipmentCode",
                "equipmentName",
                "description",
                "status",
                "planned_at",
                "severity"));

    if (!permissions.allowed("repairPlan", "查看"))
      check(
          repository.countPersonalPlans(auth.tenantId(), auth.userId(), auth.userId()) > 0,
          "没有查看维修计划权限");
    var values =
        repository.listPlans(auth.tenantId()).stream()
            .filter(
                r ->
                    id(r.get("owner_id")) == auth.userId()
                        || id(r.get("creator_id")) == auth.userId()
                        || scope(
                            "repairPlan",
                            "查看",
                            eq(id(r.get("equipment_id"))),
                            id(r.get("creator_id"))))
            .map(this::planView)
            .filter(condition)
            .filter(
                r ->
                    q.isBlank()
                        || text(r, "code").contains(q)
                        || text(r, "equipmentName").contains(q)
                        || text(r, "description").contains(q))
            .toList();
    return Map.of(
        "data",
        values.stream()
            .skip((long) (Math.max(1, page) - 1) * Math.min(100, Math.max(1, size)))
            .limit(Math.min(100, Math.max(1, size)))
            .toList(),
        "total",
        values.size());
  }

  Map<String, Object> planView(Map<String, Object> r) {
    var v = view(r);
    var e = eq(id(r.get("equipment_id")));
    v.put("equipmentCode", e.getEquipmentCode());
    v.put("equipmentName", e.getName());
    v.put("rowVersion", r.get("row_version"));
    v.put(
        "anomalyIds",
        repository.listPlanAnomalyIds(auth.tenantId(), id(r.get("id"))).stream()
            .map(x -> x.get("id"))
            .toList());
    return v;
  }

  public Object planDetail(long id) {
    var r = planRaw(id, false);
    planAccess(r, "查看");
    var v = planView(r);
    v.put("events", events("repairPlan", id));
    return v;
  }

  @Transactional
  public void generationFailed(long tenant, long plan, String message) {
    var records = repository.lockGenerationFailure(tenant, plan);
    if (records.isEmpty()) return;
    var row = records.getFirst();
    if (message.equals(text(row, "generation_error"))) return;
    repository.storeGenerationError(message, tenant, plan);
    repository.insertFailureEvent(
        IdWorker.getId(), tenant, plan, encode(json, Map.of("reason", message)));
    repository.insertFailureNotice(
        IdWorker.getId(),
        tenant,
        plan,
        row.get("owner_id"),
        "维修计划生成失败：" + message.substring(0, Math.min(200, message.length())));
  }

  @Transactional
  public Object savePlan(Long pid, Map<String, Object> b) {
    long eqId = id(b.get("equipmentId"));
    check(scope("repairPlan", pid == null ? "新增" : "编辑", eq(eqId), auth.userId()), "没有维护维修计划权限");
    String description = required(b, "description", 4000),
        fault = required(b, "faultType", 150),
        severity = required(b, "severity", 40);
    var planned = LocalDateTime.parse(required(b, "plannedAt", 30));
    long owner = id(b.get("ownerId"));
    check(repository.countActiveUser(auth.tenantId(), owner) == 1, "请选择有效的计划负责人");
    if (pid == null) {
      pid = IdWorker.getId();
      repository.insertPlan(
          pid,
          auth.tenantId(),
          number("JX"),
          eqId,
          description,
          planned,
          owner,
          auth.userId(),
          fault,
          severity);
    } else {
      var r = planRaw(pid, true);
      planAccess(r, "编辑");
      check(Set.of("草稿", "待生成").contains(r.get("status")), "已生成或取消计划不能编辑");
      check(id(r.get("equipment_id")) == eqId, "编辑不能更换设备");
      if (id(r.get("row_version")) != id(b.get("rowVersion"))) conflict();
      repository.updatePlan(description, planned, owner, fault, severity, auth.tenantId(), pid);
    }
    var ids = b.get("anomalyIds") instanceof List<?> l ? l : List.of();
    var target = pid;
    var retained =
        ids.stream().map(ExperienceSupport::id).collect(java.util.stream.Collectors.toSet());
    for (var previous : repository.lockPlanAnomalyIds(auth.tenantId(), pid)) {
      long aid = id(previous.get("id"));
      if (retained.contains(aid)) continue;
      var anomaly = raw(aid, true);
      checkAction(anomaly, "安排维修");
      repository.detachPlanAnomaly(auth.tenantId(), aid);
      event("anomaly", aid, "取消计划关联", Map.of("planId", pid.toString(), "reason", "维修计划调整"));
    }
    for (long aid : ids.stream().map(ExperienceSupport::id).sorted().toList()) {
      var a = raw(aid, true);
      checkAction(a, "安排维修");
      check(id(a.get("equipment_id")) == eqId, "只能安排同设备异常");
      check(
          a.get("repair_id") == null
              && (a.get("plan_id") == null || id(a.get("plan_id")) == pid)
              && !Set.of("已消除", "已作废").contains(a.get("status")),
          "异常已有安排或已结束");
      repository.attachPlanAnomaly(pid, auth.tenantId(), aid);
      event("anomaly", aid, "安排维修计划", Map.of("planId", pid.toString()));
    }
    event("repairPlan", pid, "保存维修计划", Map.of("plannedAt", planned.toString()));
    var r = planRaw(pid, false);
    if (r.get("status").equals("待生成")
        && !planned.isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))) generate(pid);
    return planDetail(target);
  }

  @Transactional
  public Object planAction(long pid, Map<String, Object> b) {
    var r = planRaw(pid, true);
    if (id(r.get("row_version")) != id(b.get("rowVersion"))) conflict();
    String action = required(b, "action", 30);
    planAccess(r, action.equals("cancel") ? "取消" : action.equals("generate") ? "生成工单" : "启用");
    check(Set.of("草稿", "待生成").contains(r.get("status")), "计划状态不允许操作");
    if (action.equals("cancel")) {
      String reason = required(b, "reason", 1000);
      repository.cancelPlan(auth.tenantId(), pid);
      var affected = repository.lockCancelledAnomalies(auth.tenantId(), pid);
      for (var a : affected) {
        repository.detachCancelledAnomaly(auth.tenantId(), a.get("id"));
        event("anomaly", id(a.get("id")), "取消维修安排", Map.of("reason", reason));
      }
      event("repairPlan", pid, "取消计划", Map.of("reason", reason));
    } else {
      check(Set.of("enable", "generate").contains(action), "未知计划操作");
      repository.reopenPlan(auth.tenantId(), pid);
      event("repairPlan", pid, "启用计划", Map.of());
      if (action.equals("generate")
          || !LocalDateTime.parse(Objects.toString(r.get("planned_at")).replace(' ', 'T'))
              .isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))) generate(pid);
    }
    return planDetail(pid);
  }

  @Transactional
  public void generate(long pid) {
    var r = planRaw(pid, true);
    check(r.get("status").equals("待生成"), "计划不在待生成状态");
    planAccess(r, "启用");
    var b = Map.of("faultType", r.get("fault_type"), "severity", r.get("severity"));
    long repair = createRepair(r, b);
    repository.finishGeneration(repair, auth.tenantId(), pid);
    var affected = repository.lockPendingAnomalies(auth.tenantId(), pid);
    for (var a : affected) {
      repository.linkGeneratedRepair(repair, auth.tenantId(), a.get("id"));
      event("anomaly", id(a.get("id")), "计划生成维修工单", Map.of("repairId", Long.toString(repair)));
    }
    event("repairPlan", pid, "生成维修工单", Map.of("repairId", Long.toString(repair)));
  }

  public Object ownerChoices() {
    String action = permissions.allowed("repairPlan", "新增") ? "新增" : "编辑";
    var scope = permissions.scope("repairPlan", action);
    var choices =
        repository.listOwnerChoices(auth.tenantId()).stream()
            .filter(
                u ->
                    scope.matches(
                        permissions.companyOfOrganization(
                            u.get("orgId") == null ? null : ExperienceSupport.id(u.get("orgId"))),
                        u.get("orgId") == null ? null : ExperienceSupport.id(u.get("orgId")),
                        ExperienceSupport.id(u.get("id")),
                        auth.userId()))
            .peek(u -> u.remove("orgId"))
            .toList();
    return Map.of("data", choices);
  }
}
