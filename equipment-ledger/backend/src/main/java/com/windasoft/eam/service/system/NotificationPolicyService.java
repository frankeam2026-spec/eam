package com.windasoft.eam.service.system;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.dto.system.NotificationContracts;
import com.windasoft.eam.dto.system.NotificationViews;
import com.windasoft.eam.dto.system.PlanReminderBinding;
import com.windasoft.eam.entity.system.NotifyPolicy;
import com.windasoft.eam.entity.system.NotifyRule;
import com.windasoft.eam.entity.system.NotifyRulePerson;
import com.windasoft.eam.repository.system.NotificationRepository;
import com.windasoft.eam.repository.system.NotifyDefaultRepository;
import com.windasoft.eam.repository.system.NotifyPolicyRepository;
import com.windasoft.eam.repository.system.NotifyRulePersonRepository;
import com.windasoft.eam.repository.system.NotifyRuleRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 策略配置及默认选择由本服务编排，存储仓储不决定业务启用和接收人规则。 */
@Service
public class NotificationPolicyService {
  public static final Set<String> KINDS = Set.of("repair", "daily", "periodic", "maintenance");
  public static final Set<String> REPAIR_SCENES =
      Set.of("WAIT_DISPATCH", "REPORT_UNCLAIMED", "WAIT_CLAIM", "PROCESS", "WAIT_REVIEW");
  public static final Set<String> TASK_SCENES = Set.of("BEFORE_DUE", "AFTER_DUE", "WAIT_REVIEW");
  private final NotifyPolicyRepository policies;
  private final NotifyRuleRepository rules;
  private final NotifyRulePersonRepository people;
  private final NotifyDefaultRepository defaults;
  private final SystemUserRepository users;
  private final NotificationRepository repo;
  private final AuthContext auth;
  private final PermissionService permissions;

  public NotificationPolicyService(
      NotifyPolicyRepository policies,
      NotifyRuleRepository rules,
      NotifyRulePersonRepository people,
      NotifyDefaultRepository defaults,
      SystemUserRepository users,
      NotificationRepository repo,
      AuthContext auth,
      PermissionService permissions) {
    this.policies = policies;
    this.rules = rules;
    this.people = people;
    this.defaults = defaults;
    this.users = users;
    this.repo = repo;
    this.auth = auth;
    this.permissions = permissions;
  }

