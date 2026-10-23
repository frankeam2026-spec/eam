package com.windasoft.eam.service.repair;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.AcceptanceDecision;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RepairWorkflowService {
  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.business.BusinessValueService custom;

  private final com.windasoft.eam.service.basedata.BaseDataReferenceService references;

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.inventory.InventoryMutex experienceInventoryMutex;

  private final com.windasoft.eam.repository.repair.RepairWorkflowRepository flow;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;

  public RepairWorkflowService(
      com.windasoft.eam.repository.repair.RepairWorkflowRepository flow,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json,
      com.windasoft.eam.service.basedata.BaseDataReferenceService references) {
    this.references = references;
    this.flow = flow;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
  }

  public List<Map<String, Object>> userChoices() {
    if (!permissions.allowed("repair", "派工") && !permissions.allowed("repair", "处理")) {
      if (flow.assignedOrderCount(auth.tenantId(), auth.userId()) == 0) deny();
    }
    return flow.users(auth.tenantId());
  }

  public RepairOrder require(long id) {
    var row = flow.findOrder(auth.tenantId(), id);
    if (row == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0012");
    return row;
  }

  public void viewAllowed(RepairOrder row) {
    if (flow.candidate(auth.tenantId(), row.getId(), auth.userId()) > 0
        || flow.hasInbox(auth.tenantId(), auth.userId(), row.getId()) > 0) return;
    permissions.checkRepair(row, "查看");
  }

  public boolean isOwner(RepairOrder row) {
    return Objects.equals(row.getAssigneeUserId(), auth.userId());
  }

  public boolean canAccept(RepairOrder row) {
    return permissions.canAccept(auth.user(), row);
  }

  public Map<String, Object> detail(long id) {
    var row = require(id);
    viewAllowed(row);
    var eq = equipment(row);
    Map<String, Object> result = new LinkedHashMap<>();
    var extra = custom.read("repair_report", row.getId());
    row.setCustomFields(extra.customFields());
    row.setCustomFieldLabels(extra.customFieldLabels());
    row.setCustomSchemaVersion(extra.customSchemaVersion());
    var processing = new LinkedHashMap<String, Object>();
    processing.put("id", Long.toString(id));
    custom.attach("repair_process", processing);
    result.put("processingFields", processing);
    result.put("order", row);
    result.put("categoryId", eq.getCategoryId() == null ? "" : eq.getCategoryId().toString());
    result.put("model", eq.getModel() == null ? "" : eq.getModel());
    result.put("candidates", flow.candidates(auth.tenantId(), id));
    result.put(
        "events",
        flow.events(auth.tenantId(), id).stream()
            .map(
                e -> {
                  var value = new LinkedHashMap<>(e);
                  value.put("details", read(String.valueOf(e.get("details"))));
                  return value;
                })
            .toList());
    result.put("materials", flow.materials(auth.tenantId(), id));
    result.put("installed", flow.installed(auth.tenantId(), eq.getId()));
    result.put(
        "canClaim",
        "待接单".equals(row.getStatus()) && flow.candidate(auth.tenantId(), id, auth.userId()) > 0);
    result.put("canProcess", isOwner(row));
    result.put("canAccept", canAccept(row));
    result.put("canDispatch", canDispatch(row));
    result.put("canIssue", canIssue(eq.getCompanyOrgId()));
    result.put("results", results());
    return result;
  }

  public List<Map<String, Object>> results() {
    return flow.processingResults(auth.tenantId()).stream()
        .map(
            item ->
                Map.<String, Object>of(
                    "id",
                    item.getId().toString(),
                    "name",
                    item.getName(),
                    "resolved",
                    com.windasoft.eam.service.basedata.BaseDataReferenceService.fixed(item)))
        .toList();
  }

  @Transactional
  public RepairOrder action(long id, int version, JsonNode body) {
    experienceInventoryMutex.enter();
    var row = flow.lock(auth.tenantId(), id);
    if (row == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0012");
    VersionGuard.check(row.getRowVersion(), version);
    String action = text(body, "action", 30), previous = row.getStatus();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("before", previous);
    String label;
    switch (action) {
      case "dispatch", "transfer" -> {
        if (action.equals("dispatch")) {
          permissions.checkRepair(row, "派工");
          state(row, "待派工");
        } else {
          if (!isOwner(row)) permissions.checkRepair(row, "派工");
          state(row, "处理中", "挂起", "待接单");
          String reason = text(body, "reason", 2000);
          details.put("reason", reason);
        }
        List<Long> ids = ids(body.get("userIds"));
        if (ids.isEmpty())
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0013");
        var names = new ArrayList<String>();
        for (long userId : ids) names.add(user(userId).getDisplayName());
        flow.closeTodos(auth.tenantId(), id);
        flow.clearCandidates(auth.tenantId(), id);
        for (long userId : ids) {
          flow.addCandidate(IdWorker.getId(), auth.tenantId(), id, userId);
          todo(row, userId, "待接单", true);
        }
        row.setDispatcherUserId(auth.userId());
        row.setAssigneeUserId(null); // Explicit SQL clears the previous owner (MP skips nulls).
        row.setAssignee(String.join("、", names));
        if(action.equals("dispatch"))row.setDispatchedAt(LocalDateTime.now());
        row.setStatus("待接单");
        flow.clearOwner(auth.tenantId(), id);
        if (!ids.contains(auth.userId())) todo(row, auth.userId(), "等待接单", true);
        details.put("recipients", names);
        label = action.equals("dispatch") ? "派工" : "转派";
      }
      case "claim" -> {
        row.setClaimedAt(LocalDateTime.now());
        state(row, "待接单");
        if (flow.candidate(auth.tenantId(), id, auth.userId()) == 0) deny();
        row.setAssigneeUserId(auth.userId());
        row.setAssignee(auth.user().getDisplayName());
        row.setStatus("处理中");
        flow.claim(auth.tenantId(), id, auth.userId());
        flow.closeTodos(auth.tenantId(), id);
        todo(row, auth.userId(), "待处理", true);
        for (var candidate : flow.candidates(auth.tenantId(), id))
          if (!String.valueOf(auth.userId()).equals(candidate.get("id")))
            todo(
                row,
                Long.parseLong(String.valueOf(candidate.get("id"))),
                "已由" + auth.user().getDisplayName() + "接单",
                false);
        if (row.getDispatcherUserId() != null
            && !Objects.equals(row.getDispatcherUserId(), auth.userId()))
          todo(row, row.getDispatcherUserId(), "已接单", false);
        label = "接单";
      }
      case "process" -> {
        owner(row);
        state(row, "处理中");
        custom.save(
            "repair_process",
            id,
            body.get("customFields"),
            body.has("customSchemaVersion") ? body.path("customSchemaVersion").asInt() : null,
            Objects.toString(equipment(row).getCategoryId(), ""),
            false);
        String content = text(body, "content", 20000), method = text(body, "repairMethod", 20);
        if (content.isBlank())
          throw new com.windasoft.eam.common.FieldValidationException("content", "请填写处理内容");
        if (!Set.of("自修", "外协").contains(method))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0014");
        var typeItem =
            references.required(
                auth.tenantId(),
                body.path("faultTypeId").asText(),
                "FAULT_TYPE",
                "faultTypeId",
                "故障类型");
        var causeItem =
            references.required(
                auth.tenantId(),
                body.path("faultCauseId").asText(),
                "FAULT_CAUSE",
                "faultCauseId",
                "故障原因");
        var resultItem =
            references.required(
                auth.tenantId(),
                body.path("processingResultId").asText(),
                "PROCESS_RESULT",
                "processingResultId",
                "处理结果");
        String type = typeItem.getName(),
            cause = causeItem.getName(),
            result = resultItem.getName();
        boolean resolved =
            com.windasoft.eam.service.basedata.BaseDataReferenceService.fixed(resultItem);
        var collaborators = ids(body.get("collaboratorIds"));
        var collaboratorsView = new ArrayList<Map<String, String>>();
        for (long userId : collaborators)
          collaboratorsView.add(
              Map.of("id", String.valueOf(userId), "name", user(userId).getDisplayName()));
        row.setFaultCauseId(causeItem.getId());
        row.setFaultTypeId(typeItem.getId());
        row.setProcessingResultId(resultItem.getId());
        row.setFaultCause(cause);
        row.setFaultType(type);
        row.setRepairMethod(method);
        row.setRepairAction(content);
        row.setProcessingResult(result);
        row.setPreventiveMeasures(text(body, "prevention", 20000));
        row.setCollaboratorsJson(write(collaboratorsView));
        row.setProcessAttachmentsJson(attachments(body.get("attachments")));
        if (resolved) custody.finishTask("维修", id, json.convertValue(body, java.util.Map.class));
        details.put("faultTypeId", typeItem.getId().toString());
        details.put("faultCauseId", causeItem.getId().toString());
        details.put("processingResultId", resultItem.getId().toString());
        details.putAll(
            Map.of(
                "faultCause",
                cause,
                "faultType",
                type,
                "repairMethod",
                method,
                "content",
                content,
                "result",
                result,
                "prevention",
                row.getPreventiveMeasures(),
                "collaborators",
                collaboratorsView,
                "attachments",
                read(row.getProcessAttachmentsJson())));
        if (resolved) {
          if (flow.materials(auth.tenantId(), id).stream()
              .anyMatch(x -> "待出库".equals(x.get("status"))))
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.repair.message0015");
          row.setCompletedAt(LocalDateTime.now());
          row.setStatus("待验收");
          flow.closeTodos(auth.tenantId(), id);
          Set<Long> recipients = new HashSet<>();
          recipients.add(row.getReporterUserId());
          for (var candidate : flow.activeUsers(auth.tenantId()))
            if (permissions.canAccept(candidate, row)) recipients.add(candidate.getId());
          for (long userId : recipients) todo(row, userId, "待验收", true);
        }
        label = resolved ? "提交验收" : "记录处理";
      }
      case "suspend", "resume" -> {
        owner(row);
        state(row, action.equals("suspend") ? "处理中" : "挂起");
        String reason = text(body, "reason", 2000);
        if (reason.isBlank())
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0016");
        row.setStatus(action.equals("suspend") ? "挂起" : "处理中");
        details.put("reason", reason);
        label = action.equals("suspend") ? "挂起" : "恢复处理";
      }
      case "outsource" -> {
        owner(row);
        state(row, "处理中", "挂起");
        String vendor = text(body, "vendor", 200);
        if (vendor.isBlank())
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0017");
        row.setRepairMethod("外协");
        row.setOutsourceVendor(vendor);
        details.put("vendor", vendor);
        details.put("reason", text(body, "reason", 2000));
        label = "委外";
      }
      case "accept", "reject", "return" -> {
        var decision = AcceptanceDecision.parse(action);
        state(row, "待验收");
        if (!canAccept(row)) deny();
        int score = body.path("score").asInt(0);
        if (score < 1 || score > 5)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0018");
        String comment = text(body, "comment", 10000);
        if (comment.isBlank())
          throw new com.windasoft.eam.common.LocalizedArgumentException("ui.equipment.text1088");
        if (decision == AcceptanceDecision.ACCEPT) {
          row.setAcceptedAt(LocalDateTime.now());
          row.setAcceptedOwnerId(row.getAssigneeUserId());
          row.setAcceptanceScore(score);
          details.put("ownerId", Objects.toString(row.getAssigneeUserId(), ""));
        }
        details.put("score", score);
        details.put("decision", decision.code());
        details.put("result", decision.label());
        details.put("comment", comment);
        details.put("attachments", read(attachments(body.get("attachments"))));
        row.setAcceptanceComment(comment);
        row.setStatus(decision == AcceptanceDecision.ACCEPT ? "已完成" : "处理中");
        flow.closeTodos(auth.tenantId(), id);
        todo(
            row,
            row.getAssigneeUserId(),
            decision == AcceptanceDecision.ACCEPT ? "验收通过" : "待处理",
            decision != AcceptanceDecision.ACCEPT);
        if (row.getReporterUserId() != null
            && !Objects.equals(row.getReporterUserId(), auth.userId()))
          todo(
              row,
              row.getReporterUserId(),
              decision == AcceptanceDecision.ACCEPT ? "验收通过" : "验收打回",
              false);
        label = decision == AcceptanceDecision.ACCEPT ? "验收通过" : "验收打回";
      }
      case "material" ->
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0019");
      case "cancelMaterial" -> {
        owner(row);
        state(row, "处理中", "挂起");
        long materialId = id(body, "materialId");
        if (flow.cancelMaterial(auth.tenantId(), materialId, id) != 1)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0020");
        details.put("materialId", String.valueOf(materialId));
        label = "撤回领料";
        if (flow.materials(auth.tenantId(), id).stream()
            .noneMatch(x -> "待出库".equals(x.get("status"))))
          flow.closeIssueTodos(auth.tenantId(), id);
      }
      case "issue" ->
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0021");
      case "install" -> {
        owner(row);
        state(row, "处理中");
        var eq = equipment(row);
        long materialId = id(body, "materialId");
        var material = flow.lockMaterial(auth.tenantId(), materialId, id);
        BigDecimal quantity = quantity(body);
        if (material == null || flow.consume(auth.tenantId(), materialId, quantity) != 1)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0022");
        long stockId = ((Number) material.get("stock_id")).longValue();
        var stock = flow.lockStock(auth.tenantId(), stockId);
        flow.install(IdWorker.getId(), auth.tenantId(), eq.getId(), stockId, id, quantity);
        String name = String.valueOf(stock.get("name"));
        flow.history(
            IdWorker.getId(),
            auth.tenantId(),
            eq.getId(),
            row.getOrderNo(),
            "换上 " + name + " × " + quantity,
            auth.user().getDisplayName(),
            "在用",
            "已安装");
        details.putAll(Map.of("name", name, "quantity", quantity));
        label = "换上备件";
      }
      case "remove" -> {
        owner(row);
        state(row, "处理中");
        var eq = equipment(row);
        long installedId = id(body, "installedId");
        var installed =
            flow.installed(auth.tenantId(), eq.getId()).stream()
                .filter(x -> String.valueOf(installedId).equals(x.get("id")))
                .findFirst()
                .orElseThrow(
                    () ->
                        new com.windasoft.eam.common.LocalizedArgumentException(
                            "server.repair.message0023"));
        if (flow.remove(auth.tenantId(), eq.getId(), installedId) != 1)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0024");
        String reason = text(body, "reason", 2000);
        flow.history(
            IdWorker.getId(),
            auth.tenantId(),
            eq.getId(),
            row.getOrderNo(),
            "换下 " + installed.get("name") + " × " + installed.get("quantity"),
            auth.user().getDisplayName(),
            "已换下",
            reason);
        details.putAll(
            Map.of(
                "name",
                installed.get("name"),
                "quantity",
                installed.get("quantity"),
                "reason",
                reason));
        label = "换下备件";
      }
      default ->
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0025");
    }
    details.put("after", row.getStatus());
    VersionGuard.updated(flow.updateOrder(row));
    record(row, label, details);
    return require(id);
  }

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.context.ApplicationEventPublisher experiencePublisher;

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.experience.MaterialCustodyService custody;

  public void record(RepairOrder row, String action, Object details) {
    experiencePublisher.publishEvent(
        new com.windasoft.eam.event.experience.ExperienceEvents.RepairState(
            row.getId(), row.getStatus()));
    flow.event(
        IdWorker.getId(),
        auth.tenantId(),
        row.getId(),
        action,
        auth.userId(),
        auth.user().getDisplayName(),
        write(details));
  }

  public List<Map<String, Object>> inbox() {
    return (java.util.List<java.util.Map<String, Object>>)
        com.windasoft.eam.common.NotificationMessages.render(
            flow.inboxRows(auth.tenantId(), auth.userId()));
  }

  public void markRead(long id) {
    if (flow.markRead(auth.tenantId(), auth.userId(), id) != 1)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0026");
  }

  public List<Map<String, Object>> stockChoices(long orderId) {
    var row = require(orderId);
    viewAllowed(row);
    return flow.stocks(auth.tenantId(), equipment(row).getCompanyOrgId());
  }

  public List<Map<String, Object>> inventory() {
    permissions.require("inventory", "查看");
    return flow.stocks(auth.tenantId(), permissions.companyOfUser());
  }

  @Transactional
  public void addStock(JsonNode body) {
    throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0027");
  }

  private void todo(RepairOrder row, Long userId, String kind, boolean todo) {
    if (userId != null)
      flow.inbox(
          IdWorker.getId(),
          auth.tenantId(),
          userId,
          row.getId(),
          kind,
          row.getOrderNo() + " · " + row.getEquipmentName(),
          todo);
  }

  private boolean canDispatch(RepairOrder row) {
    try {
      permissions.checkRepair(row, "派工");
      return true;
    } catch (ResponseStatusException denied) {
      return false;
    }
  }

  private boolean canIssue(long company) {
    try {
      permissions.checkInventoryCompany(company, "出库");
      return true;
    } catch (ResponseStatusException denied) {
      return false;
    }
  }

  private void owner(RepairOrder row) {
    if (!isOwner(row)) deny();
  }

  private Equipment equipment(RepairOrder row) {
    var e = flow.equipment(auth.tenantId(), row.getEquipmentCode());
    if (e == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0064");
    return e;
  }

  private SystemUser user(long id) {
    var u = flow.activeUser(auth.tenantId(), id);
    if (u == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0028");
    return u;
  }

  private static void state(RepairOrder row, String... allowed) {
    if (!Arrays.asList(allowed).contains(row.getStatus()))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0029");
  }

  private static void deny() {
    throw new com.windasoft.eam.common.LocalizedStatusException(
        HttpStatus.FORBIDDEN, "server.repair.message0030");
  }

  private static String text(JsonNode n, String key, int max) {
    String v = n.path(key).asText("").trim();
    if (v.length() > max)
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.repair.message0031", key);
    return v;
  }

  private static long id(JsonNode node, String key) {
    try {
      long id = Long.parseLong(node.path(key).asText());
      if (id <= 0) throw new NumberFormatException();
      return id;
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.repair.message0032", key);
    }
  }

  private static List<Long> ids(JsonNode node) {
    if (node == null || node.isNull()) return List.of();
    if (!node.isArray() || node.size() > 50)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0033");
    var ids = new LinkedHashSet<Long>();
    for (var x : node)
      try {
        long value = Long.parseLong(x.asText());
        if (value <= 0) throw new NumberFormatException();
        ids.add(value);
      } catch (Exception e) {
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0034");
      }
    return List.copyOf(ids);
  }

  private static BigDecimal quantity(JsonNode body) {
    try {
      var value = new BigDecimal(body.path("quantity").asText());
      if (value.signum() <= 0
          || value.scale() > 3
          || value.compareTo(new BigDecimal("99999999999")) > 0) throw new ArithmeticException();
      return value;
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0035");
    }
  }

  @org.springframework.beans.factory.annotation.Autowired
  @org.springframework.context.annotation.Lazy
  private com.windasoft.eam.service.experience.AnomalyService anomalySources;

  public String attachments(JsonNode node) {
    if (node == null || node.isNull()) return "[]";
    if (!node.isArray() || node.size() > 30)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0036");
    for (var file : node) {
      String fileId = file.path("id").asText();
      String sourceUrl = file.path("url").asText();
      if (sourceUrl.matches("/api/v1/anomalies/[0-9]+/files/[0-9a-f-]{36}")) {
        var segments = sourceUrl.split("/");
        if (!sourceUrl.endsWith("/" + fileId))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0037");
        anomalySources.checkRepairAttachment(Long.parseLong(segments[4]), fileId);
        continue;
      }
      if (!fileId.matches("[0-9a-fA-F-]{36}")
          || !file.path("url").asText().equals("/api/v1/repairs/attachments/" + fileId))
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0038");
    }
    return write(node);
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0039");
    }
  }

  private Object read(String value) {
    try {
      return json.readTree(value == null ? "[]" : value);
    } catch (Exception e) {
      return List.of();
    }
  }
}
