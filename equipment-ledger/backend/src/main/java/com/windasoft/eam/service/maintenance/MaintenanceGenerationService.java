package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.advance;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.deny;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.ids;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.number;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.require;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.rows;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.text;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.yes;
import static com.windasoft.eam.service.maintenance.MaintenanceTaskJournal.EXECUTION_NOTICE;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.service.system.AuthContext;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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

  /** 锁定计划和安排后按原游标补生成；只复用读取事实，逐任务编号、结果预填、通知和游标更新仍在原事务中。 */
  @Transactional
  public int generate(long tenant, Long planId) {
    int count = 0;
    var plans = repository.lockPlans(tenant, planId);
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    for (var plan : plans) {
      var p = decode(plan.get("payload"));
      if (!yes(p.get("enabled"))) continue;
      // 按计划提前天数扩展生成截止日，不改变每次任务的应执行日期。
      LocalDate generationDeadline = today.plusDays(number(p.get("advanceDays")));
      var schedules = repository.lockPlanSchedules(tenant, plan.get("id"));
      for (var schedule : schedules) {
        var cycle = decode(schedule.get("payload"));
        var typeRow = projectSource.raw("types", tenant, number(schedule.get("type_id")), false);
        if (!yes(decode(typeRow.get("payload")).get("enabled"))) continue;
        LocalDate due = ((Date) schedule.get("next_date")).toLocalDate();
        LocalDate anchor = ((Date) schedule.get("anchor_date")).toLocalDate();
        long index = number(schedule.get("cycle_index"));
        boolean completion = text(p, "calculation").equals("完成时间");
        if (completion && repository.countOpenScheduleTasks(tenant, schedule.get("id")) > 0)
          continue;
        if (!due.isAfter(generationDeadline)) {
          // 补生成同一安排时，设备、类型和项目事实不随到期日变化；在本次事务内只解析一次。
          // 未到生成日的安排仍不读取设备/项目，避免无任务可生成时新增校验失败。
          var equipment = projectSource.equipment(tenant, number(schedule.get("equipment_id")));
          var projects = projectSource.planProjects(cycle, tenant, equipment, false);
          require(!projects.isEmpty(), MessageContext.text("server.maintenance.message0074"));
          var type = views.definition(typeRow);
          for (int n = 0; n < 100 && !due.isAfter(generationDeadline); n++) {
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
                (ids(cycle.get("owners")).isEmpty()
                        ? ids(p.get("owners"))
                        : ids(cycle.get("owners")))
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
                    .collect(Collectors.joining("、")));
            snap.put("remark", p.get("remark"));
            snap.put("autoFill", p.get("autoFill"));
            snap.put("scanStart", p.get("scanStart"));
            snap.put("remindDays", p.get("remindDays"));
            long task = IdWorker.getId();
            String month = today.format(DateTimeFormatter.ofPattern("yyyyMM"));
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
                    EXECUTION_NOTICE,
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
        }
        if (!completion) repository.advanceSchedule(due, index, tenant, schedule.get("id"));
      }
    }
    // 只有未开始的任务可自动过期作废；执行中的任务及其处理记录仍保留。
    for (var task : repository.lockExpiredTasks(tenant, today)) {
      long id = number(task.get("id"));
      repository.expireTask(tenant, id);
      journal.closeTodos(tenant, id);
      journal.event(tenant, id, "过期作废", null, "系统", Map.of());
    }
    for (var r : repository.listPendingTasks(tenant)) {
      var snap = decode(r.get("snapshot"));
      if (!((Date) r.get("due_date"))
              .toLocalDate()
              .isAfter(today.plusDays(number(snap.get("remindDays"))))
          && !journal.hasExecutionNotice(tenant, number(r.get("id"))))
        journal.notify(
            tenant,
            number(r.get("id")),
            ids(snap.get("owners")),
            EXECUTION_NOTICE,
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
      throw new LocalizedArgumentException("server.maintenance.message0009");
    }
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new LocalizedArgumentException("server.inspection.message0012");
    }
  }
}
