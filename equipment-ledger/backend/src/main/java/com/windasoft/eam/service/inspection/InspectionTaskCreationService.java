package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.system.PermissionService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Builds and persists task snapshots and the business records created with each task. */
@Service
public class InspectionTaskCreationService {
  private final InspectionRepository repository;
  private final ObjectMapper json;
  private final PermissionService permissions;
  private final InspectionProjectService projectSource;
  private final InspectionTaskJournal journal;
  private final InspectionNumbers numbers;
  private final DailyArrangementService arrangements;
  private final DailyArrangementRepository dailyStorage;

  public InspectionTaskCreationService(
      InspectionRepository repository,
      ObjectMapper json,
      PermissionService permissions,
      InspectionProjectService projectSource,
      InspectionTaskJournal journal,
      InspectionNumbers numbers,
      DailyArrangementService arrangements,
      DailyArrangementRepository dailyStorage) {
    this.repository = repository;
    this.json = json;
    this.permissions = permissions;
    this.projectSource = projectSource;
    this.journal = journal;
    this.numbers = numbers;
    this.arrangements = arrangements;
    this.dailyStorage = dailyStorage;
  }

  private record ProjectKey(List<String> references) {
    private ProjectKey {
      references = List.copyOf(references);
    }
  }

  /**
   * Reuses lookup results without mutating them, only during one plan-generation transaction. A new cache is
   * created for every invocation so later runs always observe current master data.
   */
  static final class GenerationCache {
    private final Map<Long, Map<String, Object>> equipment = new HashMap<>();
    private final Map<ProjectKey, List<Map<String, Object>>> projects = new HashMap<>();
    private final Map<Long, Long> companyIds = new HashMap<>();
    private final Map<Long, String> departmentNames = new HashMap<>();
    private final Map<Long, String> typeNames = new HashMap<>();
  }


