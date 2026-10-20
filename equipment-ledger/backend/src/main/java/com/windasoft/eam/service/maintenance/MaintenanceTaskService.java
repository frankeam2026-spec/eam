package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.AcceptanceDecision;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.service.system.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceTaskService {
  private final MaintenanceRepository repository;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final MaintenanceTaskAccessService taskAccess;
  private final MaintenanceViewAssembler assembler;
  private final MaintenanceValidationService validation;
  private final MaintenanceTaskJournal journal;
  private final MaintenanceTaskStatisticsService statistics;
  private final com.windasoft.eam.service.business.BusinessValueService custom;
  private final com.windasoft.eam.service.inventory.InventoryMutex experienceInventoryMutex;
  private final org.springframework.context.ApplicationEventPublisher experiencePublisher;
  private final com.windasoft.eam.service.experience.MaterialCustodyService materialCustody;

  public MaintenanceTaskService(
      MaintenanceRepository repository,
      ObjectMapper json,
      AuthContext auth,
      MaintenanceTaskAccessService taskAccess,
      MaintenanceViewAssembler assembler,
      MaintenanceValidationService validation,
      MaintenanceTaskJournal journal,
      MaintenanceTaskStatisticsService statistics,
      com.windasoft.eam.service.business.BusinessValueService custom,
      com.windasoft.eam.service.inventory.InventoryMutex mutex,
      org.springframework.context.ApplicationEventPublisher publisher,
      com.windasoft.eam.service.experience.MaterialCustodyService custody) {
    this.repository = repository;
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

  @Transactional
  public Map<String, Object> action(long id, String action, Map<String, Object> body) {
    experienceInventoryMutex.enter();
    long tenant = auth.tenantId();
    var r = taskRaw(tenant, id, true);
    if (!taskAccess(r, "查看")) deny();
    VersionGuard.checkPayload((Number) r.get("row_version"), body);
    String state = Objects.toString(r.get("status")), next = state;
    var snap = decode(r.get("snapshot"));
    long user = auth.userId();
    var result =
        r.get("result") == null ? new LinkedHashMap<String, Object>() : decode(r.get("result"));
    var owners = ids(snap.get("owners"));
    var reviewers = ids(snap.get("reviewers"));
    Long owner = r.get("owner_id") == null ? null : number(r.get("owner_id"));
    if (Set.of("start", "process", "suspend", "resume").contains(action)) {
      if (!owners.contains(user) || (owner != null && owner != user)) deny();
    }
    if (action.equals("start")) {
      require(
          state.equals("待执行"),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0048"));
      require(
          !((java.sql.Date) r.get("due_date"))
              .toLocalDate()
              .isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai"))),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0049"));
      if (yes(snap.get("scanStart")))
        require(
            text(snap, "equipmentCode").equals(text(body, "equipmentCode")),
            com.windasoft.eam.common.MessageContext.text("server.maintenance.message0050"));
      owner = user;
      next = "执行中";
      result.put("startedAt", LocalDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
      journal.closeTodos(tenant, id);
      journal.notify(tenant, id, List.of(user), "保养待处理", text(snap, "equipmentName"), true);
    } else if (action.equals("process")) {
      require(
          state.equals("执行中") && Objects.equals(owner, user),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0051"));
      validation.validUsers(body.get("collaborators"), false);
      validation.validAttachments(body.get("attachments"), false);
      var submitted = rows(body.get("items"));
      var checked = new ArrayList<Map<String, Object>>();
      for (var item : rows(snap.get("items"))) {
        var value =
            submitted.stream()
                .filter(x -> text(x, "id").equals(text(item, "id")))
                .findFirst()
                .orElse(Map.of());
        String answer = text(value, "value");
        require(
            !yes(item.get("required")) || !answer.isBlank(),
            com.windasoft.eam.common.MessageContext.text(
                "server.maintenance.message0052", text(item, "name")));
        validation.validAttachments(value.get("images"), true);
        require(
            !yes(item.get("photoRequired")) || !rows(value.get("images")).isEmpty(),
            com.windasoft.eam.common.MessageContext.text(
                "server.maintenance.message0053", text(item, "name")));
        boolean abnormal = false;
        if (!answer.isBlank()) {
          if (text(item, "valueType").equals("选项")) {
            require(
                strings(item.get("options")).contains(answer),
                com.windasoft.eam.common.MessageContext.text("server.maintenance.message0054"));
            abnormal = !strings(item.get("normalOptions")).contains(answer);
          }
          if (text(item, "valueType").equals("数值")) {
            var n = new BigDecimal(answer);
            abnormal =
                (!text(item, "lower").isEmpty()
                        && n.compareTo(new BigDecimal(text(item, "lower"))) < 0)
                    || (!text(item, "upper").isEmpty()
                        && n.compareTo(new BigDecimal(text(item, "upper"))) > 0);
          }
        }
        var v = new LinkedHashMap<>(value);
        v.put("id", item.get("id"));
        v.put("abnormal", abnormal);
        require(
            !abnormal || !yes(value.get("resolved")) || !text(value, "measure").isBlank(),
            com.windasoft.eam.common.MessageContext.text("server.maintenance.message0055"));
        checked.add(v);
      }
      String start = text(body, "startAt"), end = text(body, "endAt");
      require(
          !start.isEmpty() && !end.isEmpty(),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0056"));
      require(
          !LocalDateTime.parse(start).isAfter(LocalDateTime.parse(end)),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0057"));
      result = new LinkedHashMap<>(body);
      result.remove("rowVersion");
      result.put("items", checked);
      materialCustody.finishTask("保养", id, body);
      boolean needsAcceptance = !text(snap, "reviewMode").equals("无需验收");
      next = needsAcceptance ? "待验收" : "已完成";
      journal.closeTodos(tenant, id);
      journal.notify(
          tenant,
          id,
          needsAcceptance ? reviewers : owners,
          needsAcceptance ? "保养待验收" : "保养已完成",
          text(snap, "equipmentName"),
          needsAcceptance);
    } else if (action.equals("accept")) {
      require(
          state.equals("待验收"),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0058"));
      if (!reviewers.contains(user) || !taskAccess(r, "验收")) deny();
      var decision = AcceptanceDecision.parse(body.get("decision"));
      require(
          number(body.get("score")) >= 1 && number(body.get("score")) <= 5,
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0059"));
      validation.validAttachments(body.get("attachments"), false);
      next = decision == AcceptanceDecision.ACCEPT ? "已完成" : "执行中";
      var acceptance = new LinkedHashMap<String, Object>(body);
      acceptance.put("decision", decision.code());
      acceptance.put("reviewerId", Long.toString(user));
      acceptance.put("reviewer", auth.user().getDisplayName());
      acceptance.put("reviewedAt", LocalDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
      result.put("acceptance", acceptance);
      journal.closeTodos(tenant, id);
      journal.notify(
          tenant,
          id,
          owner == null ? owners : List.of(owner),
          next.equals("已完成") ? "保养已验收" : "保养待处理",
          text(snap, "equipmentName"),
          !next.equals("已完成"));
    } else if (action.equals("suspend")) {
      require(
          state.equals("执行中"),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0060"));
      require(
          !text(body, "description").isEmpty(),
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0036"));
      next = "挂起";
      journal.closeTodos(tenant, id);
    } else if (action.equals("resume")) {
      require(
          state.equals("挂起"),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0061"));
      next = "执行中";
      journal.notify(tenant, id, List.of(user), "保养待处理", text(snap, "equipmentName"), true);
    } else if (action.equals("transfer")) {
      if (!taskAccess(r, "编辑")) deny();
      require(
          Set.of("待执行", "执行中", "挂起").contains(state),
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0038"));
      validation.validUsers(body.get("owners"), true);
      snap.put("owners", ids(body.get("owners")).stream().map(Object::toString).toList());
      owner = null;
      next = "待执行";
      journal.closeTodos(tenant, id);
      journal.notify(
          tenant, id, ids(body.get("owners")), "保养待执行", text(snap, "equipmentName"), true);
    } else if (action.equals("void")) {
      if (!taskAccess(r, "删除")) deny();
      require(
          !Set.of("已完成", "已作废").contains(state),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0062"));
      require(
          !text(body, "description").isEmpty(),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0063"));
      next = "已作废";
      journal.closeTodos(tenant, id);
    } else
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0011");
    if (next.equals("已完成") && !state.equals("已完成")) {
      repository.insertEquipmentHistory(
          IdWorker.getId(),
          tenant,
          r.get("equipment_id"),
          r.get("code"),
          text(result, "description"),
          userName(tenant, owner),
          "已完成",
          text(body, "description"));
      if ("完成时间".equals(text(snap, "calculation"))) {
        var cycle = decode(snap.get("cycle"));
        LocalDate date =
            advance(
                LocalDateTime.parse(text(result, "endAt")).toLocalDate(),
                number(cycle.get("period")),
                text(cycle, "unit"));
        repository.resetScheduleDate(date, date, tenant, r.get("schedule_id"));
      }
    }
    if (action.equals("process")) custom.save("maintenance_task", id, body, false);
    repository.updateTask(next, owner, encode(snap), encode(result), tenant, id);
    String label =
        switch (action) {
          case "start" -> "开始保养";
          case "process" -> "提交保养";
          case "accept" ->
              AcceptanceDecision.parse(body.get("decision")) == AcceptanceDecision.ACCEPT
                  ? "验收通过"
                  : "验收打回";
          case "suspend" -> "挂起";
          case "resume" -> "恢复";
          case "transfer" -> "转派";
          default -> "作废";
        };
    journal.event(tenant, id, label, user, auth.user().getDisplayName(), body);
    if (action.equals("process"))
      experiencePublisher.publishEvent(
          new com.windasoft.eam.event.experience.ExperienceEvents.TaskResult("保养", id));
    return task(id);
  }

  private List<Map<String, Object>> sortedBatch(List<Map<String, Object>> values) {
    require(
        values != null && !values.isEmpty() && values.size() <= 100,
        com.windasoft.eam.common.MessageContext.text("server.inspection.message0040"));
    require(
        values.stream().allMatch(v -> v != null && number(v.get("id")) > 0),
        com.windasoft.eam.common.MessageContext.text("server.maintenance.message0064"));
    require(
        values.stream().map(v -> number(v.get("id"))).distinct().count() == values.size(),
        com.windasoft.eam.common.MessageContext.text("server.maintenance.message0065"));
    var sorted = new ArrayList<>(values);
    sorted.sort(Comparator.comparingLong(v -> number(v.get("id"))));
    return sorted;
  }

  @Transactional
  public void batchDelete(List<Map<String, Object>> values) {
    var sorted = sortedBatch(values);
    experienceInventoryMutex.enter();
    long tenant = auth.tenantId();
    for (var value : sorted) {
      long id = number(value.get("id"));
      var task = taskRaw(tenant, id, true);
      if (!taskAccess(task, "删除")) deny();
      VersionGuard.checkPayload((Number) task.get("row_version"), value);
      require(
          Set.of("待执行", "已作废", "过期作废").contains(text(task, "status")),
          com.windasoft.eam.common.MessageContext.text(
              "server.maintenance.message0066", text(task, "code")));
      require(
          !repository.hasTaskExecutionOrMaterials(tenant, id),
          com.windasoft.eam.common.MessageContext.text(
              "server.maintenance.message0067", text(task, "code")));
      journal.closeTodos(tenant, id);
      repository.softDeleteTask(tenant, id, auth.userId());
      journal.event(
          tenant,
          id,
          "删除任务",
          auth.userId(),
          auth.user().getDisplayName(),
          Map.of("code", task.get("code"), "rowVersion", value.get("rowVersion")));
    }
  }

  @Transactional
  public void batchPostpone(List<Map<String, Object>> values, String endDate, String reason) {
    var sorted = sortedBatch(values);
    require(
        reason != null && !reason.isBlank() && reason.length() <= 1000,
        com.windasoft.eam.common.MessageContext.text("server.maintenance.message0068"));
    LocalDate date;
    try {
      date = LocalDate.parse(endDate);
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.maintenance.message0011");
    }
    require(
        !date.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai"))),
        com.windasoft.eam.common.MessageContext.text("server.maintenance.message0069"));
    experienceInventoryMutex.enter();
    long tenant = auth.tenantId();
    for (var value : sorted) {
      long id = number(value.get("id"));
      var task = taskRaw(tenant, id, true);
      if (!taskAccess(task, "编辑")) deny();
      VersionGuard.checkPayload((Number) task.get("row_version"), value);
      require(
          Set.of("待执行", "执行中", "挂起").contains(text(task, "status")),
          com.windasoft.eam.common.MessageContext.text(
              "server.maintenance.message0070", text(task, "code")));
      require(
          task.get("expires_date") != null,
          com.windasoft.eam.common.MessageContext.text(
              "server.maintenance.message0071", text(task, "code")));
      LocalDate old = ((java.sql.Date) task.get("expires_date")).toLocalDate();
      require(
          date.isAfter(old),
          com.windasoft.eam.common.MessageContext.text(
              "server.maintenance.message0072", text(task, "code")));
      require(
          !date.isBefore(((java.sql.Date) task.get("due_date")).toLocalDate()),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0073"));
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
  public void batchAccept(List<Map<String, Object>> values) {
    for (var v : sortedBatch(values)) action(number(v.get("id")), "accept", v);
  }

  Map<String, Object> taskRaw(long tenant, long id, boolean lock) {
    var r = repository.loadTask(tenant, id, lock);
    if (r.isEmpty())
      throw new com.windasoft.eam.common.LocalizedStatusException(
          HttpStatus.NOT_FOUND, "server.maintenance.message0012");
    return r.getFirst();
  }

  boolean taskAccess(Map<String, Object> row, String action) {
    return taskAccess.allowed(row, action);
  }

  Map<String, Object> taskView(Map<String, Object> row) {
    return assembler.task(row);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> task(long id) {
    var r = taskRaw(auth.tenantId(), id, false);
    if (!taskAccess(r, "查看")) deny();
    var v = taskView(r);
    custom.attach("maintenance_task", v);
    var events = repository.listTaskEvents(auth.tenantId(), id);
    v.put("statistics", statistics.summarize(v, events));
    v.put(
        "events",
        events.stream()
            .map(
                e -> {
                  e.put("details", decode(e.get("details")));
                  return e;
                })
            .toList());
    return v;
  }

  String userName(long tenant, Long id) {
    if (id == null) return "";
    var users = repository.findUserName(tenant, id);
    return users.isEmpty() ? "" : Objects.toString(users.getFirst().get("display_name"));
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
