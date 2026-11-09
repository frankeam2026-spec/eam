package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionGenerationService {
  private final DailyArrangementService arrangements;
  private final DailyArrangementRepository dailyStorage;

  private final InspectionRepository repository;
  private final ObjectMapper json;
  private final InspectionProjectService projectSource;
  private final InspectionViewAssembler views;
  private final InspectionTaskLifecycleService taskLifecycle;
  private final InspectionTaskCreationService taskCreation;


  public InspectionGenerationService(
      InspectionRepository repository,
      ObjectMapper json,
      InspectionProjectService projectSource,
      InspectionViewAssembler views,
      InspectionTaskLifecycleService taskLifecycle,
      InspectionTaskCreationService taskCreation,
      DailyArrangementService arrangements,
      DailyArrangementRepository dailyStorage) {
    this.repository = repository;
    this.json = json;
    this.projectSource = projectSource;
    this.views = views;
    this.taskLifecycle = taskLifecycle;
    this.taskCreation = taskCreation;
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
    var cache = new InspectionTaskCreationService.GenerationCache();
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
            count += taskCreation.createTask(tenant, plan, p, details, next, "", "", null, cache);
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
                        taskCreation.createTask(
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
                      taskCreation.createTask(
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
    taskLifecycle.maintainOpenTasks(tenant, planId);
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
