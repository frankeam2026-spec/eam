package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.advance;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.deny;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.require;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.AcceptanceDecision;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.domain.maintenance.MaintenanceCalculationMode;
import com.windasoft.eam.domain.maintenance.MaintenanceReviewMode;
import com.windasoft.eam.domain.maintenance.MaintenanceTaskAction;
import com.windasoft.eam.domain.maintenance.MaintenanceTaskStatus;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.maintenance.MaintenanceBatchAcceptance;
import com.windasoft.eam.dto.maintenance.MaintenanceCustomFields;
import com.windasoft.eam.dto.maintenance.MaintenanceExecutionResult;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskCommand;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskData;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskSnapshot;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskTarget;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskView;
import com.windasoft.eam.entity.equipment.EquipmentHistory;
import com.windasoft.eam.event.experience.ExperienceEvents.TaskResult;
import com.windasoft.eam.repository.equipment.EquipmentHistoryRepository;
import com.windasoft.eam.repository.maintenance.MaintenancePlanDeviceRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTaskRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import com.windasoft.eam.service.business.BusinessValueService;
import com.windasoft.eam.service.experience.MaterialCustodyService;
import com.windasoft.eam.service.inventory.InventoryMutex;
import com.windasoft.eam.service.system.AuthContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 保养任务动作及批量操作的事务编排；履历和关联资料分别交给所属领域的仓储读写。 */
@Service
public class MaintenanceTaskService {
  private static final int MAX_BATCH_TASKS = 100;
  private static final String HISTORY_RECORD_TYPE = "MAINTENANCE";
  private final MaintenanceTaskRepository repository;
  private final MaintenancePlanDeviceRepository schedules;
  private final MaintenanceExecutionService executions;
  private final EquipmentHistoryRepository histories;
  private final SystemUserRepository users;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final MaintenanceTaskAccessService taskAccess;
  private final MaintenanceViewAssembler assembler;
  private final MaintenanceValidationService validation;
  private final MaintenanceTaskJournal journal;
  private final MaintenanceTaskStatisticsService statistics;
  private final BusinessValueService custom;
  private final InventoryMutex experienceInventoryMutex;
  private final ApplicationEventPublisher experiencePublisher;
  private final MaterialCustodyService materialCustody;

  public MaintenanceTaskService(
      MaintenanceTaskRepository repository,
      MaintenancePlanDeviceRepository schedules,
      MaintenanceExecutionService executions,
      EquipmentHistoryRepository histories,
      SystemUserRepository users,
      ObjectMapper json,
      AuthContext auth,
      MaintenanceTaskAccessService taskAccess,
      MaintenanceViewAssembler assembler,
      MaintenanceValidationService validation,
      MaintenanceTaskJournal journal,
      MaintenanceTaskStatisticsService statistics,
      BusinessValueService custom,
      InventoryMutex mutex,
      ApplicationEventPublisher publisher,
      MaterialCustodyService custody) {
    this.repository = repository;
    this.schedules = schedules;
    this.executions = executions;
    this.histories = histories;
    this.users = users;
    this.json = json;
    this.auth = auth;
    this.taskAccess = taskAccess;
    this.assembler = assembler;
    this.validation = validation;
    this.journal = journal;
    this.statistics = statistics;
    this.custom = custom;
    this.experienceInventoryMutex = mutex;
    this.experiencePublisher = publisher;
    this.materialCustody = custody;
  }