  public int createTask(
      long tenant,
      Map<String, Object> plan,
      Map<String, Object> p,
      List<Map<String, Object>> details,
      LocalDateTime due,
      String shiftId,
      String shiftName,
      LocalDateTime shiftEnd,
      GenerationCache cache) {
    String kind = text(plan, "kind").equals("daily-plans") ? "daily" : "periodic";
    long equipmentId = kind.equals("daily") ? number(details.getFirst().get("equipmentId")) : 0;
    long planId = number(plan.get("id"));
    if (repository.countDailyTask(tenant, planId, equipmentId, shiftId, due) > 0) return 0;
    var snap = new LinkedHashMap<String, Object>(p);
    snap.remove("details");
    snap.remove("roster");
    snap.remove("_arrangement");
    snap.remove("arrangements");
    snap.remove("_eventKey");
    snap.remove("_activeIds");
    snap.remove("_teams");
    snap.remove("_exemptions");
    snap.remove("_businessDate");
    snap.remove("_rosterShift");
    snap.remove("_windowEnd");
    var equipmentList = new ArrayList<Map<String, Object>>();
    var items = new ArrayList<Map<String, Object>>();
    var owners = new LinkedHashSet<Long>();
    var reviewers = new LinkedHashSet<Long>(ids(p.get("reviewers")));
    for (var detail : details) {
      long detailEquipmentId = number(detail.get("equipmentId"));
      var eq =
          cache.equipment.computeIfAbsent(
              detailEquipmentId, id -> projectSource.equipment(tenant, id));
      if (kind.equals("periodic")) owners.addAll(ids(p.get("owners")));
      else if (p.get("_arrangement") instanceof Map<?, ?>) {
        var a = (Map<String, Object>) p.get("_arrangement");
        owners.addAll(
            arrangements.ownersAt(
                tenant,
                p,
                a,
                detail,
                LocalDate.parse(text(p, "_businessDate")),
                text(p, "_rosterShift"),
                due));
      } else owners.addAll(effectiveOwners(tenant, p, detail, due.toLocalDate(), shiftId));
      reviewers.addAll(ids(detail.get("reviewers")));
      equipmentList.add(
          Map.of(
              "id",
              eq.get("id").toString(),
              "code",
              eq.get("equipment_code"),
              "name",
              eq.get("name"),
              "model",
              Objects.toString(eq.get("model"), ""),
              "department",
              Objects.toString(eq.get("department"), ""),
              "functionLocation",
              Objects.toString(eq.get("function_location"), "")));
      var projectKey =
          new ProjectKey(
              rows(detail.get("projectRefs")).stream()
                  .map(ref -> text(ref, "standardId") + ":" + text(ref, "itemId"))
                  .toList());
      var projects =
          cache.projects.computeIfAbsent(
              projectKey, ignored -> projectSource.projects(tenant, detail, false));
      for (var project : projects) {
        var item = new LinkedHashMap<>(project);
        item.put("id", eq.get("id") + "/" + text(item, "id"));
        item.put("equipmentId", eq.get("id").toString());
        items.add(item);
      }
    }
    if (kind.equals("daily") && text(p, "personMode").equals("按照排班表") && owners.isEmpty()) {
      if (yes(p.get("skipUnstaffed"))) return 0;
      owners.addAll(ids(p.get("owners")));
    }
    var shuffled = new ArrayList<>(items);
    Collections.shuffle(shuffled);
    for (int i = 0; i < Math.min(number(p.get("randomPhotoCount")), shuffled.size()); i++)
      shuffled.get(i).put("photoRequired", true);
    snap.put("equipment", equipmentList);
    snap.put("items", items);
    snap.put("owners", owners.stream().map(Object::toString).toList());
    snap.put("reviewers", reviewers.stream().map(Object::toString).toList());
    snap.put("planName", plan.get("name"));
    snap.put("planCode", plan.get("code"));
    if (kind.equals("periodic")) {
      snap.put("typeId", text(p, "typeId"));
      long typeId = number(p.get("typeId"));
      String typeName =
          cache.typeNames.computeIfAbsent(
              typeId, id -> text(projectSource.raw("types", tenant, id, false), "name"));
      snap.put("typeName", typeName);
    } else {
      snap.remove("typeId");
      snap.remove("typeName");
    }
    snap.put("shiftName", shiftName);
    snap.put("shiftDate", due.toLocalDate().toString());
    snap.put(
        "equipmentName",
        equipmentList.stream()
            .map(e -> text(e, "name"))
            .collect(java.util.stream.Collectors.joining("、")));
    snap.put(
        "equipmentCode",
        equipmentList.stream()
            .map(e -> text(e, "code"))
            .collect(java.util.stream.Collectors.joining("、")));
    Long departmentId = nullable(plan.get("department_id"));
    String departmentName =
        cache.departmentNames.computeIfAbsent(
            departmentId,
            id -> Objects.toString(repository.findOrganizationName(tenant, id), ""));
    snap.put("department", departmentName);
    boolean open =
        p.get("_arrangement") instanceof Map<?, ?>
            && arrangements.open((Map<String, Object>) p.get("_arrangement"), details.getFirst());
    snap.put(
        "personMode",
        open ? "现场直接点检" : (kind.equals("periodic") ? "指定负责人" : text(p, "personMode")));
    Map<String, Object> exemption = Map.of();
    if (p.containsKey("_arrangement")) {
      var periods =
          (List<Map<String, Object>>)
              p.computeIfAbsent("_exemptions", ignored -> dailyStorage.exemptions(tenant, planId));
      exemption =
          periods.stream()
              .filter(
                  e ->
                      number(e.get("equipmentId")) == equipmentId
                          && !due.isBefore(dateTime(e.get("startAt")))
                          && due.isBefore(dateTime(e.get("endAt"))))
              .findFirst()
              .orElse(Map.of());
    }
    long task = IdWorker.getId();
    String code = numbers.nextCode(tenant, kind.equals("daily") ? "DJ" : "XJ");
    LocalDateTime expires = shiftDuration(due, p, "validHours", "validUnit", 1);
    if (yes(p.get("useShiftEnd")) && shiftEnd != null) expires = shiftEnd;
    String status =
        due.isAfter(now())
            ? InspectionTaskStatus.NOT_STARTED.code()
            : InspectionTaskStatus.PENDING.code();
    if (p.containsKey("_arrangement")) {
      expires = dateTime(p.get("_windowEnd"));
      if (now().isAfter(expires)) status = InspectionTaskStatus.EXPIRED.code();
      snap.put("onlyAbnormal", false);
    }
    Long company;
    if (open) {
      var eq = cache.equipment.get(equipmentId);
      company = nullable(eq.get("company_org_id"));
    } else {
      if (!cache.companyIds.containsKey(departmentId))
        cache.companyIds.put(
            departmentId, permissions.companyOfOrganization(tenant, departmentId));
      company = cache.companyIds.get(departmentId);
    }
    Object taskDepartment =
        open
            ? cache.equipment.get(equipmentId).get("department_org_id")
            : plan.get("department_id");
    if (!exemption.isEmpty()) status = InspectionTaskStatus.EXEMPT.code();
    repository.insertTask(
        task,
        tenant,
        kind,
        code,
        planId,
        kind.equals("daily") ? null : number(p.get("typeId")),
        equipmentId,
        shiftId,
        company,
        taskDepartment,
        due,
        expires,
        status,
        encode(snap));
    if (p.get("_arrangement") instanceof Map<?, ?>) {
      var a = (Map<String, Object>) p.get("_arrangement");
      dailyStorage.taskMetadata(tenant, task, a, text(p, "_businessDate"), text(p, "_eventKey"));
      if (!exemption.isEmpty()) dailyStorage.scheduledExemption(tenant, task, exemption);
      if (exemption.isEmpty()
          && owners.isEmpty()
          && !open
          && nullable(a.get("followUpUserId")) != null)
        journal.notify(
            tenant,
            task,
            List.of(number(a.get("followUpUserId"))),
            "点巡检待安排",
            text(snap, "equipmentName"),
            true,
            false);
    }
    journal.event(
        tenant,
        task,
        "生成任务",
        null,
        "系统",
        Map.of(
            "计划", plan.get("name"),
            "开始时间", due.toString(),
            "班次", shiftName,
            "新状态", status));
    if (exemption.isEmpty() && !due.isAfter(now()))
      journal.notify(
          tenant,
          task,
          new ArrayList<>(owners),
          "点巡检待执行",
          text(snap, "equipmentName"),
          true,
          false);
    return 1;
  }


  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0012");
    }
  }
}