  static void require(boolean valid, String message) {
    if (!valid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  public NotificationViews.Rows<NotificationViews.PolicyRow> list(
      String kind, String q, int page, int size) {
    permissions.require("notificationPolicy", "查看");
    var p = policies.list(auth.tenantId(), kind, q, page, size);
    return new NotificationViews.Rows<>(
        p.getRecords().stream()
            .map(
                r ->
                    new NotificationViews.PolicyRow(
                        r.getId().toString(),
                        r.getName(),
                        r.getBusinessKind(),
                        r.getEnabled(),
                        r.getRowVersion(),
                        r.getEffectiveAt()))
            .toList(),
        p.getTotal());
  }

  public NotificationViews.Choices<NotificationViews.Choice> choices(
      String kind, String q, int page, int size) {
    require(KINDS.contains(kind), "提醒业务类型无效");
    if (!permissions.allowed("notificationPolicy", "查看"))
      permissions.require(
          switch (kind) {
            case "repair" -> "repair";
            case "maintenance" -> "maintenancePlan";
            case "daily" -> "inspectionDailyPlan";
            default -> "inspectionPeriodicPlan";
          },
          "查看");
    var p = policies.choices(auth.tenantId(), kind, q, page, size);
    return new NotificationViews.Choices<>(
        p.getRecords().stream()
            .map(
                r ->
                    new NotificationViews.Choice(r.getId().toString(), r.getName(), r.getEnabled()))
            .toList(),
        new NotificationViews.Total(p.getTotal()));
  }

  public NotificationViews.Policy detail(long id) {
    permissions.require("notificationPolicy", "查看");
    var p = policies.find(auth.tenantId(), id, false);
    if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "提醒策略不存在");
    return view(p);
  }

  private NotificationViews.Policy view(NotifyPolicy p) {
    var persons = people.list(auth.tenantId(), p.getId());
    var byRule = persons.stream().collect(Collectors.groupingBy(NotificationViews.Person::ruleId));
    var list =
        rules.list(auth.tenantId(), p.getId()).stream()
            .map(
                r -> {
                  var selected = byRule.getOrDefault(r.getId().toString(), List.of());
                  return new NotificationViews.Rule(
                      r.getId().toString(),
                      r.getScene(),
                      r.getOffsetValue(),
                      r.getOffsetUnit(),
                      r.getEnabled() == 1,
                      r.getEffectiveAt(),
                      selected.stream()
                          .filter(x -> x.recipientKind().equals("TO"))
                          .map(NotificationViews.Person::userId)
                          .toList(),
                      selected.stream()
                          .filter(x -> x.recipientKind().equals("CC"))
                          .map(NotificationViews.Person::userId)
                          .toList());
                })
            .toList();
    return new NotificationViews.Policy(
        p.getId().toString(),
        p.getName(),
        p.getBusinessKind(),
        p.getEnabled() == 1,
        p.getRowVersion(),
        list,
        persons);
  }

  /** 主体锁在先，规则和固定人员同一事务替换；仅规则语义或接收人变化推进下界，避免保存重发。 */
  @Transactional
  public NotificationViews.Policy save(NotificationContracts.Policy input) {
    permissions.require("notificationPolicy", "编辑");
    require(KINDS.contains(input.businessKind()), "提醒业务类型无效");
    long tenant = auth.tenantId(), id = input.id() == null ? IdWorker.getId() : input.id();
    var old = input.id() == null ? null : policies.find(tenant, id, true);
    if (input.id() != null && old == null)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "提醒策略不存在");
    if (old != null) {
      VersionGuard.check(old.getRowVersion(), input.rowVersion() == null ? -1 : input.rowVersion());
      require(input.businessKind().equals(old.getBusinessKind()), "已有策略不能更换业务类型");
    }
    require(
        !input.enabled() || input.rules().stream().anyMatch(NotificationContracts.Rule::enabled),
        "启用策略至少需要一条有效规则");
    var before = old == null ? List.<NotifyRule>of() : rules.list(tenant, id);
    var byId = before.stream().collect(Collectors.toMap(NotifyRule::getId, r -> r));
    var beforePeople = old == null ? List.<NotificationViews.Person>of() : people.list(tenant, id);
    var grouped =
        beforePeople.stream().collect(Collectors.groupingBy(NotificationViews.Person::ruleId));
    var ids = new HashSet<Long>();
    var allUsers = new TreeSet<Long>();
    for (var r : input.rules()) {
      require(
          (input.businessKind().equals("repair") ? REPAIR_SCENES : TASK_SCENES).contains(r.scene()),
          "提醒场景无效");
      require(Set.of("MINUTE", "HOUR", "DAY").contains(r.unit()), "提醒时间单位无效");
      require(!r.enabled() || !r.recipients().isEmpty(), "启用规则必须选择提醒人");
      require(r.recipients().size() + r.cc().size() <= 200, "每条提醒最多选择200人");
      if (r.id() != null) require(ids.add(r.id()) && byId.containsKey(r.id()), "规则ID无效或重复");
      allUsers.addAll(r.recipients());
      allUsers.addAll(r.cc());
    }
    // 接收人按唯一ID分块校验，避免规则×人数逐条查询；仍排除停用、删除及其他租户。
    var selected = new ArrayList<>(allUsers);
    for (int offset = 0; offset < selected.size(); offset += 500) {
      var batch = selected.subList(offset, Math.min(offset + 500, selected.size()));
      require(users.activeIdentities(tenant, batch).size() == batch.size(), "提醒人员不存在、停用或不属于当前租户");
    }
    var p = new NotifyPolicy();
    p.setId(id);
    p.setTenantId(tenant);
    p.setName(input.name().trim());
    p.setBusinessKind(input.businessKind());
    p.setEnabled(input.enabled() ? 1 : 0);
    if (old == null) policies.insert(p);
    else policies.update(p);
    int sort = 0;
    for (var r : input.rules()) {
      long rid = r.id() == null ? IdWorker.getId() : r.id();
      var prior = byId.get(rid);
      var to = new TreeSet<>(r.recipients());
      var cc = new TreeSet<>(r.cc());
      cc.removeAll(to);
      var previous = grouped.getOrDefault(Long.toString(rid), List.of());
      var previousTo =
          previous.stream()
              .filter(x -> x.recipientKind().equals("TO"))
              .map(x -> Long.valueOf(x.userId()))
              .collect(Collectors.toCollection(TreeSet::new));
      var previousCc =
          previous.stream()
              .filter(x -> x.recipientKind().equals("CC"))
              .map(x -> Long.valueOf(x.userId()))
              .collect(Collectors.toCollection(TreeSet::new));
      boolean changed =
          prior == null
              || !r.scene().equals(prior.getScene())
              || r.value() != prior.getOffsetValue()
              || !r.unit().equals(prior.getOffsetUnit())
              || r.enabled() != (prior.getEnabled() == 1)
              || !to.equals(previousTo)
              || !cc.equals(previousCc);
      var row = new NotifyRule();
      row.setId(rid);
      row.setTenantId(tenant);
      row.setPolicyId(id);
      row.setScene(r.scene());
      row.setOffsetValue(r.value());
      row.setOffsetUnit(r.unit());
      row.setEnabled(r.enabled() ? 1 : 0);
      row.setSortNo(sort++);
      if (prior == null) rules.insert(row);
      else rules.update(row, changed);
      var recipients = new ArrayList<NotifyRulePerson>();
      for (var user : to) recipients.add(person(tenant, rid, user, "TO"));
      for (var user : cc) recipients.add(person(tenant, rid, user, "CC"));
      people.replace(tenant, rid, recipients);
    }
    for (var r : before) if (!ids.contains(r.getId())) rules.disable(tenant, r.getId());
    return detail(id);
  }

