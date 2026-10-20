package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceGenerationService {
  private final MaintenanceRepository repository;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final MaintenanceProjectService projectSource;
  private final MaintenanceViewAssembler views;
  private final MaintenanceTaskJournal journal;

  public MaintenanceGenerationService(
      MaintenanceRepository repository,
      ObjectMapper json,
      AuthContext auth,
      MaintenanceProjectService projectSource,
      MaintenanceViewAssembler views,
      MaintenanceTaskJournal journal) {
    this.repository = repository;
    this.json = json;
    this.auth = auth;
    this.projectSource = projectSource;
    this.views = views;
    this.journal = journal;
  }

  @Transactional
  public int generate(long tenant, Long planId) {
    int count = 0;
    var plans = repository.lockPlans(tenant, planId);
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    for (var plan : plans) {
      var p = decode(plan.get("payload"));
      if (!yes(p.get("enabled"))) continue;
      // Generate when today reaches next maintenance date minus advance days.
      LocalDate generationDeadline = today.plusDays(number(p.get("advanceDays")));
      var schedules = repository.lockPlanSchedules(tenant, plan.get("id"));
      for (var schedule : schedules) {
        var cycle = decode(schedule.get("payload"));
        if (!yes(
            decode(
                    projectSource
                        .raw("types", tenant, number(schedule.get("type_id")), false)
                        .get("payload"))
                .get("enabled"))) continue;
        LocalDate due = ((java.sql.Date) schedule.get("next_date")).toLocalDate();
        LocalDate anchor = ((java.sql.Date) schedule.get("anchor_date")).toLocalDate();
        long index = number(schedule.get("cycle_index"));
        boolean completion = text(p, "calculation").equals("完成时间");
        if (completion && repository.countOpenScheduleTasks(tenant, schedule.get("id")) > 0)
          continue;
        for (int n = 0; n < 100 && !due.isAfter(generationDeadline); n++) {
          var equipment = projectSource.equipment(tenant, number(schedule.get("equipment_id")));
          var projects = projectSource.planProjects(cycle, tenant, equipment, false);
          require(
              !projects.isEmpty(),
              com.windasoft.eam.common.MessageContext.text("server.maintenance.message0074"));
          var type =
              views.definition(
                  projectSource.raw("types", tenant, number(schedule.get("type_id")), false));
          var snap = new LinkedHashMap<String, Object>();
          snap.put("planName", plan.get("name"));
          snap.put("equipmentCode", equipment.get("equipment_code"));
          snap.put("equipmentName", equipment.get("name"));
          snap.put("model", Objects.toString(equipment.get("model"), ""));
          snap.put("department", Objects.toString(equipment.get("department"), ""));
          snap.put("functionLocation", Objects.toString(equipment.get("function_location"), ""));
          snap.put("typeId", type.get("id"));
          snap.put("typeName", type.get("name"));
          snap.put("calculation", p.get("calculation"));
          boolean needsAcceptance = !text(p, "reviewMode").equals("无需验收");
          snap.put("reviewMode", needsAcceptance ? "整机验收" : "无需验收");
          snap.put("cycle", cycle);
          snap.put(
              "owners",
              (ids(cycle.get("owners")).isEmpty() ? ids(p.get("owners")) : ids(cycle.get("owners")))
                  .stream().map(Object::toString).toList());
          snap.put(
              "reviewers",
              (!needsAcceptance
                      ? List.<Long>of()
                      : ids(cycle.get("reviewers")).isEmpty()
                          ? ids(p.get("reviewers"))
                          : ids(cycle.get("reviewers")))
                  .stream().map(Object::toString).toList());
          snap.put("items", projects);
          snap.put(
              "standardName",
              projects.stream()
                  .map(i -> text(i, "sourceStandardName"))
                  .distinct()
                  .collect(java.util.stream.Collectors.joining("、")));
          snap.put("remark", p.get("remark"));
          snap.put("autoFill", p.get("autoFill"));
          snap.put("scanStart", p.get("scanStart"));
          snap.put("remindDays", p.get("remindDays"));
          long task = IdWorker.getId();
          String month = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
          repository.nextMonthlyNumber("maintenance-" + tenant, month);
          String code = "BY-" + month + "-" + String.format("%04d", repository.currentNumber());
          long duration =
              number(
                  cycle.containsKey("validDuration")
                      ? cycle.get("validDuration")
                      : cycle.get("validDays"));
          String durationUnit = cycle.containsKey("validUnit") ? text(cycle, "validUnit") : "日";
          var expires = duration == 0 ? null : advance(due, duration, durationUnit);
          int added =
              repository.insertTask(
                  task,
                  tenant,
                  code,
                  plan.get("id"),
                  schedule.get("id"),
                  equipment.get("id"),
                  schedule.get("type_id"),
                  equipment.get("company_org_id"),
                  equipment.get("department_org_id"),
                  due,
                  expires,
                  encode(snap));
          if (added > 0) {
            if ("上次结果".equals(text(p, "autoFill"))) {
              var previous =
                  repository.findLastCompletedResult(
                      tenant, equipment.get("id"), schedule.get("id"));
              if (!previous.isEmpty()) {
                var values = rows(decode(previous.getFirst().get("result")).get("items"));
                var draft = new ArrayList<Map<String, Object>>();
                for (var value : values) {
                  var v = new LinkedHashMap<>(value);
                  v.put("images", List.of());
                  v.put("measure", "");
                  v.put("resolved", false);
                  draft.add(v);
                }
                repository.updateTaskResult(encode(Map.of("items", draft)), tenant, task);
              }
            }
            count++;
            journal.event(
                tenant,
                task,
                "生成任务",
                null,
                "系统",
                Map.of("计划", plan.get("name"), "保养日期", due.toString()));
            if (!due.isAfter(today.plusDays(number(p.get("remindDays")))))
              journal.notify(
                  tenant,
                  task,
                  ids(snap.get("owners")),
                  "保养待执行",
                  text(snap, "equipmentName"),
                  true);
          }
          if (completion) {
            if (added == 0) {
              due = advance(due, number(cycle.get("period")), text(cycle, "unit"));
              repository.resetScheduleDate47(due, due, tenant, schedule.get("id"));
              continue;
            }
            break;
          }
          index++;
          due = advance(anchor, number(cycle.get("period")) * index, text(cycle, "unit"));
        }
        if (!completion) repository.advanceSchedule(due, index, tenant, schedule.get("id"));
      }
    }
    // Expire only unopened tasks; keep in-progress work and its records intact.
    for (var task : repository.lockExpiredTasks(tenant, today)) {
      long id = number(task.get("id"));
      repository.expireTask(tenant, id);
      journal.closeTodos(tenant, id);
      journal.event(tenant, id, "过期作废", null, "系统", Map.of());
    }
    for (var r : repository.listPendingTasks(tenant)) {
      var snap = decode(r.get("snapshot"));
      if (!((java.sql.Date) r.get("due_date"))
              .toLocalDate()
              .isAfter(today.plusDays(number(snap.get("remindDays"))))
          && repository.countExecutionNotices(tenant, r.get("id")) == 0)
        journal.notify(
            tenant,
            number(r.get("id")),
            ids(snap.get("owners")),
            "保养待执行",
            text(snap, "equipmentName"),
            true);
    }
    return count;
  }

  @Transactional
  public int generatePlan(long id) {
    var p = projectSource.raw("plans", auth.tenantId(), id, false);
    if (!projectSource.visible(p, "编辑")) deny();
    return generate(auth.tenantId(), id);
  }

  Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.maintenance.message0009");
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