  /** 按原库存互斥、任务行锁顺序办理状态动作。首次转为已完成时产生设备履历， 履历、执行记录、待办和通知事件必须随任务一起回滚，不能单独开启事务提交履历。 */
  @Transactional
  public MaintenanceTaskView action(long id, MaintenanceTaskCommand command) {
    experienceInventoryMutex.enter();
    long tenant = auth.tenantId();
    var r = taskRaw(tenant, id, true);
    if (!taskAccess(r, PermissionAction.VIEW)) {
      deny();
    }
    checkVersion(r.rowVersion(), command.rowVersion());
    String state = Objects.toString(r.status()), next = state;
    var snap = r.snapshot().deepCopy();
    var facts = json.convertValue(snap, MaintenanceTaskSnapshot.class);
    long user = auth.userId();
    var result = r.result() == null ? new MaintenanceExecutionResult() : r.result();
    var owners = facts.getOwners() == null ? List.<Long>of() : facts.getOwners();
    var reviewers = facts.getReviewers() == null ? List.<Long>of() : facts.getReviewers();
    var participantOwners = owners;
    Long owner = r.ownerId();
    // 动作参数先解析为受控编码，权限和状态校验仍保留在当前完整事务内。
    var operation = command.action();
    if (Set.of(
            MaintenanceTaskAction.START,
            MaintenanceTaskAction.PROCESS,
            MaintenanceTaskAction.SUSPEND,
            MaintenanceTaskAction.RESUME)
        .contains(operation)) {
      if (!owners.contains(user) || (owner != null && owner != user)) {
        deny();
      }
    }
    if (operation == MaintenanceTaskAction.START) {
      var start = (MaintenanceTaskCommand.Start) command;
      require(
          state.equals(MaintenanceTaskStatus.PENDING.code()),
          MessageContext.text("server.maintenance.message0048"));
      require(
          !r.dueDate().isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai"))),
          MessageContext.text("server.maintenance.message0049"));
      if (facts.isScanStart()) {
        require(
            text(facts.getEquipmentCode()).equals(text(start.equipmentCode())),
            MessageContext.text("server.maintenance.message0050"));
      }
      owner = user;
      next = MaintenanceTaskStatus.IN_PROGRESS.code();
      result.setStartedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
      journal.closeTodos(tenant, id);
      journal.notify(tenant, id, List.of(user), "保养待处理", text(facts.getEquipmentName()), true);
    } else if (operation == MaintenanceTaskAction.PROCESS) {
      var process = (MaintenanceTaskCommand.Process) command;
      require(
          state.equals(MaintenanceTaskStatus.IN_PROGRESS.code()) && Objects.equals(owner, user),
          MessageContext.text("server.maintenance.message0051"));
      validation.validUsers(process.collaborators(), false);
      validation.validFileReferences(process.attachments(), false);
      var submitted = new LinkedHashMap<String, MaintenanceExecutionResult.Item>();
      for (var item : list(process.items())) {
        require(item != null, MessageContext.text("server.inspection.message0011"));
        // 保留同ID第一次提交的答案；建立索引避免每个历史项目都扫描整个请求列表。
        submitted.putIfAbsent(text(item.getId()), item);
      }
      var checked = new ArrayList<MaintenanceExecutionResult.Item>();
      for (var item :
          facts.getItems() == null ? List.<MaintenanceTaskSnapshot.Item>of() : facts.getItems()) {
        var value =
            submitted.getOrDefault(text(item.getId()), new MaintenanceExecutionResult.Item());
        String answer = text(value.getValue());
        require(
            !item.isRequired() || !answer.isBlank(),
            MessageContext.text("server.maintenance.message0052", text(item.getName())));
        validation.validFileReferences(value.getImages(), true);
        require(
            !item.isPhotoRequired() || !list(value.getImages()).isEmpty(),
            MessageContext.text("server.maintenance.message0053", text(item.getName())));
        boolean abnormal = false;
        if (!answer.isBlank()) {
          if (text(item.getValueType()).equals("选项")) {
            require(
                (item.getOptions() == null ? List.<String>of() : item.getOptions())
                    .contains(answer),
                MessageContext.text("server.maintenance.message0054"));
            abnormal =
                !(item.getNormalOptions() == null ? List.<String>of() : item.getNormalOptions())
                    .contains(answer);
          }
          if (text(item.getValueType()).equals("数值")) {
            var n = new BigDecimal(answer);
            abnormal =
                (!text(item.getLower()).isEmpty()
                        && n.compareTo(new BigDecimal(text(item.getLower()))) < 0)
                    || (!text(item.getUpper()).isEmpty()
                        && n.compareTo(new BigDecimal(text(item.getUpper()))) > 0);
          }
        }
        var v =
            new MaintenanceExecutionResult.Item(
                text(item.getId()),
                value.getValue(),
                abnormal,
                value.isResolved(),
                value.getMeasure(),
                value.getRemark(),
                value.isImplicitNormal(),
                value.isSkipped(),
                value.getImages());
        require(
            !abnormal || !value.isResolved() || !text(value.getMeasure()).isBlank(),
            MessageContext.text("server.maintenance.message0055"));
        checked.add(v);
      }
      require(
          process.startAt() != null && process.endAt() != null,
          MessageContext.text("server.maintenance.message0056"));
      require(
          !process.startAt().isAfter(process.endAt()),
          MessageContext.text("server.maintenance.message0057"));
      // 正常提交替换上一轮结果，沿用原行为清除旧验收和仅在开始时记录的startedAt。
      result = new MaintenanceExecutionResult();
      result.setDescription(process.description());
      result.setStartAt(process.startAt());
      result.setEndAt(process.endAt());
      result.setCollaborators(process.collaborators());
      result.setAttachments(process.attachments());
      result.setItems(checked);
      materialCustody.finishTask("保养", id, process.surplusRemark(), process.holderId());
      boolean needsAcceptance =
          !MaintenanceReviewMode.NONE.code().equals(text(facts.getReviewMode()));
      next =
          needsAcceptance
              ? MaintenanceTaskStatus.PENDING_ACCEPTANCE.code()
              : MaintenanceTaskStatus.COMPLETED.code();
      journal.closeTodos(tenant, id);
      journal.notify(
          tenant,
          id,
          needsAcceptance ? reviewers : owners,
          needsAcceptance ? "保养待验收" : "保养已完成",
          text(facts.getEquipmentName()),
          needsAcceptance);
    } else if (operation == MaintenanceTaskAction.ACCEPT) {
      var accept = (MaintenanceTaskCommand.Accept) command;
      require(
          state.equals(MaintenanceTaskStatus.PENDING_ACCEPTANCE.code()),
          MessageContext.text("server.maintenance.message0058"));
      if (!reviewers.contains(user) || !taskAccess(r, PermissionAction.ACCEPT)) {
        deny();
      }
      var decision = AcceptanceDecision.parse(accept.decision());
      require(
          accept.score() != null && accept.score() >= 1 && accept.score() <= 5,
          MessageContext.text("server.maintenance.message0059"));
      validation.validFileReferences(accept.attachments(), false);
      next =
          decision == AcceptanceDecision.ACCEPT
              ? MaintenanceTaskStatus.COMPLETED.code()
              : MaintenanceTaskStatus.IN_PROGRESS.code();
      var acceptance = new MaintenanceExecutionResult.Acceptance();
      acceptance.setDescription(accept.description());
      acceptance.setScore(BigDecimal.valueOf(accept.score()));
      acceptance.setAttachments(accept.attachments());
      acceptance.setDecision(decision.code());
      acceptance.setReviewerId(user);
      acceptance.setReviewer(auth.user().getDisplayName());
      acceptance.setReviewedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
      result.setAcceptance(acceptance);
      journal.closeTodos(tenant, id);
      journal.notify(
          tenant,
          id,
          owner == null ? owners : List.of(owner),
          next.equals(MaintenanceTaskStatus.COMPLETED.code()) ? "保养已验收" : "保养待处理",
          text(facts.getEquipmentName()),
          !next.equals(MaintenanceTaskStatus.COMPLETED.code()));
    } else if (operation == MaintenanceTaskAction.SUSPEND) {
      require(
          state.equals(MaintenanceTaskStatus.IN_PROGRESS.code()),
          MessageContext.text("server.maintenance.message0060"));
      require(
          !text(command.description()).isEmpty(),
          MessageContext.text("server.inspection.message0036"));
      next = MaintenanceTaskStatus.SUSPENDED.code();
      journal.closeTodos(tenant, id);
    } else if (operation == MaintenanceTaskAction.RESUME) {
      require(
          state.equals(MaintenanceTaskStatus.SUSPENDED.code()),
          MessageContext.text("server.maintenance.message0061"));
      next = MaintenanceTaskStatus.IN_PROGRESS.code();
      journal.notify(tenant, id, List.of(user), "保养待处理", text(facts.getEquipmentName()), true);
    } else if (operation == MaintenanceTaskAction.TRANSFER) {
      var transfer = (MaintenanceTaskCommand.Transfer) command;
      if (!taskAccess(r, PermissionAction.EDIT)) {
        deny();
      }
      require(
          Set.of(
                  MaintenanceTaskStatus.PENDING.code(),
                  MaintenanceTaskStatus.IN_PROGRESS.code(),
                  MaintenanceTaskStatus.SUSPENDED.code())
              .contains(state),
          MessageContext.text("server.inspection.message0038"));
      validation.validUsers(transfer.owners(), true);
      participantOwners = list(transfer.owners()).stream().distinct().toList();
      snap.set(
          "owners", json.valueToTree(participantOwners.stream().map(Object::toString).toList()));
      owner = null;
      next = MaintenanceTaskStatus.PENDING.code();
      journal.closeTodos(tenant, id);
      journal.notify(tenant, id, participantOwners, "保养待执行", text(facts.getEquipmentName()), true);
    } else if (operation == MaintenanceTaskAction.VOID) {
      if (!taskAccess(r, PermissionAction.DELETE)) {
        deny();
      }
      require(
          !Set.of(MaintenanceTaskStatus.COMPLETED.code(), MaintenanceTaskStatus.CANCELLED.code())
              .contains(state),
          MessageContext.text("server.maintenance.message0062"));
      require(
          !text(command.description()).isEmpty(),
          MessageContext.text("server.maintenance.message0063"));
      next = MaintenanceTaskStatus.CANCELLED.code();
      journal.closeTodos(tenant, id);
    } else {
      throw new LocalizedArgumentException("server.inspection.message0011");
    }
    if (next.equals(MaintenanceTaskStatus.COMPLETED.code())
        && !state.equals(MaintenanceTaskStatus.COMPLETED.code())) {
      // 保留履历产生时机和原字段口径；复用设备仓储的数据库时间写入，后续保存失败时一并回滚。
      var history = new EquipmentHistory();
      history.setId(IdWorker.getId());
      history.setTenantId(tenant);
      history.setEquipmentId(r.equipmentId());
      history.setRecordType(HISTORY_RECORD_TYPE);
      history.setRecordNo(Objects.toString(r.code()));
      history.setDescription(text(result.getDescription()));
      history.setOperatorName(userName(tenant, owner));
      history.setStatus(MaintenanceTaskStatus.COMPLETED.code());
      history.setResult(text(command.description()));
      histories.insert(history);
      if (MaintenanceCalculationMode.COMPLETION.code().equals(text(facts.getCalculation()))) {
        var cycle = facts.getCycle();
        LocalDate date =
            advance(result.getEndAt().toLocalDate(), cycle.getPeriod(), text(cycle.getUnit()));
        schedules.reset(tenant, r.scheduleId(), date);
      }
    }
    if (operation == MaintenanceTaskAction.PROCESS) {
      var process = (MaintenanceTaskCommand.Process) command;
      custom.save(
          "maintenance_task", id, process.customFields(), process.customSchemaVersion(), "", false);
    }
    // 保留原顺序：人员与结果先保存，再推进主体版本；任一步失败均由本动作事务回滚。
    executions.participants(tenant, id, participantOwners, reviewers);
    executions.save(tenant, id, result);
    snap.remove(List.of("owners", "reviewers"));
    repository.updateTask(next, owner, encode(snap), tenant, id);
    String label =
        switch (operation) {
          case START -> "开始保养";
          case PROCESS -> "提交保养";
          case ACCEPT ->
              AcceptanceDecision.parse(((MaintenanceTaskCommand.Accept) command).decision())
                      == AcceptanceDecision.ACCEPT
                  ? "验收通过"
                  : "验收打回";
          case SUSPEND -> "挂起";
          case RESUME -> "恢复";
          case TRANSFER -> "转派";
          default -> "作废";
        };
    journal.event(tenant, id, label, user, auth.user().getDisplayName(), command);
    if (operation == MaintenanceTaskAction.PROCESS) {
      experiencePublisher.publishEvent(new TaskResult("保养", id));
    }
    return task(id);
  }

