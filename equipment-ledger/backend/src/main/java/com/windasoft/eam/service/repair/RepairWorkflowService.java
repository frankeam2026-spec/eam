package com.windasoft.eam.service.repair;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.AcceptanceDecision;
import com.windasoft.eam.common.FieldValidationException;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.common.MessageRef;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.domain.equipment.EquipmentSpareStatus;
import com.windasoft.eam.domain.repair.LegacyRepairMaterialStatus;
import com.windasoft.eam.domain.repair.RepairMethod;
import com.windasoft.eam.domain.repair.RepairNoticeKind;
import com.windasoft.eam.domain.repair.RepairOrderStatus;
import com.windasoft.eam.domain.repair.RepairWorkflowAction;
import com.windasoft.eam.dto.repair.RepairInboxView;
import com.windasoft.eam.dto.repair.RepairReadViews;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.equipment.EquipmentHistory;
import com.windasoft.eam.entity.inventory.LegacySpareStock;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.event.experience.ExperienceEvents;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.repository.equipment.EquipmentHistoryRepository;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.equipment.EquipmentSpareRepository;
import com.windasoft.eam.repository.inventory.LegacySpareStockRepository;
import com.windasoft.eam.repository.repair.RepairCandidateRepository;
import com.windasoft.eam.repository.repair.RepairEventRepository;
import com.windasoft.eam.repository.repair.RepairInboxRepository;
import com.windasoft.eam.repository.repair.RepairMaterialRepository;
import com.windasoft.eam.repository.repair.RepairOrderRepository;
import com.windasoft.eam.repository.repair.RepairWorkflowRepository;
import com.windasoft.eam.repository.system.NotificationRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import com.windasoft.eam.service.basedata.BaseDataReferenceService;
import com.windasoft.eam.service.business.BusinessValueService;
import com.windasoft.eam.service.experience.AnomalyService;
import com.windasoft.eam.service.experience.MaterialCustodyService;
import com.windasoft.eam.service.inventory.InventoryMutex;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 维修状态流转的事务编排入口：协调派工、接单、处理、验收、材料及履历。 跨工单、待办和库存的写入保持同一事务，不按代码行数机械拆分职责。 */
@Service
public class RepairWorkflowService {
  @org.springframework.beans.factory.annotation.Autowired private RepairApprovalService approvals;

  @org.springframework.beans.factory.annotation.Autowired private BusinessValueService custom;

  private final BaseDataReferenceService references;

  @org.springframework.beans.factory.annotation.Autowired
  private InventoryMutex experienceInventoryMutex;

  private final RepairWorkflowRepository flow;
  private final RepairOrderRepository orders;
  private final RepairCandidateRepository candidates;
  private final RepairEventRepository events;
  private final RepairInboxRepository inboxes;
  private final RepairMaterialRepository materials;
  private final LegacySpareStockRepository stocks;
  private final EquipmentSpareRepository spares;
  private final EquipmentQueryRepository equipmentRows;
  private final SystemUserRepository userRows;
  private final BaseItemRepository baseRows;
  private final EquipmentHistoryRepository history;
  private final NotificationRepository notifications;
  private final RepairRelationsService relations;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;

  public RepairWorkflowService(
      RepairWorkflowRepository flow,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json,
      BaseDataReferenceService references,
      RepairOrderRepository orders,
      RepairCandidateRepository candidates,
      RepairEventRepository events,
      RepairInboxRepository inboxes,
      RepairMaterialRepository materials,
      LegacySpareStockRepository stocks,
      EquipmentSpareRepository spares,
      EquipmentQueryRepository equipmentRows,
      SystemUserRepository userRows,
      BaseItemRepository baseRows,
      EquipmentHistoryRepository history,
      NotificationRepository notifications,
      RepairRelationsService relations) {
    this.relations = relations;
    this.orders = orders;
    this.candidates = candidates;
    this.events = events;
    this.inboxes = inboxes;
    this.materials = materials;
    this.stocks = stocks;
    this.spares = spares;
    this.equipmentRows = equipmentRows;
    this.userRows = userRows;
    this.baseRows = baseRows;
    this.history = history;
    this.notifications = notifications;
    this.references = references;
    this.flow = flow;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
  }

