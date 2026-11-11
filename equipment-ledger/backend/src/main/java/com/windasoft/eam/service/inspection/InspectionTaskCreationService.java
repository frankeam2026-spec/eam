package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.executionTodoEligible;
import static com.windasoft.eam.common.inspection.InspectionSupport.ids;
import static com.windasoft.eam.common.inspection.InspectionSupport.now;
import static com.windasoft.eam.common.inspection.InspectionSupport.nullable;
import static com.windasoft.eam.common.inspection.InspectionSupport.number;
import static com.windasoft.eam.common.inspection.InspectionSupport.rows;
import static com.windasoft.eam.common.inspection.InspectionSupport.shiftDuration;
import static com.windasoft.eam.common.inspection.InspectionSupport.text;
import static com.windasoft.eam.common.inspection.InspectionSupport.yes;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import com.windasoft.eam.dto.inspection.ScheduledInspectionExemption;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 在生成事务内装配任务快照并写入关联、履历与初始通知；不推进计划调度游标。 */
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

  /** 缓存仅属于本轮计划生成事务，不跨请求复用，避免后续轮次看不到人员、项目或免检变更。 */
  static final class GenerationCache {
    private final Map<Long, Map<String, Object>> equipment = new HashMap<>();
    private final Map<ProjectKey, List<Map<String, Object>>> projects = new HashMap<>();
    private final Map<Long, Long> companyIds = new HashMap<>();
    private final Map<Long, String> departmentNames = new HashMap<>();
    private final Map<Long, String> typeNames = new HashMap<>();

    private List<ScheduledInspectionExemption> exemptions = List.of();

    void rememberExemptions(List<ScheduledInspectionExemption> periods) {
      exemptions = List.copyOf(periods);
    }

    void rememberTypeName(long typeId, String name) {
      typeNames.put(typeId, name);
    }
  }

  List<Long> effectiveOwners(
      long tenant,
      Map<String, Object> plan,
      Map<String, Object> detail,
      LocalDate date,
      String shiftId) {
    if (text(plan, "personMode").equals("任何人可点检"))
      return repository.listActiveUsers(tenant).stream().map(r -> number(r.get("id"))).toList();
    if (text(plan, "personMode").equals("按照排班表"))
      return rows(plan.get("roster")).stream()
          .filter(
              r -> text(r, "date").equals(date.toString()) && text(r, "shiftId").equals(shiftId))
          .flatMap(r -> ids(r.get("users")).stream())
          .distinct()
          .toList();
    return ids(detail.get("owners")).isEmpty()
        ? ids(plan.get("owners"))
        : ids(detail.get("owners"));
  }

  /** 在调用方持有计划及调度锁的事务内创建一次发生，任务快照、关联、事件和初始通知参与同一提交。 已有发生先返回0，不再分配编号或重复写事件；缓存由本轮生成传入，不跨生成请求保留。 */
  public int createTask(
      long tenant,
      Map<String, Object> plan,
      Map<String, Object> p,
      List<Map<String, Object>> details,
      InspectionTaskCreationContext context,
      GenerationCache cache) {
    var daily = context.daily();
    LocalDateTime due = context.due();
    String shiftId = context.shiftId(), shiftName = context.shiftName();
    LocalDateTime shiftEnd = context.shiftEnd();
    boolean allowLate =
        yes(daily == null ? p.get("allowLate") : daily.definition().get("allowLate"));
    String kind = text(plan, "kind").equals("daily-plans") ? "daily" : "periodic";
    long equipmentId = kind.equals("daily") ? number(details.getFirst().get("equipmentId")) : 0;
    long planId = number(plan.get("id"));
    if (repository.countDailyTask(tenant, planId, equipmentId, shiftId, due) > 0) return 0;
    var snap = new LinkedHashMap<String, Object>(p);
    snap.remove("details");
    snap.remove("roster");
    snap.remove("arrangements");
    snap.remove("_activeIds");
    snap.remove("_teams");
    if (daily != null) {
      snap.put("allowLate", allowLate);
      snap.put("onlyAbnormal", false);
    }
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
      else if (daily != null) {
        owners.addAll(
            arrangements.ownersAt(
                tenant,
                p,
                daily.definition(),
                detail,
                daily.businessDate(),
                daily.rosterShiftId(),
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
    if (daily == null
        && kind.equals("daily")
        && text(p, "personMode").equals("按照排班表")
        && owners.isEmpty()) {
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
        equipmentList.stream().map(e -> text(e, "name")).collect(Collectors.joining("、")));
    snap.put(
        "equipmentCode",
        equipmentList.stream().map(e -> text(e, "code")).collect(Collectors.joining("、")));
    Long departmentId = nullable(plan.get("department_id"));
    String departmentName =
        cache.departmentNames.computeIfAbsent(
            departmentId, id -> Objects.toString(repository.findOrganizationName(tenant, id), ""));
    snap.put("department", departmentName);
    boolean open = daily != null && arrangements.open(daily.definition(), details.getFirst());
    snap.put(
        "personMode",
        open
            ? "现场直接点检"
            : (daily != null || kind.equals("periodic") ? "指定负责人" : text(p, "personMode")));
    ScheduledInspectionExemption exemption = null;
    if (daily != null) {
      exemption =
          cache.exemptions.stream()
              .filter(
                  e ->
                      e.equipmentId() == equipmentId
                          && !due.isBefore(e.startAt())
                          && due.isBefore(e.endAt()))
              .findFirst()
              .orElse(null);
    }
    long task = IdWorker.getId();
    String code = numbers.nextCode(tenant, kind.equals("daily") ? "DJ" : "XJ");
    LocalDateTime expires = shiftDuration(due, p, "validHours", "validUnit", 1);
    if (yes(p.get("useShiftEnd")) && shiftEnd != null) expires = shiftEnd;
    String status =
        due.isAfter(now())
            ? InspectionTaskStatus.NOT_STARTED.code()
            : InspectionTaskStatus.PENDING.code();
    if (daily != null) {
      expires = context.shiftEnd();
      if (now().isAfter(expires)) {
        status = InspectionTaskStatus.EXPIRED.code();
      }
    }
    Long company;
    if (open) {
      var eq = cache.equipment.get(equipmentId);
      company = nullable(eq.get("company_org_id"));
    } else {
      if (!cache.companyIds.containsKey(departmentId))
        cache.companyIds.put(departmentId, permissions.companyOfOrganization(tenant, departmentId));
      company = cache.companyIds.get(departmentId);
    }
    Object taskDepartment =
        open
            ? cache.equipment.get(equipmentId).get("department_org_id")
            : plan.get("department_id");
    if (exemption != null) {
      status = InspectionTaskStatus.EXEMPT.code();
    }
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
    if (daily != null) {
      var a = daily.definition();
      Long followUp = nullable(a.get("followUpUserId"));
      // 当前只按班次生成，不再携带已停用的人工事件键；保留任务列的空字符串语义。
      dailyStorage.taskMetadata(tenant, task, a, daily.businessDate().toString(), "");
      if (exemption != null) {
        dailyStorage.scheduledExemption(tenant, task, exemption);
      }
      if (exemption == null && owners.isEmpty() && !open && followUp != null && followUp > 0) {
        journal.notify(
            tenant, task, List.of(followUp), "点巡检待安排", text(snap, "equipmentName"), true, false);
      }
    }
    journal.event(
        tenant,
        task,
        "生成任务",
        null,
        "系统",
        Map.of("计划", plan.get("name"), "开始时间", due.toString(), "班次", shiftName, "新状态", status));
    // 资格须在写入公共通知来源之前判定，事后关闭待办不能撤回已入队的通知。
    if (executionTodoEligible(status, due, expires, allowLate, now())) {
      journal.notify(
          tenant,
          task,
          new ArrayList<>(owners),
          "点巡检待执行",
          text(snap, "equipmentName"),
          true,
          false);
    }
    return 1;
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new LocalizedArgumentException("server.inspection.message0012");
    }
  }
}