  private NotifyRulePerson person(long tenant, long rule, long user, String kind) {
    var p = new NotifyRulePerson();
    p.setTenantId(tenant);
    p.setRuleId(rule);
    p.setUserId(user);
    p.setRecipientKind(kind);
    return p;
  }

  public List<NotificationViews.Default> defaults() {
    permissions.require("notificationPolicy", "查看");
    return defaults.list(auth.tenantId()).stream()
        .map(
            d ->
                new NotificationViews.Default(
                    d.getBusinessKind(),
                    d.getPolicyId() == null ? null : d.getPolicyId().toString(),
                    d.getRowVersion()))
        .toList();
  }

  @Transactional
  public void setDefault(String kind, NotificationContracts.DefaultPolicy input) {
    permissions.require("notificationPolicy", "编辑");
    require(KINDS.contains(kind), "提醒业务类型无效");
    var old = defaults.find(auth.tenantId(), kind, true);
    VersionGuard.check(old.getRowVersion(), input.rowVersion());
    validatePolicy(auth.tenantId(), kind, input.policyId());
    defaults.save(auth.tenantId(), kind, input.policyId());
  }

  private void validatePolicy(long tenant, String kind, Long id) {
    if (id == null) return;
    var p = policies.find(tenant, id, false);
    require(p != null && kind.equals(p.getBusinessKind()) && p.getEnabled() == 1, "请选择本业务已启用的提醒策略");
  }

  public void saveBinding(long tenant, String kind, long id, Map<String, Object> body) {
    if (!body.containsKey("reminderMode")) {
      return;
    }
    String mode = Objects.toString(body.get("reminderMode"), "DEFAULT");
    Long selected =
        mode.equals("POLICY")
                && body.get("reminderPolicyId") != null
                && !body.get("reminderPolicyId").toString().isBlank()
            ? Long.valueOf(body.get("reminderPolicyId").toString())
            : null;
    saveBinding(tenant, kind, id, mode, selected);
  }

  /** 类型化计划调用同一绑定规则，不复制策略校验或改变生效时间更新条件。 */
  public void saveBinding(long tenant, String kind, long id, String requestedMode, Long selected) {
    String mode = Objects.toString(requestedMode, "DEFAULT");
    require(Set.of("DEFAULT", "POLICY", "OFF").contains(mode), "提醒策略模式无效");
    Long policy = mode.equals("POLICY") ? selected : null;
    require(!mode.equals("POLICY") || policy != null, "请选择提醒策略");
    validatePolicy(tenant, kind, policy);
    repo.binding(tenant, kind, id, mode, policy);
  }

  public PlanReminderBinding planBinding(long tenant, String kind, long id) {
    return repo.binding(tenant, kind, id);
  }

  public void attachBinding(long tenant, String kind, long id, Map<String, Object> target) {
    var b = planBinding(tenant, kind, id);
    target.put("reminderMode", b.reminderMode());
    target.put("reminderPolicyId", b.reminderPolicyId());
    target.put("reminderEffectiveAt", b.reminderEffectiveAt());
    target.put("reminderPolicyName", b.reminderPolicyName());
  }

  public NotificationViews.Rows<NotificationViews.User> users(String q, int page, int size) {
    permissions.require("notificationPolicy", "查看");
    var p = users.notificationChoices(auth.tenantId(), q, page, size);
    return new NotificationViews.Rows<>(
        p.getRecords().stream()
            .map(
                u ->
                    new NotificationViews.User(
                        u.getId().toString(), u.getDisplayName(), u.getEmail()))
            .toList(),
        p.getTotal());
  }

  /** OFF取消旧提醒；默认绑定和策略本身取较晚生效时间，不补发绑定前错过的时间点。 */
  public NotifyPolicy effectivePolicy(long tenant, String kind, Long plan) {
    return resolvePolicy(tenant, kind, plan == null ? null : planBinding(tenant, kind, plan));
  }

  private NotifyPolicy resolvePolicy(long tenant, String kind, PlanReminderBinding binding) {
    if (binding != null && binding.reminderMode().equals("OFF")) return null;
    if (binding != null && binding.reminderMode().equals("POLICY"))
      return binding.reminderPolicyId() == null
          ? null
          : enabled(policies.find(tenant, Long.parseLong(binding.reminderPolicyId()), false));
    var selected = defaults.find(tenant, kind, false);
    if (selected == null || selected.getPolicyId() == null) return null;
    var p = enabled(policies.find(tenant, selected.getPolicyId(), false));
    if (p != null && selected.getEffectiveAt().isAfter(p.getEffectiveAt()))
      p.setEffectiveAt(selected.getEffectiveAt());
    return p;
  }

  private NotifyPolicy enabled(NotifyPolicy p) {
    return p != null && p.getEnabled() == 1 ? p : null;
  }

  public boolean replacesLegacy(long tenant, String kind, long plan) {
    var binding = planBinding(tenant, kind, plan);
    return binding.reminderMode().equals("OFF") || resolvePolicy(tenant, kind, binding) != null;
  }
}
