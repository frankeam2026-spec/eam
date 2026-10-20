package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionGenerationService {
  @org.springframework.beans.factory.annotation.Autowired
  private DailyArrangementService arrangements;

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.repository.inspection.DailyArrangementRepository dailyStorage;

  private final InspectionRepository repository;
  private final ObjectMapper json;
  private final PermissionService permissions;
  private final InspectionProjectService projectSource;
  private final InspectionViewAssembler views;
  private final InspectionTaskJournal journal;
  private final InspectionNumbers numbers;

  public InspectionGenerationService(
      InspectionRepository repository,
      ObjectMapper json,
      PermissionService permissions,
      InspectionProjectService projectSource,
      InspectionViewAssembler views,
      InspectionTaskJournal journal,
      InspectionNumbers numbers) {
    this.repository = repository;
    this.json = json;
    this.permissions = permissions;
    this.projectSource = projectSource;
    this.views = views;
    this.journal = journal;
    this.numbers = numbers;
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
    return strings(plan.get("holidays")).contains(date.toString())
        || ids(plan.get("restDays"))
            .contains(
                (long)
                    (text(plan, "restCycle").equals("按月")
                        ? date.getDayOfMonth()
                        : date.getDayOfWeek().getValue()));
  }

  int createTask(
      long tenant,
      Map<String, Object> plan,
      Map<String, Object> p,
      List<Map<String, Object>> details,
      LocalDateTime due,
      String shiftId,
      String shiftName,
      LocalDateTime shiftEnd) {
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
      var eq = projectSource.equipment(tenant, number(detail.get("equipmentId")));
      if (kind.equals("periodic")) owners.addAll(ids(p.get("owners")));
      else if (p.get("_arrangement") instanceof Map<?, ?>) {
        var a = (Map<String, Object>) p.get("_arrangement");
        owners.addAll(
            arrangements.owners(
                tenant,
                p,
                a,
                detail,
                LocalDate.parse(text(p, "_businessDate")),
                text(p, "_rosterShift")));
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
      for (var project : projectSource.projects(tenant, detail, false)) {
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
      snap.put("typeName", projectSource.raw("types", tenant, number(p.get("typeId")), false).get("name"));
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
    snap.put(
        "department",
        Objects.toString(repository.findOrganizationName(tenant, plan.get("department_id")), ""));
    snap.put("personMode", kind.equals("periodic") ? "指定负责人" : text(p, "personMode"));
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
    String status = due.isAfter(now()) ? "未开始" : "待执行";
    if (p.containsKey("_arrangement")) {
      expires = dateTime(p.get("_windowEnd"));
      if (now().isAfter(expires)) status = "已过期";
      snap.put("onlyAbnormal", false);
    }
    var org = repository.findOrganizationParent(tenant, plan.get("department_id"));
    Long company = permissions.companyOfOrganization(tenant, nullable(plan.get("department_id")));
    if (!exemption.isEmpty()) status = "已免检";
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
        plan.get("department_id"),
        due,
        expires,
        status,
        encode(snap));
    if (p.get("_arrangement") instanceof Map<?, ?>) {
      var a = (Map<String, Object>) p.get("_arrangement");
      dailyStorage.taskMetadata(tenant, task, a, text(p, "_businessDate"), text(p, "_eventKey"));
      if (!exemption.isEmpty()) dailyStorage.scheduledExemption(tenant, task, exemption);
      if (exemption.isEmpty() && owners.isEmpty())
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
        Map.of("计划", plan.get("name"), "开始时间", due.toString(), "班次", shiftName));
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
    int count = 0;
    if (yes(p.get("enabled"))) {
      if (text(plan, "kind").equals("periodic-plans")) {
        var selectedType = views.definition(projectSource.raw("types", tenant, number(p.get("typeId")), true));
        if (!yes(selectedType.get("enabled"))) return 0;
      }
      var schedule = repository.lockSchedule(tenant, planId);
      LocalDateTime next = dateTime(schedule.get("next_at")),
          anchor = dateTime(schedule.get("anchor_at"));
      long index = number(schedule.get("cycle_index"));
      LocalDate start = LocalDate.parse(text(p, "startDate")),
          end = LocalDate.parse(text(p, "endDate"));
      var details = rows(p.get("details"));
      if (text(plan, "kind").equals("periodic-plans")) {
        for (int n = 0;
            n < 100 && readyForGeneration(next, p) && !next.toLocalDate().isAfter(end);
            n++) {
          if (!next.toLocalDate().isBefore(start))
            count += createTask(tenant, plan, p, details, next, "", "", null);
          index++;
          next = advance(anchor, index, number(p.get("period")), text(p, "unit"));
        }
      } else {
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
                  if (!readyForGeneration(occurrence.start(), p)) {
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
                            occurrence.end());
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
                          shiftEnd);
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
            var people = arrangements.owners(tenant, plan, a, detail, date, o.rosterShiftId());
            if (people.isEmpty()) unstaffed++;
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

  public Map<String, Object> manualEvent(
      long tenant, Map<String, Object> plan, Map<String, Object> body) {
    var p = decode(plan.get("payload"));
    require(
        yes(p.get("enabled")),
        com.windasoft.eam.common.MessageContext.text("daily.error.disabled"));
    var a =
        dailyStorage.load(tenant, number(plan.get("id"))).stream()
            .filter(
                v ->
                    text(v, "id").equals(text(body, "arrangementId"))
                        && text(v, "frequency").equals("event")
                        && yes(v.get("enabled")))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        com.windasoft.eam.common.MessageContext.text(
                            "daily.error.eventUnavailable")));
    String key = text(body, "idempotencyKey");
    require(
        key.matches("[a-zA-Z0-9-]{16,36}"),
        com.windasoft.eam.common.MessageContext.text("daily.error.key"));
    LocalDateTime at = LocalDateTime.parse(text(body, "eventAt"));
    require(
        !at.isAfter(now())
            && !at.toLocalDate().isBefore(LocalDate.parse(text(p, "startDate")))
            && !at.toLocalDate().isAfter(LocalDate.parse(text(p, "endDate"))),
        com.windasoft.eam.common.MessageContext.text("daily.error.eventDate"));
    var chosen =
        arrangements.details(p, a).stream()
            .filter(
                d ->
                    yes(body.get("_supervisor"))
                        || arrangements
                            .owners(tenant, p, a, d, at.toLocalDate(), "")
                            .contains(number(body.get("_actorId"))))
            .toList();
    var requested = new HashSet<>(ids(body.get("equipmentIds")));
    require(
        !requested.isEmpty()
            && chosen.stream()
                .map(d -> number(d.get("equipmentId")))
                .toList()
                .containsAll(requested),
        com.windasoft.eam.common.MessageContext.text("daily.error.noEligibleEquipment"));
    chosen = chosen.stream().filter(d -> requested.contains(number(d.get("equipmentId")))).toList();
    var prior = dailyStorage.manualTasks(tenant, number(a.get("id")), key);
    if (!prior.isEmpty()) {
      require(
          prior.stream()
              .map(t -> number(t.get("equipment_id")))
              .collect(java.util.stream.Collectors.toSet())
              .equals(requested),
          com.windasoft.eam.common.MessageContext.text("daily.error.eventConflict"));
      for (var old : prior)
        require(
            dateTime(old.get("due_at")).equals(at),
            com.windasoft.eam.common.MessageContext.text("daily.error.eventConflict"));
      return Map.of(
          "count", prior.size(), "taskIds", prior.stream().map(t -> text(t, "id")).toList());
    }
    var o =
        new DailyInspectionSchedule.Occurrence(
            text(a, "id") + ":" + key,
            text(a, "name"),
            at.toLocalDate(),
            at,
            at.plusMinutes(number(a.get("windowMinutes"))),
            "");
    int count = 0;
    for (var detail : chosen)
      count +=
          createTask(
              tenant, plan, adjust(p, a, o, key), List.of(detail), at, o.key(), o.name(), o.end());
    return Map.of(
        "count",
        count,
        "taskIds",
        dailyStorage.manualTasks(tenant, number(a.get("id")), key).stream()
            .map(t -> text(t, "id"))
            .toList());
  }

  void maintainTasks(long tenant, long planId) {
    for (var task : repository.lockOpenPlanTasks(tenant, planId)) {
      long id = number(task.get("id"));
      var snap = decode(task.get("snapshot"));
      String state = text(task, "status");
      LocalDateTime due = dateTime(task.get("due_at")), expires = dateTime(task.get("expires_at"));
      if (state.equals("未开始") && !due.isAfter(now())) {
        repository.activateTask(tenant, id);
        state = "待执行";
        journal.event(tenant, id, "任务到期开始", null, "系统", Map.of());
      }
      if (now().isAfter(expires) && Set.of("未开始", "待执行").contains(state)) {
        String next =
            text(task, "kind").equals("daily") && task.get("arrangement_id") != null
                ? "已过期"
                : (yes(snap.get("allowLate")) ? "已过期" : "过期作废");
        repository.changeTaskStatus(next, tenant, id);
        journal.event(tenant, id, next, null, "系统", Map.of());
        if (next.equals("过期作废")) journal.closeTodos(tenant, id);
        state = next;
      }
      if (text(task, "kind").equals("daily")
          && task.get("follow_up_user_id") != null
          && now().isAfter(expires)
          && Set.of("待执行", "已过期", "执行中", "挂起", "审核不通过").contains(state))
        journal.notify(
            tenant,
            id,
            List.of(number(task.get("follow_up_user_id"))),
            "点巡检超期提醒",
            text(snap, "equipmentName"),
            false,
            true);
      if (Set.of("待执行", "已过期").contains(state))
        journal.notify(
            tenant,
            id,
            ids(snap.get("owners")),
            "点巡检待执行",
            text(snap, "equipmentName"),
            true,
            false);
      if (state.equals("未开始")
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
      if (Set.of("待执行", "执行中", "挂起", "已过期").contains(state)
          && number(snap.get("expireRemindHours")) > 0
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
}
