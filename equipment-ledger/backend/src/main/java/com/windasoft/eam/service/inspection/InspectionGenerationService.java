package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.repository.system.NotificationRepository;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionGenerationService {
  private final NotificationRepository notificationPolicies;
  private final DailyArrangementService arrangements;
  private final DailyArrangementRepository dailyStorage;

  private final InspectionRepository repository;
  private final ObjectMapper json;
  private final PermissionService permissions;
  private final InspectionProjectService projectSource;
  private final InspectionViewAssembler views;
  private final InspectionTaskJournal journal;
  private final InspectionNumbers numbers;

  private record ProjectKey(List<String> references) {
    private ProjectKey {
      references = List.copyOf(references);
    }
  }

  /**
   * Reuses lookup results without mutating them, only during one plan-generation transaction. A new cache is
   * created for every invocation so later runs always observe current master data.
   */
  private static final class GenerationCache {
    private final Map<Long, Map<String, Object>> equipment = new HashMap<>();
    private final Map<ProjectKey, List<Map<String, Object>>> projects = new HashMap<>();
    private final Map<Long, Long> companyIds = new HashMap<>();
    private final Map<Long, String> departmentNames = new HashMap<>();
    private final Map<Long, String> typeNames = new HashMap<>();
  }

  public InspectionGenerationService(
      InspectionRepository repository,
      ObjectMapper json,
      PermissionService permissions,
      InspectionProjectService projectSource,
      InspectionViewAssembler views,
      InspectionTaskJournal journal,
      InspectionNumbers numbers,
      NotificationRepository notificationPolicies,
      DailyArrangementService arrangements,
      DailyArrangementRepository dailyStorage) {
    this.repository = repository;
    this.json = json;
    this.permissions = permissions;
    this.projectSource = projectSource;
    this.views = views;
    this.journal = journal;
    this.numbers = numbers;
    this.notificationPolicies = notificationPolicies;
    this.arrangements = arrangements;
    this.dailyStorage = dailyStorage;
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

  LocalDateTime advance(LocalDateTime anchor, long index, long period, String unit) {
    long n = Math.multiplyExact(index, period);
    return switch (unit) {
      case "日" -> anchor.plusDays(n);
      case "周" -> anchor.plusWeeks(n);
      case "月" -> anchor.plusMonths(n);
      case "年" -> anchor.plusYears(n);
      default ->
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.maintenance.message0008");
    };
  }

  boolean rest(Map<String, Object> plan, LocalDate date) {
    return DailyInspectionSchedule.rest(plan, date);
  }

  private int createTask(
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

  @Transactional(readOnly = true)
  public List<Map<String, Object>> generationTargets() {
    return repository.generationTargets();
  }

  @Transactional
  public int generate(long tenant, long planId) {
    var plans = repository.lockPlan(tenant, planId);
    if (plans.isEmpty()) return 0;
    var plan = plans.getFirst();
    var p = decode(plan.get("payload"));
    if (text(plan, "kind").equals("daily-plans")) {
      p.put("arrangements", dailyStorage.load(tenant, planId));
      p.put("workdays", dailyStorage.workdays(tenant, planId));
      p.put("_exemptions", dailyStorage.exemptions(tenant, planId));
      p.put(
          "_activeIds",
          dailyStorage.activePeople(tenant).stream()
              .map(u -> number(u.get("id")))
              .collect(java.util.stream.Collectors.toSet()));
      p.put("_teams", new HashMap<Long, List<Long>>());
    }
    var cache = new GenerationCache();
    boolean generationEnabled = yes(p.get("enabled"));
    if (generationEnabled && text(plan, "kind").equals("periodic-plans")) {
      var selectedType =
          views.definition(projectSource.raw("types", tenant, number(p.get("typeId")), true));
      generationEnabled = yes(selectedType.get("enabled"));
      String typeName = text(selectedType, "name");
      if (!typeName.isBlank()) cache.typeNames.put(number(p.get("typeId")), typeName);
    }
    int count = 0;
    if (generationEnabled) {
      var schedule = repository.lockSchedule(tenant, planId);
      LocalDateTime next = dateTime(schedule.get("next_at")),
          anchor = dateTime(schedule.get("anchor_at"));
      long index = number(schedule.get("cycle_index"));
      var details = rows(p.get("details"));
      if (text(plan, "kind").equals("periodic-plans")) {
        LocalDateTime start = periodicBoundary(p.get("startDate"), false),
            end = periodicBoundary(p.get("endDate"), true);
        for (int n = 0;
            n < 100 && readyForGeneration(next, p) && !next.isAfter(end);
            n++) {
          if (!next.isBefore(start))
            count += createTask(tenant, plan, p, details, next, "", "", null, cache);
          index++;
          next = advance(anchor, index, number(p.get("period")), text(p, "unit"));
        }
      } else {
        LocalDate start = LocalDate.parse(text(p, "startDate")),
            end = LocalDate.parse(text(p, "endDate"));
        LocalDateTime deadline = generationDeadline(p);
        for (int n = 0;
            n < 100
                && !next.toLocalDate().isAfter(deadline.toLocalDate())
                && !next.toLocalDate().isAfter(end);
            n++) {
          LocalDate date = next.toLocalDate();
          boolean allStarted = true;
          if (!date.isBefore(start)
              && (!rest(p, date) || strings(p.get("workdays")).contains(date.toString()))) {
            if (!rows(p.get("arrangements")).isEmpty()) {
              for (var a : rows(p.get("arrangements")))
                for (var occurrence : DailyInspectionSchedule.on(p, a, date)) {
                  if (!readyForGeneration(occurrence.start(), p)
                      || (text(a, "personMode").equals("open")
                          && occurrence.start().isAfter(now()))) {
                    allStarted = false;
                    continue;
                  }
                  if (count >= 2000) {
                    allStarted = false;
                    continue;
                  }
                  var adjusted = adjust(p, a, occurrence, "");
                  for (var detail : arrangements.details(p, a))
                    count +=
                        createTask(
                            tenant,
                            plan,
                            adjusted,
                            List.of(detail),
                            occurrence.start(),
                            occurrence.key(),
                            occurrence.name(),
                            occurrence.end(),
                            cache);
                }
            } else {
              for (var shift : rows(p.get("shifts"))) {
                LocalDateTime due = date.atTime(LocalTime.parse(text(shift, "start"))),
                    shiftEnd = date.atTime(LocalTime.parse(text(shift, "end")));
                if (!shiftEnd.isAfter(due)) shiftEnd = shiftEnd.plusDays(1);
                if (!readyForGeneration(due, p)) {
                  allStarted = false;
                  continue;
                }
                for (var detail : details)
                  count +=
                      createTask(
                          tenant,
                          plan,
                          p,
                          List.of(detail),
                          due,
                          text(shift, "id"),
                          text(shift, "name"),
                          shiftEnd,
                          cache);
              }
            }
          }
          if (!allStarted) break;
          next = date.plusDays(1).atStartOfDay();
        }
      }
      repository.advanceSchedule(next, index, tenant, planId);
    }
    maintainTasks(tenant, planId);
    return count;
  }

  private Map<String, Object> adjust(
      Map<String, Object> plan,
      Map<String, Object> a,
      DailyInspectionSchedule.Occurrence o,
      String eventKey) {
    var p = new LinkedHashMap<String, Object>(plan);
    p.put("_arrangement", a);
    p.put("_businessDate", o.businessDate().toString());
    p.put("_rosterShift", o.rosterShiftId());
    p.put("_windowEnd", o.end().toString());
    p.put("_eventKey", eventKey);
    p.put("personMode", "指定负责人");
    p.put("allowLate", yes(a.get("allowLate")));
    p.put("onlyAbnormal", false);
    return p;
  }

  public Map<String, Object> preview(long tenant, Map<String, Object> plan, int days) {
    require(
        days == 7 || days == 30,
        com.windasoft.eam.common.MessageContext.text("daily.error.previewDays"));
    LocalDate start = LocalDate.parse(text(plan, "startDate"));
    if (start.isBefore(now().toLocalDate())) start = now().toLocalDate();
    LocalDate end = start.plusDays(days - 1), last = LocalDate.parse(text(plan, "endDate"));
    if (end.isAfter(last)) end = last;
    var result = new ArrayList<Map<String, Object>>();
    long count = 0, unstaffed = 0;
    for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1))
      for (var a : rows(plan.get("arrangements")))
        for (var o : DailyInspectionSchedule.on(plan, a, date))
          for (var detail : arrangements.details(plan, a)) {
            require(
                ++count <= 100000,
                com.windasoft.eam.common.MessageContext.text("daily.error.previewLimit"));
            var people =
                arrangements.ownersAt(tenant, plan, a, detail, date, o.rosterShiftId(), o.start());
            if (people.isEmpty() && !arrangements.open(a, detail)) unstaffed++;
            if (result.size() < 500)
              result.add(
                  Map.of(
                      "arrangementName",
                      text(a, "name"),
                      "equipmentId",
                      text(detail, "equipmentId"),
                      "businessDate",
                      date.toString(),
                      "start",
                      o.start().toString(),
                      "end",
                      o.end().toString(),
                      "candidateCount",
                      people.size()));
          }
    return Map.of(
        "rows",
        result,
        "total",
        count,
        "unstaffed",
        unstaffed,
        "limit",
        500,
        "from",
        start.toString(),
        "to",
        end.toString());
  }

  /** Retained endpoint fails explicitly for old clients; tasks now require a shift. */
  public Map<String, Object> manualEvent(
      long tenant, Map<String, Object> plan, Map<String, Object> body) {
    throw new com.windasoft.eam.common.LocalizedArgumentException("daily.error.shiftOnly");
  }

  void maintainTasks(long tenant, long planId) {
    for (var task : repository.lockOpenPlanTasks(tenant, planId)) {
      long id = number(task.get("id"));
      var snap = decode(task.get("snapshot"));
      String state = text(task, "status");
      LocalDateTime due = dateTime(task.get("due_at")), expires = dateTime(task.get("expires_at"));
      if (state.equals(InspectionTaskStatus.NOT_STARTED.code()) && !due.isAfter(now())) {
        repository.activateTask(tenant, id);
        String previous = state;
        state = InspectionTaskStatus.PENDING.code();
        journal.event(
            tenant, id, "任务到期开始", null, "系统",
            Map.of("原状态", previous, "新状态", state));
      }
      if (now().isAfter(expires)
          && Set.of(
                  InspectionTaskStatus.NOT_STARTED.code(), InspectionTaskStatus.PENDING.code())
              .contains(state)) {
        String next =
            text(task, "kind").equals("daily") && task.get("arrangement_id") != null
                ? InspectionTaskStatus.EXPIRED.code()
                : (yes(snap.get("allowLate"))
                    ? InspectionTaskStatus.EXPIRED.code()
                    : InspectionTaskStatus.EXPIRED_CANCELLED.code());
        String previous = state;
        repository.changeTaskStatus(next, tenant, id);
        journal.event(
            tenant, id, next, null, "系统",
            Map.of("原状态", previous, "新状态", next));
        if (next.equals(InspectionTaskStatus.EXPIRED_CANCELLED.code()))
          journal.closeTodos(tenant, id);
        state = next;
      }
      boolean newReminders=notificationPolicies.replacesLegacy(tenant,text(task,"kind"),planId);
      if (!newReminders && text(task, "kind").equals("daily")
          && task.get("follow_up_user_id") != null
          && now().isAfter(expires)
          && Set.of(
                  InspectionTaskStatus.PENDING.code(),
                  InspectionTaskStatus.EXPIRED.code(),
                  InspectionTaskStatus.IN_PROGRESS.code(),
                  InspectionTaskStatus.SUSPENDED.code(),
                  InspectionTaskStatus.REVIEW_REJECTED.code())
              .contains(state))
        journal.notify(
            tenant,
            id,
            List.of(number(task.get("follow_up_user_id"))),
            "点巡检超期提醒",
            text(snap, "equipmentName"),
            false,
            true);
      boolean lateExecutionAllowed = yes(snap.get("allowLate"));
      if (state.equals(InspectionTaskStatus.EXPIRED.code()) && !lateExecutionAllowed)
        journal.closeTodos(tenant, id);
      if (state.equals(InspectionTaskStatus.PENDING.code())
          || (state.equals(InspectionTaskStatus.EXPIRED.code()) && lateExecutionAllowed))
        journal.notify(
            tenant,
            id,
            ids(snap.get("owners")),
            "点巡检待执行",
            text(snap, "equipmentName"),
            true,
            false);
      if (state.equals(InspectionTaskStatus.NOT_STARTED.code())
          && number(snap.get("remindHours")) > 0
          && !now().isBefore(shiftDuration(due, snap, "remindHours", "remindUnit", -1)))
        journal.notify(
            tenant,
            id,
            ids(snap.get("owners")),
            "点巡检开始提醒",
            text(snap, "equipmentName"),
            false,
            true);
      if (!newReminders
          && Set.of(
                  InspectionTaskStatus.PENDING.code(),
                  InspectionTaskStatus.IN_PROGRESS.code(),
                  InspectionTaskStatus.SUSPENDED.code(),
                  InspectionTaskStatus.REVIEW_REJECTED.code())
              .contains(state)
          && number(snap.get("expireRemindHours")) > 0
          && now().isBefore(expires)
          && !now()
              .isBefore(shiftDuration(expires, snap, "expireRemindHours", "expireRemindUnit", -1)))
        journal.notify(
            tenant,
            id,
            ids(snap.get("owners")),
            "点巡检即将过期",
            text(snap, "equipmentName"),
            false,
            true);
    }
  }

  Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0012");
    }
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0012");
    }
  }

  public int refreshAssignments(long tenant, long planId, long actor) {
    return pendingAssignments(tenant, planId, actor, null, true);
  }

  public int assignmentImpact(long tenant, long planId, Map<String, Object> draft) {
    return pendingAssignments(tenant, planId, 0, draft, false);
  }

  private int pendingAssignments(
      long tenant, long planId, long actor, Map<String, Object> draft, boolean apply) {
    var locked = repository.lockPlan(tenant, planId);
    if (locked.isEmpty()) return 0;
    var p = draft == null ? decode(locked.getFirst().get("payload")) : draft;
    var current =
        draft == null ? dailyStorage.load(tenant, planId) : rows(draft.get("arrangements"));
    int changed = 0;
    for (var task : repository.lockOpenPlanTasks(tenant, planId)) {
      if (!Set.of(
              InspectionTaskStatus.NOT_STARTED.code(),
              InspectionTaskStatus.PENDING.code(),
              InspectionTaskStatus.EXPIRED.code())
          .contains(text(task, "status"))
          || task.get("owner_id") != null) continue;
      var result = decode(task.get("result"));
      if (!text(result, "startedAt").isBlank()
          || journal.hasAction(tenant, number(task.get("id")), "转派")) continue;
      var a =
          current.stream()
              .filter(x -> text(x, "id").equals(text(task, "arrangement_id")))
              .findFirst()
              .orElse(null);
      if (a == null) continue;
      var detail =
          arrangements.details(p, a).stream()
              .filter(d -> number(d.get("equipmentId")) == number(task.get("equipment_id")))
              .findFirst()
              .orElse(null);
      if (detail == null) continue;
      var at = dateTime(task.get("due_at"));
      String slot = "";
      if (Set.of("shift", "slots").contains(text(a, "frequency"))) {
        String key = text(task, "shift_id"), prefix = text(a, "id") + ":";
        if (!key.startsWith(prefix)) continue;
        slot = key.substring(prefix.length());
        if (rows(a.get("slots")).stream().noneMatch(x -> key.equals(prefix + text(x, "id"))))
          continue;
      }
      var users =
          arrangements.ownersAt(
              tenant, p, a, detail, LocalDate.parse(text(task, "business_date")), slot, at);
      String mode = arrangements.open(a, detail) ? "现场直接点检" : "指定负责人";
      var old = decode(task.get("snapshot"));
      Long follow = nullable(a.get("followUpUserId"));
      if (new HashSet<>(ids(old.get("owners"))).equals(new HashSet<>(users))
          && text(old, "personMode").equals(mode)
          && Objects.equals(nullable(task.get("follow_up_user_id")), follow)) continue;
      if (apply) {
        dailyStorage.reassignPending(tenant, number(task.get("id")), users, mode, follow);
        journal.closeTodos(tenant, number(task.get("id")));
        journal.event(
            tenant,
            number(task.get("id")),
            "调整执行人员",
            actor,
            "",
            Map.of(
                "原人员",
                ids(old.get("owners")),
                "新人员",
                users,
                "原方式",
                text(old, "personMode"),
                "新方式",
                mode));
      }
      changed++;
    }
    return changed;
  }
}