  private <T> List<T> sortedBatch(List<T> values, Function<T, Long> id) {
    require(
        values != null && !values.isEmpty() && values.size() <= MAX_BATCH_TASKS,
        MessageContext.text("server.inspection.message0040"));
    require(
        values.stream()
            .allMatch(value -> value != null && id.apply(value) != null && id.apply(value) > 0),
        MessageContext.text("server.maintenance.message0064"));
    require(
        values.stream().map(id).distinct().count() == values.size(),
        MessageContext.text("server.maintenance.message0065"));
    return values.stream().sorted(Comparator.comparing(id)).toList();
  }

  @Transactional
  public void batchDelete(List<MaintenanceTaskTarget> values) {
    var sorted = sortedBatch(values, MaintenanceTaskTarget::id);
    experienceInventoryMutex.enter();
    long tenant = auth.tenantId();
    for (var value : sorted) {
      long id = value.id();
      var task = taskRaw(tenant, id, true);
      if (!taskAccess(task, PermissionAction.DELETE)) {
        deny();
      }
      checkVersion(task.rowVersion(), value.rowVersion());
      require(
          Set.of(
                  MaintenanceTaskStatus.PENDING.code(),
                  MaintenanceTaskStatus.CANCELLED.code(),
                  MaintenanceTaskStatus.EXPIRED_CANCELLED.code())
              .contains(task.status()),
          MessageContext.text("server.maintenance.message0066", task.code()));
      require(
          !repository.hasTaskExecutionOrMaterials(tenant, id),
          MessageContext.text("server.maintenance.message0067", task.code()));
      // 保留原先待办、全部消息、任务主体的更新顺序，迁移职责时不改变加锁路径。
      journal.closeTodos(tenant, id);
      journal.completeForDeletion(tenant, id);
      repository.softDeleteTask(tenant, id, auth.userId());
      journal.event(
          tenant,
          id,
          "删除任务",
          auth.userId(),
          auth.user().getDisplayName(),
          Map.of("code", task.code(), "rowVersion", value.rowVersion()));
    }
  }

