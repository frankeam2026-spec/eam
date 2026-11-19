package com.windasoft.eam.service.experience;

import static com.windasoft.eam.common.experience.ExperienceSupport.*;

import com.windasoft.eam.common.experience.ExperienceSupport;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.equipment.EquipmentInstalledSpareView;
import com.windasoft.eam.dto.experience.EquipmentAttentionItem;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.experience.EquipmentExperienceRepository;
import com.windasoft.eam.repository.repair.RepairQueryRepository;
import com.windasoft.eam.service.equipment.EquipmentInstalledSpareService;
import com.windasoft.eam.service.inspection.InspectionListService;
import com.windasoft.eam.service.maintenance.MaintenanceService;
import com.windasoft.eam.service.repair.RepairWorkflowService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EquipmentExperienceService {
  private final EquipmentExperienceRepository repository;
  private final EquipmentQueryRepository equipmentFacts;
  private final RepairQueryRepository repairHistory;
  final AuthContext auth;
  final PermissionService permissions;
  final RepairWorkflowService workflow;
  final MaintenanceService maintenance;
  final InspectionListService inspectionLists;
  final AnomalyService anomalies;
  final EquipmentInstalledSpareService installedSpares;

  public EquipmentExperienceService(
      EquipmentExperienceRepository repository,
      EquipmentQueryRepository equipmentFacts,
      RepairQueryRepository repairHistory,
      AuthContext auth,
      PermissionService permissions,
      RepairWorkflowService workflow,
      MaintenanceService maintenance,
      InspectionListService inspectionLists,
      AnomalyService anomalies,
      EquipmentInstalledSpareService installedSpares) {
    this.repository = repository;
    this.equipmentFacts = equipmentFacts;
    this.repairHistory = repairHistory;
    this.auth = auth;
    this.permissions = permissions;
    this.workflow = workflow;
    this.maintenance = maintenance;
    this.inspectionLists = inspectionLists;
    this.anomalies = anomalies;
    this.installedSpares = installedSpares;
  }

  Equipment equipment(long id) {
    var e = equipmentFacts.findFacts(auth.tenantId(), id);
    check(
        e != null
            && Objects.equals(e.getTenantId(), auth.tenantId())
            && !Integer.valueOf(1).equals(e.getDeleted()),
        "设备不存在");
    permissions.checkEquipment(e, PermissionAction.VIEW);
    return e;
  }

  List<Map<String, Object>> equipmentAnomalies(long equipmentId) {
    var all = new ArrayList<Map<String, Object>>();
    int page = 1;
    while (true) {
      var response = anomalies.list("", equipmentId, page++, 100, false);
      var batch = rows(response.get("data"));
      all.addAll(batch);
      if (batch.isEmpty() || all.size() >= id(response.get("total"))) return all;
    }
  }

  List<RepairOrder> repairRows(Equipment e) {
    // 已关联的维修以设备ID为准，设备改编号后仍可追溯，复用编号也不会串单。
    return repairHistory
        .historyByEquipment(auth.tenantId(), e.getId(), e.getEquipmentCode())
        .stream()
        .filter(
            r -> {
              try {
                workflow.viewAllowed(r);
                return true;
              } catch (ResponseStatusException denied) {
                if (denied.getStatusCode().value() != 403) throw denied;
                return false;
              }
            })
        .toList();
  }

  List<Map<String, Object>> maintenanceRows(long eid) {
    var ids =
        repository.listMaintenanceIds(auth.tenantId(), eid).stream()
            .map(r -> r.get("id").toString())
            .collect(java.util.stream.Collectors.toSet());
    return maintenance.tasks().stream().filter(t -> ids.contains(text(t, "id"))).toList();
  }

  List<Map<String, Object>> inspectionRows(long eid) {
    return inspectionLists.tasks().stream()
        .filter(t -> rows(t.get("equipment")).stream().anyMatch(e -> id(e.get("id")) == eid))
        .toList();
  }

  record AttentionData(
      List<EquipmentAttentionItem> tasks,
      List<EquipmentAttentionItem> issues,
      Map<String, Boolean> access) {}

  private AttentionData attentionData(Equipment e) {
    long equipmentId = e.getId();
    var repairs = repairRows(e);
    var pm = maintenanceRows(equipmentId);
    var inspections = inspectionRows(equipmentId);
    var allIssues = equipmentAnomalies(equipmentId);
    var ownerIds = new HashSet<String>();
    for (var list : List.of(pm, inspections)) for (var t : list) ownerIds.addAll(taskOwnerIds(t));
    var names = repository.userNames(auth.tenantId(), ownerIds);
    var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    var tasks = new ArrayList<EquipmentAttentionItem>();
    for (var r : repairs)
      if (!Set.of("已完成", "已撤销").contains(r.getStatus()))
        tasks.add(
            new EquipmentAttentionItem(
                r.getId().toString(),
                "repair",
                r.getOrderNo(),
                Objects.toString(r.getDescription(), ""),
                r.getStatus(),
                Objects.toString(r.getAssignee(), ""),
                Objects.toString(r.getReportedAt(), ""),
                "",
                false,
                "维修",
                "/repairs/" + r.getId()));
    for (var entry : Map.of("maintenance", pm, "inspection", inspections).entrySet())
      for (var t : entry.getValue()) {
        if (Set.of("已完成", "已审核", "已作废", "已免检", "过期作废", "已取消").contains(text(t, "status"))) continue;
        String kind = entry.getKey();
        String deadline = text(t, kind.equals("maintenance") ? "expiresDate" : "expiresAt");
        String title = text(t, "planName");
        if (title.isBlank()) title = text(t, "name");
        if (title.isBlank()) title = text(t, "equipmentName");
        String source =
            kind.equals("maintenance") ? "保养" : text(t, "kind").equals("daily") ? "日常点检" : "周期巡检";
        tasks.add(
            new EquipmentAttentionItem(
                text(t, "id"),
                kind,
                text(t, "code"),
                title,
                text(t, "status"),
                taskOwnerIds(t).stream()
                    .map(v -> names.getOrDefault(v, ""))
                    .filter(v -> !v.isBlank())
                    .distinct()
                    .collect(java.util.stream.Collectors.joining("、")),
                text(t, "createdAt"),
                deadline,
                overdue(deadline, now),
                source,
                "/" + kind + "/task/" + text(t, "id")));
      }
    tasks.sort(attentionOrder());
    var issues =
        allIssues.stream()
            .filter(a -> !Set.of("已消除", "已作废").contains(text(a, "status")))
            .map(
                a ->
                    new EquipmentAttentionItem(
                        text(a, "id"),
                        "anomaly",
                        text(a, "code"),
                        text(a, "description"),
                        text(a, "status"),
                        text(a, "discovered_name"),
                        text(a, "discovered_at"),
                        "",
                        false,
                        text(a, "source_kind"),
                        text(a, "url")))
            .sorted(attentionOrder())
            .toList();
    var access =
        Map.of(
            "repair", permissions.allowed("repair", PermissionAction.VIEW) || !repairs.isEmpty(),
            "maintenance", permissions.allowed("maintenanceTask", PermissionAction.VIEW) || !pm.isEmpty(),
            "inspection",
                permissions.allowed("inspectionDailyTask", PermissionAction.VIEW)
                    || permissions.allowed("inspectionPeriodicTask", PermissionAction.VIEW)
                    || !inspections.isEmpty(),
            "anomaly", permissions.allowed("anomaly", PermissionAction.VIEW) || !allIssues.isEmpty());
    return new AttentionData(tasks, issues, access);
  }

  static List<String> taskOwnerIds(Map<String, Object> t) {
    if (!text(t, "ownerId").isBlank()) return List.of(text(t, "ownerId"));
    if (t.get("owners") instanceof List<?> list)
      return list.stream()
          .map(Object::toString)
          .filter(v -> v.matches("[0-9]+"))
          .distinct()
          .toList();
    return List.of();
  }

  static boolean overdue(String deadline, LocalDateTime now) {
    if (deadline.isBlank()) return false;
    try {
      if (deadline.length() == 10) return LocalDate.parse(deadline).isBefore(now.toLocalDate());
      return LocalDateTime.parse(deadline.replace(' ', 'T')).isBefore(now);
    } catch (java.time.format.DateTimeParseException invalid) {
      return false;
    }
  }

  static Comparator<EquipmentAttentionItem> attentionOrder() {
    return Comparator.comparing(EquipmentAttentionItem::overdue)
        .reversed()
        .thenComparing(t -> t.createdAt().replace('T', ' '), Comparator.reverseOrder())
        .thenComparing(EquipmentAttentionItem::kind)
        .thenComparing(EquipmentAttentionItem::id);
  }

  private static Map<String, Object> installedSpareRow(EquipmentInstalledSpareView spare) {
    var row = new LinkedHashMap<String, Object>();
    row.put("id", spare.getId());
    row.put("code", spare.getCode());
    row.put("name", spare.getName());
    row.put("model", spare.getModel());
    row.put("quantity", spare.getQuantity());
    row.put("status", spare.getStatus());
    row.put("installedAt", spare.getInstalledAt());
    return row;
  }

  public Object overview(long id) {
    var e = equipment(id);
    var data = attentionData(e);
    var counts = new LinkedHashMap<String, Integer>();
    for (var kind : List.of("repair", "maintenance", "inspection"))
      counts.put(kind, (int) data.tasks().stream().filter(t -> t.kind().equals(kind)).count());
    counts.put("anomaly", data.issues().size());
    var installed =
        installedSpares.list(auth.tenantId(), id).stream()
            .sorted(
                Comparator.comparing(
                        (EquipmentInstalledSpareView spare) ->
                            Objects.toString(spare.getInstalledAt(), ""))
                    .reversed()
                    .thenComparing(
                        spare -> Objects.toString(spare.getId(), ""), Comparator.reverseOrder()))
            .map(EquipmentExperienceService::installedSpareRow)
            .toList();
    return Map.of(
        "tasks",
        data.tasks().stream().limit(5).toList(),
        "anomalies",
        data.issues().stream().limit(5).toList(),
        "installed",
        installed.stream().limit(5).toList(),
        "installedTotal",
        installed.size(),
        "counts",
        counts,
        "access",
        data.access(),
        "state",
        Objects.toString(e.getEquipmentStatus(), ""),
        "owner",
        Objects.toString(e.getOwnerName(), ""));
  }

  public Object attention(long id, String kind, int page, int size) {
    check(Set.of("all", "repair", "maintenance", "inspection", "anomaly").contains(kind), "事项类型无效");
    var data = attentionData(equipment(id));
    var selected =
        kind.equals("anomaly")
            ? data.issues()
            : data.tasks().stream()
                .filter(t -> kind.equals("all") || kind.equals(t.kind()))
                .toList();
    int limit = Math.min(100, Math.max(1, size));
    boolean accessible =
        kind.equals("all")
            ? List.of("repair", "maintenance", "inspection").stream()
                .anyMatch(k -> data.access().get(k))
            : data.access().get(kind);
    return Map.of(
        "data",
        selected.stream().skip((long) (Math.max(1, page) - 1) * limit).limit(limit).toList(),
        "total",
        selected.size(),
        "accessible",
        accessible);
  }

  public Object events(long id, String kind, int page, int size) {
    return events(id, kind, page, size, "asc");
  }

  public Object events(long id, String kind, int page, int size, String sort) {
    check(
        Set.of("", "repair", "maintenance", "inspection", "anomaly", "spare").contains(kind),
        "事件类型无效");
    check(Set.of("asc", "desc").contains(sort), "事件排序无效");
    var e = equipment(id);
    var out = new ArrayList<Map<String, Object>>();
    // 各事件来源独立授权。指定类型时只查询该来源，避免无关单据触发逐项读取。
    if (kind.isBlank() || kind.equals("repair")) {
      for (var r : repairRows(e)) {
        append(out, "repair", r.getId(), r.getOrderNo(), "/repairs/" + r.getId());
      }
    }
    if (kind.isBlank() || kind.equals("maintenance")) {
      for (var t : maintenanceRows(id)) {
        append(
            out,
            "maintenance",
            ExperienceSupport.id(t.get("id")),
            text(t, "code"),
            "/maintenance/task/" + t.get("id"));
      }
    }
    if (kind.isBlank() || kind.equals("inspection")) {
      for (var t : inspectionRows(id)) {
        append(
            out,
            "inspection",
            ExperienceSupport.id(t.get("id")),
            text(t, "code"),
            "/inspection/task/" + t.get("id"));
      }
    }
    if (kind.isBlank() || kind.equals("anomaly")) {
      for (var a : equipmentAnomalies(id)) {
        for (var event : repository.listAnomalyEvents(auth.tenantId(), a.get("id"))) {
          event.put("kind", "anomaly");
          event.put("code", a.get("code"));
          event.put("url", "/anomalies/" + a.get("id"));
          out.add(event);
        }
      }
    }
    if (kind.isBlank() || kind.equals("spare")) {
      out.addAll(repository.listSpareEvents(auth.tenantId(), id));
    }
    Comparator<Map<String, Object>> order =
        Comparator.comparing((Map<String, Object> r) -> text(r, "time").replace('T', ' '))
            .thenComparing(r -> text(r, "kind"))
            .thenComparing(r -> text(r, "id"));
    if (sort.equals("desc")) order = order.reversed();
    var selected =
        out.stream()
            .filter(r -> kind.isBlank() || kind.equals(r.get("kind")))
            .sorted(order)
            .toList();
    return Map.of(
        "data",
        selected.stream()
            .skip((long) (Math.max(1, page) - 1) * Math.min(100, Math.max(1, size)))
            .limit(Math.min(100, Math.max(1, size)))
            .map(ExperienceSupport::view)
            .toList(),
        "total",
        selected.size());
  }

  void append(List<Map<String, Object>> out, String kind, long task, String code, String url) {
    String table =
        switch (kind) {
          case "repair" -> "eam_repair_event";
          case "maintenance" -> "eam_maintenance_event";
          default -> "eam_inspection_event";
        };
    String key = kind.equals("repair") ? "order_id" : "task_id";
    for (var e : repository.listTaskEvents(auth.tenantId(), task, table, key)) {
      e.put("kind", kind);
      e.put("code", code);
      e.put("url", url);
      out.add(e);
    }
  }
}
