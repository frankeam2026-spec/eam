package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.AcceptanceDecision;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.system.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionTaskService {
  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.repository.inspection.DailyArrangementRepository dailyStorage;

  private final InspectionRepository repository;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final InspectionTaskAccessService taskAccess;
  private final InspectionViewAssembler views;
  private final InspectionValidationService validation;
  private final InspectionTaskJournal journal;
  private final com.windasoft.eam.service.business.BusinessValueService custom;
  private final com.windasoft.eam.service.inventory.InventoryMutex experienceInventoryMutex;
  private final org.springframework.context.ApplicationEventPublisher experiencePublisher;
  private final com.windasoft.eam.service.experience.MaterialCustodyService materialCustody;

  public InspectionTaskService(
      InspectionRepository repository,
      ObjectMapper json,
      AuthContext auth,
      InspectionTaskAccessService taskAccess,
      InspectionViewAssembler views,
      InspectionValidationService validation,
      InspectionTaskJournal journal,
      com.windasoft.eam.service.business.BusinessValueService custom,
      com.windasoft.eam.service.inventory.InventoryMutex mutex,
      org.springframework.context.ApplicationEventPublisher publisher,
      com.windasoft.eam.service.experience.MaterialCustodyService custody) {
    this.repository = repository;
    this.json = json;
    this.auth = auth;
    this.taskAccess = taskAccess;
    this.views = views;
    this.validation = validation;
    this.journal = journal;
    this.custom = custom;
    this.experienceInventoryMutex = mutex;
    this.experiencePublisher = publisher;
    this.materialCustody = custody;
  }

  Map<String, Object> taskRaw(long tenant, long id, boolean lock) {
    var r = repository.loadTask(tenant, id, lock);
    if (r.isEmpty())
      throw new com.windasoft.eam.common.LocalizedStatusException(
          HttpStatus.NOT_FOUND, "server.inspection.message0009");
    return r.getFirst();
  }

  boolean participant(Map<String, Object> row) {
    return taskAccess.participant(row);
  }

  boolean access(Map<String, Object> row, String action) {
    return taskAccess.access(row, action);
  }

  boolean execute(Map<String, Object> row) {
    return taskAccess.execute(row);
  }

  boolean review(Map<String, Object> row) {
    return taskAccess.review(row);
  }

  Map<String, Object> taskView(Map<String, Object> row) {
    return views.task(row);
  }

  public Map<String, Object> task(long id) {
    var r = taskRaw(auth.tenantId(), id, false);
    if (!access(r, "查看")) deny();
    var v = taskView(r);
    custom.attach(
        com.windasoft.eam.service.business.BusinessObjects.inspection(text(r, "kind")), v);
    if (text(r, "kind").equals("daily"))
      v.put("submissions", dailyStorage.submissions(auth.tenantId(), id));
    v.put(
        "events",
        repository.listTaskEvents(auth.tenantId(), id).stream()
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
    var r = repository.findUserName(tenant, id);
    return r.isEmpty() ? "" : text(r.getFirst(), "display_name");
  }

  List<Map<String, Object>> checkedResults(
      long tenant, Map<String, Object> snap, Map<String, Object> body, boolean complete) {
    var checked = new ArrayList<Map<String, Object>>();
    var submitted = rows(body.get("items"));
    require(
        submitted.stream().map(i -> text(i, "id")).distinct().count() == submitted.size(),
        com.windasoft.eam.common.MessageContext.text("server.inspection.message0018"));
    var known = rows(snap.get("items")).stream().map(i -> text(i, "id")).toList();
    require(
        submitted.stream().allMatch(i -> known.contains(text(i, "id"))),
        com.windasoft.eam.common.MessageContext.text("server.inspection.message0019"));
    var skipped = strings(body.get("skippedEquipmentIds"));
    require(
        rows(snap.get("equipment")).stream().map(e -> text(e, "id")).toList().containsAll(skipped),
        com.windasoft.eam.common.MessageContext.text("server.inspection.message0020"));
    require(
        skipped.isEmpty() || !text(body, "skipReason").isBlank(),
        com.windasoft.eam.common.MessageContext.text("server.inspection.message0021"));
    for (var item : rows(snap.get("items"))) {
      var answer =
          submitted.stream()
              .filter(a -> text(a, "id").equals(text(item, "id")))
              .findFirst()
              .orElse(Map.of());
      String value = text(answer, "value");
      boolean skip = skipped.contains(text(item, "equipmentId"));
      boolean implicit = complete && yes(snap.get("onlyAbnormal")) && value.isBlank() && !skip;
      require(
          !complete || skip || implicit || !yes(item.get("required")) || !value.isBlank(),
          com.windasoft.eam.common.MessageContext.text(
              "server.inspection.message0022", text(item, "name")));
      validation.attachments(tenant, answer.get("images"), true);
      require(
          !complete
              || skip
              || !yes(item.get("photoRequired"))
              || !rows(answer.get("images")).isEmpty(),
          com.windasoft.eam.common.MessageContext.text(
              "server.inspection.message0023", text(item, "name")));
      boolean abnormal = false;
      if (!value.isBlank()) {
        if (text(item, "valueType").equals("选项")) {
          require(
              strings(item.get("options")).contains(value),
              com.windasoft.eam.common.MessageContext.text("server.inspection.message0024"));
          abnormal = !strings(item.get("normalOptions")).contains(value);
        } else if (text(item, "valueType").equals("数值")) {
          BigDecimal n = new BigDecimal(value);
          abnormal =
              (!text(item, "lower").isBlank()
                      && n.compareTo(new BigDecimal(text(item, "lower"))) < 0)
                  || (!text(item, "upper").isBlank()
                      && n.compareTo(new BigDecimal(text(item, "upper"))) > 0);
        }
      }
      require(
          !complete
              || !abnormal
              || !yes(answer.get("resolved"))
              || !text(answer, "measure").isBlank(),
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0025"));
      var result = new LinkedHashMap<String, Object>(answer);
      result.put("id", item.get("id"));
      result.put(
          "value",
          implicit && text(item, "valueType").equals("选项")
              ? strings(item.get("normalOptions")).getFirst()
              : value);
      result.put("implicitNormal", implicit);
      result.put("abnormal", abnormal);
      result.put("skipped", skip);
      checked.add(result);
    }
    return checked;
  }

  void history(long tenant, Map<String, Object> task, Map<String, Object> result, String status) {
    var snap = decode(task.get("snapshot"));
    for (var eq : rows(snap.get("equipment"))) {
      if (strings(result.get("skippedEquipmentIds")).contains(text(eq, "id"))) continue;
      String summary =
          rows(result.get("items")).stream()
              .filter(r -> text(r, "id").startsWith(text(eq, "id") + "/"))
              .map(r -> text(r, "value"))
              .collect(java.util.stream.Collectors.joining("、"));
      if (repository.countEquipmentHistory(tenant, number(eq.get("id")), task.get("code")) == 0)
        repository.insertEquipmentHistory(
            IdWorker.getId(),
            tenant,
            number(eq.get("id")),
            task.get("code"),
            text(snap, "planName"),
            userName(tenant, nullable(task.get("owner_id"))),
            status,
            summary);
    }
  }

  @Transactional
  public Map<String, Object> action(long id, String action, Map<String, Object> body) {
    experienceInventoryMutex.enter();
    long tenant = auth.tenantId(), user = auth.userId();
    var row = taskRaw(tenant, id, true);
    if (!access(row, "查看")) deny();
    VersionGuard.checkPayload((Number) row.get("row_version"), body);
    var snap = decode(row.get("snapshot"));
    var result =
        row.get("result") == null ? new LinkedHashMap<String, Object>() : decode(row.get("result"));
    String state = text(row, "status"), next = state, label = "";
    String previousResult = action.equals("amend-result") ? encode(result) : "";
    Long owner = nullable(row.get("owner_id"));
    if (Set.of("start", "draft", "submit", "suspend", "resume", "scan").contains(action)
        && !execute(row)) deny();
    switch (action) {
      case "start" -> {
        require(
            Set.of("待执行", "未开始", "已过期", "审核不通过").contains(state),
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0026"));
        require(
            !now().isBefore(dateTime(row.get("due_at"))),
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0027"));
        require(
            !now().isAfter(dateTime(row.get("expires_at"))) || yes(snap.get("allowLate")),
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0028"));
        owner = user;
        next = "执行中";
        if (text(result, "startedAt").isBlank()) result.put("startedAt", now().toString());
        journal.closeTodos(tenant, id);
        journal.notify(
            tenant, id, List.of(user), "点巡检待处理", text(snap, "equipmentName"), true, false);
        label = "开始点巡检";
      }
      case "scan" -> {
        require(
            state.equals("执行中") && Objects.equals(owner, user),
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0029"));
        var eq =
            rows(snap.get("equipment")).stream()
                .filter(e -> text(e, "code").equals(text(body, "equipmentCode")))
                .findFirst()
                .orElseThrow(
                    () ->
                        new com.windasoft.eam.common.LocalizedArgumentException(
                            "server.inspection.message0010"));
        var scanned = new ArrayList<>(strings(result.get("scannedEquipmentIds")));
        if (!scanned.contains(text(eq, "id"))) scanned.add(text(eq, "id"));
        result.put("scannedEquipmentIds", scanned);
        label = "校验设备";
      }
      case "draft", "submit" -> {
        require(
            state.equals("执行中") && Objects.equals(owner, user),
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0030"));
        boolean complete = action.equals("submit");
        var values = checkedResults(tenant, snap, body, complete);
        validation.attachments(tenant, body.get("attachments"), false);
        if (complete && !text(snap, "scanMode").equals("无需扫码")) {
          var scanned = strings(result.get("scannedEquipmentIds"));
          var expected =
              rows(snap.get("equipment")).stream()
                  .map(e -> text(e, "id"))
                  .filter(e -> !strings(body.get("skippedEquipmentIds")).contains(e))
                  .toList();
          require(
              text(snap, "scanMode").equals("校验一台即可提交")
                  ? !scanned.isEmpty()
                  : scanned.containsAll(expected),
              com.windasoft.eam.common.MessageContext.text("server.inspection.message0031"));
        }
        result.put("items", values);
        result.put("description", text(body, "description"));
        result.put("attachments", rows(body.get("attachments")));
        result.put("skippedEquipmentIds", strings(body.get("skippedEquipmentIds")));
        result.put("skipReason", text(body, "skipReason"));
        label = complete ? "提交检查结果" : "保存检查结果";
        if (complete) {
          materialCustody.finishTask("点巡检", id, body);
          result.put("finishedAt", now().toString());
          if (text(row, "kind").equals("daily")) dailyStorage.submission(tenant, id, user, result);
          journal.closeTodos(tenant, id);
          next = text(snap, "reviewMode").equals("无需验收") ? "已完成" : "待审核";
          if (next.equals("已完成")) history(tenant, row, result, next);
          else
            journal.notify(
                tenant,
                id,
                ids(snap.get("reviewers")),
                "点巡检待审核",
                text(snap, "equipmentName"),
                true,
                false);
        }
      }
      case "amend-result" -> {
        if (!taskAccess.canAmendResult(row)) deny();
        // Correct the recorded results without reopening execution or changing its scope.
        var correction = new LinkedHashMap<String, Object>(body);
        correction.put("skippedEquipmentIds", strings(result.get("skippedEquipmentIds")));
        correction.put("skipReason", text(result, "skipReason"));
        var values = checkedResults(tenant, snap, correction, true);
        validation.attachments(tenant, body.get("attachments"), false);
        result.put("items", values);
        result.put("description", text(body, "description"));
        result.put("attachments", rows(body.get("attachments")));
        for (var eq : rows(snap.get("equipment"))) {
          if (strings(result.get("skippedEquipmentIds")).contains(text(eq, "id"))) continue;
          String summary = values.stream()
              .filter(r -> text(r, "id").startsWith(text(eq, "id") + "/"))
              .map(r -> text(r, "value"))
              .collect(java.util.stream.Collectors.joining("、"));
          repository.updateEquipmentHistoryResult(tenant, number(eq.get("id")), row.get("code"), summary);
        }
        label = "修改检查结果";
      }
      case "review" -> {
        require(
            state.equals("待审核"),
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0032"));
        if (!review(row)) deny();
        var decision = AcceptanceDecision.parse(body.get("decision"));
        validation.attachments(tenant, body.get("attachments"), false);
        boolean pass = decision == AcceptanceDecision.ACCEPT;
        if (text(snap, "reviewMode").equals("逐项审核")) {
          var reviews = rows(body.get("reviewItems"));
          var expected =
              rows(snap.get("items")).stream()
                  .filter(
                      i ->
                          !strings(result.get("skippedEquipmentIds"))
                              .contains(text(i, "equipmentId")))
                  .map(i -> text(i, "id"))
                  .toList();
          require(
              reviews.stream().map(i -> text(i, "id")).distinct().count() == reviews.size(),
              com.windasoft.eam.common.MessageContext.text("server.inspection.message0033"));
          require(
              reviews.stream().allMatch(i -> expected.contains(text(i, "id"))),
              com.windasoft.eam.common.MessageContext.text("server.inspection.message0034"));
          var checkedReviews = new ArrayList<Map<String, Object>>();
          for (var item : rows(snap.get("items"))) {
            if (strings(result.get("skippedEquipmentIds")).contains(text(item, "equipmentId")))
              continue;
            var r =
                reviews.stream()
                    .filter(i -> text(i, "id").equals(text(item, "id")))
                    .findFirst()
                    .orElse(Map.of());
            var itemDecision = AcceptanceDecision.parse(r.get("decision"));
            if (itemDecision == AcceptanceDecision.REJECT) pass = false;
            checkedReviews.add(
                Map.of(
                    "id", text(item, "id"),
                    "decision", itemDecision.code(),
                    "description", text(r, "description")));
          }
          result.put("reviewItems", checkedReviews);
        }
        require(
            pass || !text(body, "description").isBlank(),
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text1476"));
        result.put(
            "review",
            Map.of(
                "decision",
                (pass ? AcceptanceDecision.ACCEPT : AcceptanceDecision.REJECT).code(),
                "description",
                text(body, "description"),
                "attachments",
                rows(body.get("attachments")),
                "reviewerId",
                Long.toString(auth.userId()),
                "reviewer",
                auth.user().getDisplayName(),
                "reviewedAt",
                now().toString()));
        if (text(row, "kind").equals("daily"))
          dailyStorage.submissionReview(
              tenant, id, user, pass ? "accept" : "reject", text(body, "description"));
        next = pass ? "已审核" : "审核不通过";
        journal.closeTodos(tenant, id);
        if (pass) history(tenant, row, result, next);
        journal.notify(
            tenant,
            id,
            owner == null ? ids(snap.get("owners")) : List.of(owner),
            pass ? "点巡检审核通过" : "点巡检待修改",
            text(snap, "equipmentName"),
            !pass,
            false);
        label = pass ? "审核通过" : "审核打回";
      }
      case "suspend" -> {
        require(
            state.equals("执行中") && Objects.equals(owner, user),
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0035"));
        require(
            !text(body, "description").isBlank(),
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0036"));
        next = "挂起";
        journal.closeTodos(tenant, id);
        label = "挂起";
      }
      case "resume" -> {
        require(
            state.equals("挂起") && Objects.equals(owner, user),
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0037"));
        next = "执行中";
        journal.notify(
            tenant, id, List.of(user), "点巡检待处理", text(snap, "equipmentName"), true, false);
        label = "恢复";
      }
      case "transfer" -> {
        if (!access(row, "转派")) deny();
        require(
            Set.of("未开始", "待执行", "执行中", "挂起", "已过期", "审核不通过").contains(state),
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0038"));
        require(
            !text(row, "kind").equals("daily") || !text(body, "description").isBlank(),
            com.windasoft.eam.common.MessageContext.text("daily.error.transferReason"));
        validation.users(tenant, body.get("owners"), true);
        snap.put("owners", strings(body.get("owners")));
        snap.put("personMode", "指定负责人");
        owner = null;
        next = now().isBefore(dateTime(row.get("due_at"))) ? "未开始" : "待执行";
        journal.closeTodos(tenant, id);
        journal.notify(
            tenant,
            id,
            ids(body.get("owners")),
            "点巡检待执行",
            text(snap, "equipmentName"),
            true,
            false);
        label = "转派";
      }
      case "exempt" -> {
        if (!text(row, "kind").equals("daily") || !access(row, "免检")) deny();
        require(
            Set.of("未开始", "待执行", "已过期").contains(state) && text(result, "startedAt").isBlank(),
            com.windasoft.eam.common.MessageContext.text("daily.error.exemptState"));
        require(
            !text(body, "description").isBlank() && text(body, "description").length() <= 1000,
            com.windasoft.eam.common.MessageContext.text("daily.error.reason"));
        dailyStorage.exempt(tenant, id, user, text(body, "description"));
        next = "已免检";
        label = "批准免检";
        journal.closeTodos(tenant, id);
      }
      case "void", "delete" -> {
        if (!access(row, "删除")) deny();
        if (action.equals("delete") && text(row, "kind").equals("daily"))
          require(taskAccess.canDelete(row),
              com.windasoft.eam.common.MessageContext.text("daily.error.deleteState"));
        require(
            !Set.of("已审核", "已完成", "已作废", "过期作废", "已免检").contains(state) || action.equals("delete"),
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0039"));
        require(
            !text(body, "description").isBlank() && text(body, "description").length() <= 1000,
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0461"));
        next = "已作废";
        journal.closeTodos(tenant, id);
        label = action.equals("delete") ? "删除任务" : "作废";
      }
      default ->
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.inspection.message0011");
    }
    if (action.equals("draft"))
      custom.saveDraft(
          com.windasoft.eam.service.business.BusinessObjects.inspection(text(row, "kind")),
          id,
          body);
    if (action.equals("submit"))
      custom.save(
          com.windasoft.eam.service.business.BusinessObjects.inspection(text(row, "kind")),
          id,
          body,
          false);
    if (action.equals("delete"))
      custom.remove(
          com.windasoft.eam.service.business.BusinessObjects.inspection(text(row, "kind")), id);
    repository.updateTask(
        next, owner, encode(snap), encode(result), action.equals("delete") ? 1 : 0, tenant, id);
    var details = new LinkedHashMap<String, Object>();
    details.put("说明", text(body, "description"));
    if (action.equals("amend-result")) {
      details.put("修改前", previousResult);
      details.put("修改后", encode(result));
      var previous = decode(previousResult);
      var changes = new ArrayList<String>();
      for (var item : rows(snap.get("items"))) {
        var before = rows(previous.get("items")).stream()
            .filter(r -> text(r, "id").equals(text(item, "id"))).findFirst().orElse(Map.of());
        var after = rows(result.get("items")).stream()
            .filter(r -> text(r, "id").equals(text(item, "id"))).findFirst().orElse(Map.of());
        if (!before.equals(after)) changes.add(text(item, "name") + "："
            + text(before, "value") + " → " + text(after, "value"));
      }
      if (!Objects.equals(previous.get("attachments"), result.get("attachments")))
        changes.add("附件已更新");
      details.put("变更内容", changes);
    }
    if (action.equals("scan")) details.put("设备编号", text(body, "equipmentCode"));
    if (action.equals("transfer"))
      details.put("负责人", ids(body.get("owners")).stream().map(u -> userName(tenant, u)).toList());
    journal.event(tenant, id, label, user, auth.user().getDisplayName(), details);
    if (action.equals("submit"))
      experiencePublisher.publishEvent(
          new com.windasoft.eam.event.experience.ExperienceEvents.TaskResult("点巡检", id));
    return action.equals("delete") ? Map.of("deleted", true) : task(id);
  }

  @Transactional
  public void batchReview(List<Map<String, Object>> tasks) {
    require(
        !tasks.isEmpty() && tasks.size() <= 100,
        com.windasoft.eam.common.MessageContext.text("server.inspection.message0040"));
    tasks.stream()
        .sorted(Comparator.comparingLong(t -> number(t.get("id"))))
        .forEach(t -> action(number(t.get("id")), "review", t));
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