  @Transactional
  public void batchPostpone(List<MaintenanceTaskTarget> values, LocalDate date, String reason) {
    var sorted = sortedBatch(values, MaintenanceTaskTarget::id);
    require(
        reason != null && !reason.isBlank() && reason.length() <= 1000,
        MessageContext.text("server.maintenance.message0068"));
    require(date != null, MessageContext.text("server.maintenance.message0011"));
    require(
        !date.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai"))),
        MessageContext.text("server.maintenance.message0069"));
    experienceInventoryMutex.enter();
    long tenant = auth.tenantId();
    for (var value : sorted) {
      long id = value.id();
      var task = taskRaw(tenant, id, true);
      if (!taskAccess(task, PermissionAction.EDIT)) {
        deny();
      }
      checkVersion(task.rowVersion(), value.rowVersion());
      require(
          Set.of(
                  MaintenanceTaskStatus.PENDING.code(),
                  MaintenanceTaskStatus.IN_PROGRESS.code(),
                  MaintenanceTaskStatus.SUSPENDED.code())
              .contains(task.status()),
          MessageContext.text("server.maintenance.message0070", task.code()));
      require(
          task.expiresDate() != null,
          MessageContext.text("server.maintenance.message0071", task.code()));
      LocalDate old = task.expiresDate();
      require(
          date.isAfter(old), MessageContext.text("server.maintenance.message0072", task.code()));
      require(
          !date.isBefore(task.dueDate()), MessageContext.text("server.maintenance.message0073"));
      repository.postponeTask(tenant, id, date);
      journal.event(
          tenant,
          id,
          "任务延期",
          auth.userId(),
          auth.user().getDisplayName(),
          Map.of("原计划结束时间", old.toString(), "计划结束时间", date.toString(), "原因", reason.trim()));
    }
  }

