package com.windasoft.eam.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.basedata.*;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.equipment.*;
import com.windasoft.eam.system.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RepairWorkflowService {
  private final RepairWorkflowMapper flow;
  private final RepairOrderMapper orders;
  private final EquipmentMapper equipment;
  private final SystemUserMapper users;
  private final BaseItemMapper base;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;

  public RepairWorkflowService(
      RepairWorkflowMapper flow,
      RepairOrderMapper orders,
      EquipmentMapper equipment,
      SystemUserMapper users,
      BaseItemMapper base,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json) {
    this.flow = flow;
    this.orders = orders;
    this.equipment = equipment;
    this.users = users;
    this.base = base;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
  }

  public List<Map<String, Object>> userChoices() {
    if (!permissions.allowed("repair", "派工") && !permissions.allowed("repair", "处理")) {
      if (orders.selectCount(
              new LambdaQueryWrapper<RepairOrder>()
                  .eq(RepairOrder::getTenantId, auth.tenantId())
                  .eq(RepairOrder::getAssigneeUserId, auth.userId())
                  .eq(RepairOrder::getDeleted, 0))
          == 0) deny();
    }
    return flow.users(auth.tenantId());
  }

  public RepairOrder require(long id) {
    var row =
        orders.selectOne(
            new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getTenantId, auth.tenantId())
                .eq(RepairOrder::getId, id)
                .eq(RepairOrder::getDeleted, 0));
    if (row == null) throw new IllegalArgumentException("工单不存在");
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
    result.put("order", row);
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

  public List<String> results() {
    var result = new ArrayList<String>();
    result.add("已解决");
    result.addAll(
        base
            .selectList(
                new LambdaQueryWrapper<BaseItem>()
                    .eq(BaseItem::getTenantId, auth.tenantId())
                    .eq(BaseItem::getItemType, "PROCESS_RESULT")
                    .eq(BaseItem::getStatus, 1)
                    .orderByAsc(BaseItem::getSortNo))
            .stream()
            .map(BaseItem::getName)
            .filter(x -> !"已解决".equals(x))
            .toList());
    return result;
  }

  @Transactional
  public RepairOrder action(long id, int version, JsonNode body) {
    var row = flow.lock(auth.tenantId(), id);
    if (row == null) throw new IllegalArgumentException("工单不存在");
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
        if (ids.isEmpty()) throw new IllegalArgumentException("请选择至少一位人员");
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
        row.setStatus("待接单");
        flow.clearOwner(auth.tenantId(), id);
        if (!ids.contains(auth.userId())) todo(row, auth.userId(), "等待接单", true);
        details.put("recipients", names);
        label = action.equals("dispatch") ? "派工" : "转派";
      }
      case "claim" -> {
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
        String content = text(body, "content", 20000),
            cause = text(body, "faultCause", 10000),
            type = text(body, "faultType", 200),
            method = text(body, "repairMethod", 20),
            result = text(body, "result", 100);
        if (content.isBlank() || cause.isBlank() || type.isBlank() || result.isBlank())
          throw new IllegalArgumentException("请填写故障原因、故障类型、处理内容和处理结果");
        if (!Set.of("自修", "外协").contains(method)) throw new IllegalArgumentException("维修方式无效");
        if (!results().contains(result)) throw new IllegalArgumentException("处理结果未在基础数据中定义");
        if (base.selectCount(
                new LambdaQueryWrapper<BaseItem>()
                    .eq(BaseItem::getTenantId, auth.tenantId())
                    .eq(BaseItem::getItemType, "FAULT_TYPE")
                    .eq(BaseItem::getName, type)
                    .eq(BaseItem::getStatus, 1))
            == 0) throw new IllegalArgumentException("请选择有效故障类型");
        if (base.selectCount(
                new LambdaQueryWrapper<BaseItem>()
                    .eq(BaseItem::getTenantId, auth.tenantId())
                    .eq(BaseItem::getItemType, "FAULT_CAUSE")
                    .eq(BaseItem::getName, cause)
                    .eq(BaseItem::getStatus, 1))
            == 0) throw new IllegalArgumentException("请选择基础数据中有效的故障原因");
        var collaborators = ids(body.get("collaboratorIds"));
        var collaboratorsView = new ArrayList<Map<String, String>>();
        for (long userId : collaborators)
          collaboratorsView.add(
              Map.of("id", String.valueOf(userId), "name", user(userId).getDisplayName()));
        row.setFaultCause(cause);
        row.setFaultType(type);
        row.setRepairMethod(method);
        row.setRepairAction(content);
        row.setProcessingResult(result);
        row.setPreventiveMeasures(text(body, "prevention", 20000));
        row.setCollaboratorsJson(write(collaboratorsView));
        row.setProcessAttachmentsJson(attachments(body.get("attachments")));
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
        if ("已解决".equals(result)) {
          if (flow.materials(auth.tenantId(), id).stream()
              .anyMatch(x -> "待出库".equals(x.get("status"))))
            throw new IllegalArgumentException("请先完成或撤回待出库的领料申请");
          row.setCompletedAt(LocalDateTime.now());
          row.setStatus("待验收");
          flow.closeTodos(auth.tenantId(), id);
          Set<Long> recipients = new HashSet<>();
          recipients.add(row.getReporterUserId());
          for (var candidate :
              users.selectList(
                  new LambdaQueryWrapper<SystemUser>()
                      .eq(SystemUser::getTenantId, auth.tenantId())
                      .eq(SystemUser::getStatus, 1)))
            if (permissions.canAccept(candidate, row)) recipients.add(candidate.getId());
          for (long userId : recipients) todo(row, userId, "待验收", true);
        }
        label = "已解决".equals(result) ? "提交验收" : "记录处理";
      }
      case "suspend", "resume" -> {
        owner(row);
        state(row, action.equals("suspend") ? "处理中" : "挂起");
        String reason = text(body, "reason", 2000);
        if (reason.isBlank()) throw new IllegalArgumentException("请填写说明");
        row.setStatus(action.equals("suspend") ? "挂起" : "处理中");
        details.put("reason", reason);
        label = action.equals("suspend") ? "挂起" : "恢复处理";
      }
      case "outsource" -> {
        owner(row);
        state(row, "处理中", "挂起");
        String vendor = text(body, "vendor", 200);
        if (vendor.isBlank()) throw new IllegalArgumentException("请填写外协单位");
        row.setRepairMethod("外协");
        row.setOutsourceVendor(vendor);
        details.put("vendor", vendor);
        details.put("reason", text(body, "reason", 2000));
        label = "委外";
      }
      case "accept", "return" -> {
        state(row, "待验收");
        if (!canAccept(row)) deny();
        int score = body.path("score").asInt(0);
        if (score < 1 || score > 5) throw new IllegalArgumentException("请选择1～5分评分");
        String comment = text(body, "comment", 10000);
        if (comment.isBlank()) throw new IllegalArgumentException("请填写验收说明");
        details.put("score", score);
        details.put("result", action.equals("accept") ? "通过" : "打回");
        details.put("comment", comment);
        details.put("attachments", read(attachments(body.get("attachments"))));
        row.setAcceptanceComment(comment);
        row.setStatus(action.equals("accept") ? "已完成" : "处理中");
        flow.closeTodos(auth.tenantId(), id);
        todo(
            row,
            row.getAssigneeUserId(),
            action.equals("accept") ? "验收通过" : "待处理",
            !action.equals("accept"));
        if (row.getReporterUserId() != null
            && !Objects.equals(row.getReporterUserId(), auth.userId()))
          todo(row, row.getReporterUserId(), action.equals("accept") ? "验收通过" : "验收打回", false);
        label = action.equals("accept") ? "验收通过" : "验收打回";
      }
      case "material" -> throw new IllegalArgumentException("请通过任务用料提交领料申请，旧库存需先完成期初初始化");
      case "cancelMaterial" -> {
        owner(row);
        state(row, "处理中", "挂起");
        long materialId = id(body, "materialId");
        if (flow.cancelMaterial(auth.tenantId(), materialId, id) != 1)
          throw new IllegalArgumentException("只能撤回待出库的领料申请");
        details.put("materialId", String.valueOf(materialId));
        label = "撤回领料";
        if (flow.materials(auth.tenantId(), id).stream()
            .noneMatch(x -> "待出库".equals(x.get("status"))))
          flow.closeIssueTodos(auth.tenantId(), id);
      }
      case "issue" -> throw new IllegalArgumentException("请在物料与库存的领料单中确认发料，旧领料申请可撤回后重新提交");
      case "install" -> {
        owner(row);
        state(row, "处理中");
        var eq = equipment(row);
        long materialId = id(body, "materialId");
        var material = flow.lockMaterial(auth.tenantId(), materialId, id);
        BigDecimal quantity = quantity(body);
        if (material == null || flow.consume(auth.tenantId(), materialId, quantity) != 1)
          throw new IllegalArgumentException("可安装数量不足，请先由仓管出库");
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
                .orElseThrow(() -> new IllegalArgumentException("在用备件不存在"));
        if (flow.remove(auth.tenantId(), eq.getId(), installedId) != 1)
          throw new IllegalArgumentException("该备件已被换下");
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
      default -> throw new IllegalArgumentException("不支持的工单操作");
    }
    details.put("after", row.getStatus());
    VersionGuard.updated(orders.updateById(row));
    record(row, label, details);
    return require(id);
  }

  public void record(RepairOrder row, String action, Object details) {
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
    return flow.inboxRows(auth.tenantId(), auth.userId());
  }

  public void markRead(long id) {
    if (flow.markRead(auth.tenantId(), auth.userId(), id) != 1)
      throw new IllegalArgumentException("提醒不存在");
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
    throw new IllegalArgumentException("请通过物料档案和入库单维护库存");
  }

  private void todo(RepairOrder row, Long userId, String kind, boolean todo) {
    if (userId != null)
      flow.inbox(
          IdWorker.getId(),
          auth.tenantId(),
          userId,
          row.getId(),
          kind,
          row.getOrderNo() + " · " + row.getEquipmentName() + " · " + kind,
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
    var e =
        equipment.selectOne(
            new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getTenantId, auth.tenantId())
                .eq(Equipment::getEquipmentCode, row.getEquipmentCode()));
    if (e == null) throw new IllegalArgumentException("设备不存在");
    return e;
  }

  private SystemUser user(long id) {
    var u =
        users.selectOne(
            new LambdaQueryWrapper<SystemUser>()
                .eq(SystemUser::getTenantId, auth.tenantId())
                .eq(SystemUser::getId, id)
                .eq(SystemUser::getStatus, 1));
    if (u == null) throw new IllegalArgumentException("请选择当前租户内的启用用户");
    return u;
  }

  private static void state(RepairOrder row, String... allowed) {
    if (!Arrays.asList(allowed).contains(row.getStatus()))
      throw new IllegalArgumentException("工单当前状态不允许此操作");
  }

  private static void deny() {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有该工单的操作权限");
  }

  private static String text(JsonNode n, String key, int max) {
    String v = n.path(key).asText("").trim();
    if (v.length() > max) throw new IllegalArgumentException("内容过长：" + key);
    return v;
  }

  private static long id(JsonNode node, String key) {
    try {
      long id = Long.parseLong(node.path(key).asText());
      if (id <= 0) throw new NumberFormatException();
      return id;
    } catch (Exception e) {
      throw new IllegalArgumentException("请选择有效记录：" + key);
    }
  }

  private static List<Long> ids(JsonNode node) {
    if (node == null || node.isNull()) return List.of();
    if (!node.isArray() || node.size() > 50) throw new IllegalArgumentException("最多选择50位人员");
    var ids = new LinkedHashSet<Long>();
    for (var x : node)
      try {
        long value = Long.parseLong(x.asText());
        if (value <= 0) throw new NumberFormatException();
        ids.add(value);
      } catch (Exception e) {
        throw new IllegalArgumentException("人员编号无效");
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
      throw new IllegalArgumentException("数量必须大于0，最多3位小数");
    }
  }

  public String attachments(JsonNode node) {
    if (node == null || node.isNull()) return "[]";
    if (!node.isArray() || node.size() > 30) throw new IllegalArgumentException("最多30个附件");
    for (var file : node) {
      String fileId = file.path("id").asText();
      if (!fileId.matches("[0-9a-fA-F-]{36}")
          || !file.path("url").asText().equals("/api/v1/repairs/attachments/" + fileId))
        throw new IllegalArgumentException("请选择有效上传附件");
    }
    return write(node);
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("记录格式无效");
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
