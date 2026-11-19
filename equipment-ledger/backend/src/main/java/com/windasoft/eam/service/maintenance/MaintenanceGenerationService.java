package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.advance;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.deny;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.ids;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.number;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.require;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.text;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.yes;
import static com.windasoft.eam.service.maintenance.MaintenanceTaskJournal.EXECUTION_NOTICE;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.domain.maintenance.MaintenanceCalculationMode;
import com.windasoft.eam.domain.maintenance.MaintenanceReviewMode;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.maintenance.MaintenanceExecutionResult;
import com.windasoft.eam.entity.maintenance.MaintenanceTask;
import com.windasoft.eam.repository.common.TaskSourceRelationsRepository;
import com.windasoft.eam.repository.maintenance.MaintenancePlanDeviceRepository;
import com.windasoft.eam.repository.maintenance.MaintenancePlanRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTaskRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTypeRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MaintenanceGenerationService {
  private final MaintenanceRepository repository;
  private final MaintenanceTaskRepository tasks;
  private final MaintenancePlanDeviceRepository schedules;
  private final MaintenancePlanRepository planDefinitions;
  private final MaintenanceTypeRepository types;
  private final MaintenanceExecutionService executions;
  private final TaskSourceRelationsRepository taskSources;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final MaintenanceProjectService projectSource;
  private final MaintenanceViewAssembler views;
  private final MaintenanceTaskJournal journal;

  public MaintenanceGenerationService(
      MaintenanceRepository repository,
      MaintenanceTaskRepository tasks,
      MaintenancePlanDeviceRepository schedules,
      MaintenancePlanRepository planDefinitions,
      MaintenanceTypeRepository types,
      MaintenanceExecutionService executions,
      TaskSourceRelationsRepository taskSources,
      ObjectMapper json,
      AuthContext auth,
      PermissionService permissions,
      MaintenanceProjectService projectSource,
      MaintenanceViewAssembler views,
      MaintenanceTaskJournal journal) {
    this.repository = repository;
    this.tasks = tasks;
    this.schedules = schedules;
    this.planDefinitions = planDefinitions;
    this.types = types;
    this.executions = executions;
    this.taskSources = taskSources;
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
    this.projectSource = projectSource;
    this.views = views;
    this.journal = journal;
  }

  /** 锁定计划和安排后按原游标补生成；只复用读取事实，逐任务编号、结果预填、通知和游标更新仍在原事务中。 */
  @Transactional
  public int generate(long tenant, Long planId) {
    int count = 0;
    planDefinitions.lockForGeneration(tenant, planId);
    var plans = repository.planRowsForGeneration(tenant, planId);
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    for (var plan : plans) {
      var p = decode(plan.get("payload"));
      if (!yes(p.get("enabled"))) {
        continue;
      }
      // 按计划提前天数扩展生成截止日，不改变每次任务的应执行日期。
      LocalDate generationDeadline = today.plusDays(number(p.get("advanceDays")));
      schedules.lockForGeneration(tenant, number(plan.get("id")));
      var arrangements = repository.scheduleRowsForGeneration(tenant, number(plan.get("id")));
      for (var schedule : arrangements) {
        var cycle = decode(schedule.get("payload"));
        var type = types.find(tenant, number(schedule.get("type_id")), false);
        if (type == null) {
          throw new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.inspection.message0013");
        }
        if (!Boolean.TRUE.equals(type.getEnabled())) {
          continue;
        }
        LocalDate due = ((Date) schedule.get("next_date")).toLocalDate();
        LocalDate anchor = ((Date) schedule.get("anchor_date")).toLocalDate();
        long index = number(schedule.get("cycle_index"));
        boolean completion =
            text(p, "calculation").equals(MaintenanceCalculationMode.COMPLETION.code());
        if (completion && tasks.countOpenScheduleTasks(tenant, number(schedule.get("id"))) > 0) {
          continue;
        }
        if (!due.isAfter(generationDeadline)) {
          // 补生成同一安排时，设备、类型和项目事实不随到期日变化；在本次事务内只解析一次。
          // 未到生成日的安排仍不读取设备/项目，避免无任务可生成时新增校验失败。
          var equipment = projectSource.equipment(tenant, number(schedule.get("equipment_id")));
          var projects = projectSource.planProjects(cycle, tenant, equipment, false);
          require(!projects.isEmpty(), MessageContext.text("server.maintenance.message0074"));
          for (int n = 0; n < 100 && !due.isAfter(generationDeadline); n++) {
            var snap = new LinkedHashMap<String, Object>();
            snap.put("planName", plan.get("name"));
            snap.put("equipmentCode", equipment.getEquipmentCode());
            snap.put("equipmentName", equipment.getName());
            snap.put("model", Objects.toString(equipment.getModel(), ""));
            snap.put("department", Objects.toString(equipment.getDepartment(), ""));
            snap.put("functionLocation", Objects.toString(equipment.getFunctionLocation(), ""));
            snap.put("typeId", Long.toString(type.getId()));
            snap.put("typeName", type.getName());
            snap.put("calculation", p.get("calculation"));
            boolean needsAcceptance =
                !text(p, "reviewMode").equals(MaintenanceReviewMode.NONE.code());
            snap.put(
                "reviewMode",
                needsAcceptance
                    ? MaintenanceReviewMode.EQUIPMENT.code()
                    : MaintenanceReviewMode.NONE.code());
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
            var entity = new MaintenanceTask();
            entity.setId(task);
            entity.setTenantId(tenant);
            entity.setCode(code);
            entity.setPlanId(number(plan.get("id")));
            entity.setScheduleId(number(schedule.get("id")));
            entity.setEquipmentId(equipment.getId());
            entity.setTypeId(number(schedule.get("type_id")));
            entity.setCompanyId(equipment.getCompanyOrgId());
            entity.setDepartmentId(equipment.getDepartmentOrgId());
            entity.setDueDate(due);
            entity.setExpiresDate(expires);
            entity.setSnapshot(encode(views.historicalSnapshot(snap)));
            int added = tasks.insertTask(entity);
            if (added > 0) {
              // 唯一键重复时不覆盖人员或来源；新任务仍按主体→人员→来源的原顺序写入同一事务。
              String content = encode(snap);
              executions.participants(
                  tenant, task, ids(snap.get("owners")), ids(snap.get("reviewers")));
              taskSources.maintenance(tenant, task, equipment.getId(), content);
              if ("上次结果".equals(text(p, "autoFill"))) {
                var previous =
                    tasks.findLastCompletedResult(
                        tenant, equipment.getId(), number(schedule.get("id")));
                if (previous.isPresent()) {
                  var draft = new ArrayList<MaintenanceExecutionResult.Item>();
                  for (var value :
                      previous.get().getItems() == null
                          ? List.<MaintenanceExecutionResult.Item>of()
                          : previous.get().getItems()) {
                    draft.add(
                        new MaintenanceExecutionResult.Item(
                            value.getId(),
                            value.getValue(),
                            value.isAbnormal(),
                            false,
                            "",
                            value.getRemark(),
                            value.isImplicitNormal(),
                            value.isSkipped(),
                            List.of()));
                  }
                  var prefilled = new MaintenanceExecutionResult();
                  prefilled.setItems(draft);
                  executions.save(tenant, task, prefilled);
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
              if (!due.isAfter(today.plusDays(number(p.get("remindDays"))))) {
                journal.notify(
                    tenant,
                    task,
                    ids(snap.get("owners")),
                    EXECUTION_NOTICE,
                    text(snap, "equipmentName"),
                    true);
              }
            }
            if (completion) {
              if (added == 0) {
                due = advance(due, number(cycle.get("period")), text(cycle, "unit"));
                schedules.reset(tenant, number(schedule.get("id")), due);
                continue;
              }
              break;
            }
            index++;
            due = advance(anchor, number(cycle.get("period")) * index, text(cycle, "unit"));
          }
        }
        if (!completion) {
          schedules.advance(tenant, number(schedule.get("id")), due, Math.toIntExact(index));
        }
      }
    }
    // 只有未开始的任务可自动过期作废；执行中的任务及其处理记录仍保留。
    for (long id : tasks.lockExpiredTasks(tenant, today)) {
      tasks.expireTask(tenant, id);
      journal.closeTodos(tenant, id);
      journal.event(tenant, id, "过期作废", null, "系统", Map.of());
    }
    for (var r : tasks.listPendingTasks(tenant)) {
      var snap = decode(r.snapshot().toString());
      if (!r.dueDate().isAfter(today.plusDays(number(snap.get("remindDays"))))
          && !journal.hasExecutionNotice(tenant, r.id())) {
        journal.notify(
            tenant,
            r.id(),
            ids(snap.get("owners")),
            EXECUTION_NOTICE,
            text(snap, "equipmentName"),
            true);
      }
    }
    return count;
  }

  @Transactional
  public int generatePlan(long id) {
    var plan = planDefinitions.find(auth.tenantId(), id, false);
    if (plan == null) {
      throw new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.inspection.message0013");
    }
    boolean allowed;
    try {
      allowed =
          permissions
              .maintenance("plans", PermissionAction.EDIT)
              .matches(null, plan.getDepartmentId(), plan.getCreatorId(), auth.userId());
    } catch (ResponseStatusException denied) {
      allowed = false;
    }
    if (!allowed) {
      deny();
    }
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