  @Transactional
  public void batchAccept(List<MaintenanceBatchAcceptance> values) {
    for (var v : sortedBatch(values, MaintenanceBatchAcceptance::id)) {
      action(v.id(), v.command());
    }
  }

  MaintenanceTaskData taskRaw(long tenant, long id, boolean lock) {
    var r = repository.loadTask(tenant, id, lock);
    if (r.isEmpty()) {
      throw new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.maintenance.message0012");
    }
    return r.getFirst();
  }

  boolean taskAccess(MaintenanceTaskData row, PermissionAction action) {
    return taskAccess.allowed(row, action);
  }

  MaintenanceTaskView taskView(MaintenanceTaskData row) {
    return assembler.task(row);
  }

  @Transactional(readOnly = true)
  public MaintenanceTaskView task(long id) {
    var r = taskRaw(auth.tenantId(), id, false);
    if (!taskAccess(r, PermissionAction.VIEW)) {
      deny();
    }
    var v = taskView(r);
    var extra = custom.read("maintenance_task", id);
    v.setCustom(
        new MaintenanceCustomFields(
            json.valueToTree(extra.customFields()),
            json.valueToTree(extra.customFieldLabels()),
            extra.customSchemaVersion()));
    var events = journal.events(auth.tenantId(), id);
    v.setStatistics(
        statistics.summarize(
            json.convertValue(r.snapshot(), MaintenanceTaskSnapshot.class),
            r.result(),
            r.status(),
            events));
    v.setEvents(events);
    return v;
  }

  /** 设备履历保留历史执行人姓名，不能用当前启用人员校验过滤已停用或删除的执行人。 */
  String userName(long tenant, Long id) {
    if (id == null) {
      return "";
    }
    return Objects.toString(users.displayNameIncludingDeleted(tenant, id), "");
  }

  private static void checkVersion(int current, Integer expected) {
    require(expected != null, MessageContext.text("server.common.message0018"));
    VersionGuard.check(current, expected);
  }

  private static <T> List<T> list(List<T> values) {
    return values == null ? List.of() : values;
  }

  private static String text(String value) {
    return Objects.toString(value, "").trim();
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new LocalizedArgumentException("server.inspection.message0012");
    }
  }
}