  public List<RepairReadViews.DispatchUser> userChoices() {
    if (!permissions.allowed("repair", "派工") && !permissions.allowed("repair", "处理")) {
      if (orders.assignedOrderCount(auth.tenantId(), auth.userId()) == 0) deny();
    }
    return flow.users(auth.tenantId());
  }

  public RepairOrder require(long id) {
    var row = orders.find(auth.tenantId(), id);
    if (row == null) throw new LocalizedArgumentException("server.repair.message0012");
    return row;
  }

  public void viewAllowed(RepairOrder row) {
    if (candidates.candidate(auth.tenantId(), row.getId(), auth.userId()) > 0
        || inboxes.hasInbox(auth.tenantId(), auth.userId(), row.getId()) > 0) return;
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
    result.put("candidates", candidates.candidates(auth.tenantId(), id));
    result.put(
        "events",
        events.events(auth.tenantId(), id).stream()
            .map(
                e -> {
                  var value = new LinkedHashMap<String, Object>();
                  value.put("id", e.getId().toString());
                  value.put("action", e.getAction());
                  value.put("actor", e.getActorName());
                  value.put(
                      "time",
                      e.getCreatedAt()
                          .format(
                              java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                  value.put("details", read(e.getDetailsJson()));
                  return value;
                })
            .toList());
    result.put("materials", materials.materials(auth.tenantId(), id));
    result.put("installed", spares.installed(auth.tenantId(), eq.getId()));
    result.put(
        "canClaim",
        RepairOrderStatus.PENDING_CLAIM.code().equals(row.getStatus())
            && candidates.candidate(auth.tenantId(), id, auth.userId()) > 0);
    result.put("canProcess", isOwner(row));
    result.put("canAccept", canAccept(row));
    result.put("canDispatch", canDispatch(row));
    result.put("canIssue", canIssue(eq.getCompanyOrgId()));
    result.put("results", results());
    result.put("approvalRounds", approvals.history(id));
    result.put(
        "canResubmit",
        RepairOrderStatus.PENDING_SUBMISSION.code().equals(row.getStatus())
            && Objects.equals(row.getReporterUserId(), auth.userId()));
    return result;
  }

  public List<Map<String, Object>> results() {
    return baseRows.processingResults(auth.tenantId()).stream()
        .map(
            item ->
                Map.<String, Object>of(
                    "id",
                    item.getId().toString(),
                    "name",
                    item.getName(),
                    "resolved",
                    BaseDataReferenceService.fixed(item)))
        .toList();
  }

  /** 执行动作前先获取互斥锁与工单数据库锁，再校验客户端版本。 后续任一业务校验失败时依靠事务回滚，避免状态和领料记录部分写入。 */
  @Transactional
  public RepairOrder action(long id, int version, JsonNode body) {
    experienceInventoryMutex.enter();
    var row = orders.lock(auth.tenantId(), id);
    if (row == null) throw new LocalizedArgumentException("server.repair.message0012");
    VersionGuard.check(row.getRowVersion(), version);
    String action = text(body, "action", 30), previous = row.getStatus();
    approvals.requireReleased(row);
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("before", previous);
    String label;
    switch (RepairWorkflowAction.parse(action)) {
      case DISPATCH, TRANSFER -> {
        if (action.equals("dispatch")) {
          permissions.checkRepair(row, "派工");
          state(row, RepairOrderStatus.PENDING_DISPATCH.code());
        } else {
          if (!isOwner(row)) permissions.checkRepair(row, "派工");
          state(
              row,
              RepairOrderStatus.PROCESSING.code(),
              RepairOrderStatus.SUSPENDED.code(),
              RepairOrderStatus.PENDING_CLAIM.code());
          String reason = text(body, "reason", 2000);
          details.put("reason", reason);
        }
        List<Long> ids = ids(body.get("userIds"));
        if (ids.isEmpty()) throw new LocalizedArgumentException("server.repair.message0013");
        var names = new ArrayList<String>();
        for (long userId : ids) names.add(user(userId).getDisplayName());
        inboxes.closeTodos(auth.tenantId(), id);
        candidates.clearCandidates(auth.tenantId(), id);
        for (long userId : ids) {
          candidates.addCandidate(IdWorker.getId(), auth.tenantId(), id, userId);
          todo(row, userId, RepairNoticeKind.PENDING_CLAIM.code(), true);
        }
        row.setDispatcherUserId(auth.userId());
        row.setAssigneeUserId(null); // Explicit SQL clears the previous owner (MP skips nulls).
        row.setAssignee(String.join("、", names));
        if (action.equals("dispatch")) row.setDispatchedAt(LocalDateTime.now());
        row.setStatus(RepairOrderStatus.PENDING_CLAIM.code());
        orders.clearOwner(auth.tenantId(), id);
        if (!ids.contains(auth.userId())) todo(row, auth.userId(), "等待接单", true);
        details.put("recipients", names);
        label = action.equals("dispatch") ? "派工" : "转派";
      }
      case CLAIM -> {
        row.setClaimedAt(LocalDateTime.now());
        state(row, RepairOrderStatus.PENDING_CLAIM.code());
        if (candidates.candidate(auth.tenantId(), id, auth.userId()) == 0) deny();
        row.setAssigneeUserId(auth.userId());
        row.setAssignee(auth.user().getDisplayName());
        row.setStatus(RepairOrderStatus.PROCESSING.code());
        candidates.claim(auth.tenantId(), id, auth.userId());
        inboxes.closeTodos(auth.tenantId(), id);
        todo(row, auth.userId(), RepairNoticeKind.PENDING_PROCESS.code(), true);
        for (var candidate : candidates.candidates(auth.tenantId(), id))
          if (!String.valueOf(auth.userId()).equals(candidate.id()))
            todo(
                row,
                Long.parseLong(String.valueOf(candidate.id())),
                "已由" + auth.user().getDisplayName() + "接单",
                false);
        if (row.getDispatcherUserId() != null
            && !Objects.equals(row.getDispatcherUserId(), auth.userId()))
          todo(row, row.getDispatcherUserId(), RepairNoticeKind.CLAIMED.code(), false);
        label = "接单";
      }
      case PROCESS -> {
        owner(row);
        state(row, RepairOrderStatus.PROCESSING.code());
        custom.save(
            "repair_process",
            id,
            body.get("customFields"),
            body.has("customSchemaVersion") ? body.path("customSchemaVersion").asInt() : null,
            Objects.toString(equipment(row).getCategoryId(), ""),
            false);
        String content = text(body, "content", 20000), method = text(body, "repairMethod", 20);
        if (content.isBlank()) throw new FieldValidationException("content", "请填写处理内容");
        if (!Set.of(RepairMethod.INTERNAL.code(), RepairMethod.EXTERNAL.code()).contains(method))
          throw new LocalizedArgumentException("server.repair.message0014");
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
        boolean resolved = BaseDataReferenceService.fixed(resultItem);
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
          if (hasPendingIssue(id))
            throw new LocalizedArgumentException("server.repair.message0015");
          row.setCompletedAt(LocalDateTime.now());
          row.setStatus(RepairOrderStatus.PENDING_ACCEPTANCE.code());
          inboxes.closeTodos(auth.tenantId(), id);
          Set<Long> recipients = new HashSet<>();
          recipients.add(row.getReporterUserId());
          for (var candidate : userRows.employees(auth.tenantId()))
            if (permissions.canAccept(candidate, row)) recipients.add(candidate.getId());
          for (long userId : recipients)
            todo(row, userId, RepairNoticeKind.PENDING_ACCEPTANCE.code(), true);
        }
        label = resolved ? "提交验收" : "记录处理";
      }
      case SUSPEND, RESUME -> {
        owner(row);
        state(
            row,
            action.equals("suspend")
                ? RepairOrderStatus.PROCESSING.code()
                : RepairOrderStatus.SUSPENDED.code());
        String reason = text(body, "reason", 2000);
        if (reason.isBlank()) throw new LocalizedArgumentException("server.repair.message0016");
        row.setStatus(
            action.equals("suspend")
                ? RepairOrderStatus.SUSPENDED.code()
                : RepairOrderStatus.PROCESSING.code());
        details.put("reason", reason);
        label = action.equals("suspend") ? RepairOrderStatus.SUSPENDED.code() : "恢复处理";
      }
      case OUTSOURCE -> {
        owner(row);
        state(row, RepairOrderStatus.PROCESSING.code(), RepairOrderStatus.SUSPENDED.code());
        String vendor = text(body, "vendor", 200);
        if (vendor.isBlank()) throw new LocalizedArgumentException("server.repair.message0017");
        row.setRepairMethod("外协");
        row.setOutsourceVendor(vendor);
        details.put("vendor", vendor);
        details.put("reason", text(body, "reason", 2000));
        label = "委外";
      }
      case ACCEPT, REJECT, RETURN -> {
        var decision = AcceptanceDecision.parse(action);
        state(row, RepairOrderStatus.PENDING_ACCEPTANCE.code());
        if (!canAccept(row)) deny();
        int score = body.path("score").asInt(0);
        if (score < 1 || score > 5)
          throw new LocalizedArgumentException("server.repair.message0018");
        String comment = text(body, "comment", 10000);
        if (comment.isBlank()) throw new LocalizedArgumentException("ui.equipment.text1088");
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
        row.setStatus(
            decision == AcceptanceDecision.ACCEPT
                ? RepairOrderStatus.COMPLETED.code()
                : RepairOrderStatus.PROCESSING.code());
        inboxes.closeTodos(auth.tenantId(), id);
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
      case MATERIAL -> throw new LocalizedArgumentException("server.repair.message0019");
      case CANCEL_MATERIAL -> {
        owner(row);
        state(row, RepairOrderStatus.PROCESSING.code(), RepairOrderStatus.SUSPENDED.code());
        long materialId = id(body, "materialId");
        if (materials.cancelMaterial(auth.tenantId(), materialId, id) != 1)
          throw new LocalizedArgumentException("server.repair.message0020");
        details.put("materialId", String.valueOf(materialId));
        label = "撤回领料";
        if (!hasPendingIssue(id)) inboxes.closeIssueTodos(auth.tenantId(), id);
      }
      case ISSUE -> throw new LocalizedArgumentException("server.repair.message0021");
      case INSTALL -> {
        owner(row);
        state(row, RepairOrderStatus.PROCESSING.code());
        var eq = equipment(row);
        long materialId = id(body, "materialId");
        var material = materials.lockMaterial(auth.tenantId(), materialId, id);
        BigDecimal quantity = quantity(body);
        if (material == null || materials.consume(auth.tenantId(), materialId, quantity) != 1)
          throw new LocalizedArgumentException("server.repair.message0022");
        long stockId = material.getStockId();
        var stock = stocks.lockStock(auth.tenantId(), stockId);
        spares.install(IdWorker.getId(), auth.tenantId(), eq.getId(), stockId, id, quantity);
        String name = stock.getName();
        recordSpareHistory(
            IdWorker.getId(),
            auth.tenantId(),
            eq.getId(),
            row.getOrderNo(),
            "换上 " + name + " × " + quantity,
            auth.user().getDisplayName(),
            EquipmentSpareStatus.INSTALLED.code(),
            "已安装");
        details.putAll(Map.of("name", name, "quantity", quantity));
        label = "换上备件";
      }
      case REMOVE -> {
        owner(row);
        state(row, RepairOrderStatus.PROCESSING.code());
        var eq = equipment(row);
        long installedId = id(body, "installedId");
        var installed =
            spares.installed(auth.tenantId(), eq.getId()).stream()
                .filter(x -> String.valueOf(installedId).equals(x.getId()))
                .findFirst()
                .orElseThrow(() -> new LocalizedArgumentException("server.repair.message0023"));
        if (spares.remove(auth.tenantId(), eq.getId(), installedId) != 1)
          throw new LocalizedArgumentException("server.repair.message0024");
        String reason = text(body, "reason", 2000);
        recordSpareHistory(
            IdWorker.getId(),
            auth.tenantId(),
            eq.getId(),
            row.getOrderNo(),
            "换下 " + installed.getName() + " × " + installed.getQuantity(),
            auth.user().getDisplayName(),
            EquipmentSpareStatus.REMOVED.code(),
            reason);
        details.putAll(
            Map.of(
                "name",
                installed.getName(),
                "quantity",
                installed.getQuantity(),
                "reason",
                reason));
        label = "换下备件";
      }
      default -> throw new LocalizedArgumentException("server.repair.message0025");
    }
    details.put("after", row.getStatus());
    VersionGuard.updated(orders.update(row));
    relations.save(row);
    record(row, label, details);
    return require(id);
  }

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.context.ApplicationEventPublisher experiencePublisher;

  @org.springframework.beans.factory.annotation.Autowired private MaterialCustodyService custody;

  /** 写入维修审计事件并发布状态事件；调用者负责保持业务事务边界。 */
  public void record(RepairOrder row, String action, Object details) {
    experiencePublisher.publishEvent(
        new ExperienceEvents.RepairState(row.getId(), row.getStatus()));
    events.event(
        IdWorker.getId(),
        auth.tenantId(),
        row.getId(),
        action,
        auth.userId(),
        auth.user().getDisplayName(),
        write(details));
  }

  /** 按现有消息元数据渲染标题；历史元数据损坏时沿用原始标题，不影响消息读取。 */
  public List<RepairInboxView> inbox() {
    var rows = inboxes.inboxRows(auth.tenantId(), auth.userId());
    for (var row : rows) {
      if (row.getMessageKey() != null && !row.getMessageKey().isBlank()) {
        try {
          Map<String, Object> args =
              json.readValue(
                  row.getMessageArgs(),
                  new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
          row.setTitle(MessageContext.render(new MessageRef(row.getMessageKey(), args)));
        } catch (Exception invalidHistoricalMetadata) {
          /* 旧消息可能没有完整翻译参数，保留入库标题。 */
        }
      }
    }
    return rows;
  }

  public void markRead(long id) {
    if (inboxes.markRead(auth.tenantId(), auth.userId(), id) != 1)
      throw new LocalizedArgumentException("server.repair.message0026");
  }

  public List<RepairReadViews.Stock> stockChoices(long orderId) {
    var row = require(orderId);
    viewAllowed(row);
    return stocks.stocks(auth.tenantId(), equipment(row).getCompanyOrgId()).stream()
        .map(RepairWorkflowService::stockView)
        .toList();
  }

  public List<RepairReadViews.Stock> inventory() {
    permissions.require("inventory", "查看");
    return stocks.stocks(auth.tenantId(), permissions.companyOfUser()).stream()
        .map(RepairWorkflowService::stockView)
        .toList();
  }

  @Transactional
  public void addStock(JsonNode body) {
    throw new LocalizedArgumentException("server.repair.message0027");
  }

  /** 维修工单有待出库领料时不得提交验收；撤回最后一笔待出库领料后关闭出库待办。 */
  private boolean hasPendingIssue(long orderId) {
    return materials.materials(auth.tenantId(), orderId).stream()
        .anyMatch(
            material -> LegacyRepairMaterialStatus.PENDING_ISSUE.code().equals(material.status()));
  }

  private void todo(RepairOrder row, Long userId, String kind, boolean todo) {
    if (userId != null) {
      long noticeId = IdWorker.getId();
      inboxes.inbox(
          noticeId,
          auth.tenantId(),
          userId,
          row.getId(),
          kind,
          row.getOrderNo() + " · " + row.getEquipmentName(),
          todo);
      notifications.captureSource("eam_repair_inbox", auth.tenantId(), noticeId);
    }
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
    var e = equipmentRows.findByCode(auth.tenantId(), row.getEquipmentCode());
    if (e == null) throw new LocalizedArgumentException("server.common.message0064");
    return e;
  }

  private SystemUser user(long id) {
    var u = userRows.activeUser(auth.tenantId(), id);
    if (u == null) throw new LocalizedArgumentException("server.repair.message0028");
    return u;
  }

  private static void state(RepairOrder row, String... allowed) {
    if (!Arrays.asList(allowed).contains(row.getStatus()))
      throw new LocalizedArgumentException("server.repair.message0029");
  }

  private static void deny() {
    throw new LocalizedStatusException(HttpStatus.FORBIDDEN, "server.repair.message0030");
  }

  private static String text(JsonNode n, String key, int max) {
    String v = n.path(key).asText("").trim();
    if (v.length() > max) throw new LocalizedArgumentException("server.repair.message0031", key);
    return v;
  }

  private static long id(JsonNode node, String key) {
    try {
      long id = Long.parseLong(node.path(key).asText());
      if (id <= 0) throw new NumberFormatException();
      return id;
    } catch (Exception e) {
      throw new LocalizedArgumentException("server.repair.message0032", key);
    }
  }

  private static List<Long> ids(JsonNode node) {
    if (node == null || node.isNull()) return List.of();
    if (!node.isArray() || node.size() > 50)
      throw new LocalizedArgumentException("server.repair.message0033");
    var ids = new LinkedHashSet<Long>();
    for (var x : node)
      try {
        long value = Long.parseLong(x.asText());
        if (value <= 0) throw new NumberFormatException();
        ids.add(value);
      } catch (Exception e) {
        throw new LocalizedArgumentException("server.repair.message0034");
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
      throw new LocalizedArgumentException("server.repair.message0035");
    }
  }

  @org.springframework.beans.factory.annotation.Autowired
  @org.springframework.context.annotation.Lazy
  private AnomalyService anomalySources;

  public String attachments(JsonNode node) {
    if (node == null || node.isNull()) return "[]";
    if (!node.isArray() || node.size() > 30)
      throw new LocalizedArgumentException("server.repair.message0036");
    for (var file : node) {
      String fileId = file.path("id").asText();
      String sourceUrl = file.path("url").asText();
      if (sourceUrl.matches("/api/v1/anomalies/[0-9]+/files/[0-9a-f-]{36}")) {
        var segments = sourceUrl.split("/");
        if (!sourceUrl.endsWith("/" + fileId))
          throw new LocalizedArgumentException("server.repair.message0037");
        anomalySources.checkRepairAttachment(Long.parseLong(segments[4]), fileId);
        continue;
      }
      if (!fileId.matches("[0-9a-fA-F-]{36}")
          || !file.path("url").asText().equals("/api/v1/repairs/attachments/" + fileId))
        throw new LocalizedArgumentException("server.repair.message0038");
    }
    return write(node);
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new LocalizedArgumentException("server.repair.message0039");
    }
  }

  private Object read(String value) {
    try {
      return json.readTree(value == null ? "[]" : value);
    } catch (Exception e) {
      return List.of();
    }
  }

  /** 备件安装、拆卸与履历保存共用当前维修事务，不在设备仓储编排维修流程。 */
  private void recordSpareHistory(
      long id,
      long tenant,
      long equipment,
      String number,
      String description,
      String actor,
      String status,
      String result) {
    var row = new EquipmentHistory();
    row.setId(id);
    row.setTenantId(tenant);
    row.setEquipmentId(equipment);
    row.setRecordType("SPARE_CHANGE");
    row.setRecordNo(number);
    row.setDescription(description);
    row.setOperatorName(actor);
    row.setStatus(status);
    row.setResult(result);
    history.insert(row);
  }

  private static RepairReadViews.Stock stockView(LegacySpareStock row) {
    return new RepairReadViews.Stock(
        row.getId().toString(), row.getCode(), row.getName(), row.getModel(), row.getQuantity());
  }
}
