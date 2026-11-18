package com.windasoft.eam.service.system;

import static com.windasoft.eam.repository.system.NotificationRepository.yes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.notification.ReminderTiming;
import com.windasoft.eam.entity.system.NotifyPolicy;
import com.windasoft.eam.entity.system.NotifyRule;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.system.NotificationRepository;
import com.windasoft.eam.repository.system.NotifyRulePersonRepository;
import com.windasoft.eam.repository.system.NotifyRuleRepository;
import com.windasoft.eam.repository.system.ReminderRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderService {
  private final ReminderRepository repo;
  private final NotificationRepository notifications;
  private final ObjectMapper json;
  private final NotificationPolicyService policies;
  private final NotifyRuleRepository rules;
  private final NotifyRulePersonRepository people;
  private final SystemUserRepository users;

  public ReminderService(
      ReminderRepository repo,
      NotificationRepository notifications,
      ObjectMapper json,
      NotificationPolicyService policies,
      NotifyRuleRepository rules,
      NotifyRulePersonRepository people,
      SystemUserRepository users) {
    this.repo = repo;
    this.notifications = notifications;
    this.json = json;
    this.policies = policies;
    this.rules = rules;
    this.people = people;
    this.users = users;
  }

  public static LocalDateTime time(Object value) {
    if (value == null) return null;
    if (value instanceof java.sql.Timestamp t) return t.toLocalDateTime();
    if (value instanceof LocalDateTime t) return t;
    if (value instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
    return LocalDateTime.parse(value.toString().replace(' ', 'T'));
  }

  static long number(Object value) {
    return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
  }

  public static String kind(String module, Map<String, Object> row) {
    return module.equals("inspection") ? Objects.toString(row.get("kind")) : module;
  }

  private Map<String, ReminderTiming.Stage> stages(
      String module, long tenant, long id, Map<String, Object> row) {
    String status = Objects.toString(row.get("status"));
    if (module.equals("repair")
        && "REQUIRED".equals(row.get("approval_gate"))
        && row.get("approval_ready_at") == null) return Map.of();
    if (module.equals("repair"))
      return ReminderTiming.repair(
          time(
              row.get("approval_ready_at") == null
                  ? row.get("reported_at")
                  : row.get("approval_ready_at")),
          time(row.get("dispatched_at")),
          time(row.get("claimed_at")),
          time(row.get("completed_at")),
          status,
          yes(row.get("reminder_excluded")));
    var stages = new LinkedHashMap<String, ReminderTiming.Stage>();
    if (yes(row.get("deleted")) || yes(row.get("reminder_excluded"))) return stages;
    if (Set.of("未开始", "待执行", "执行中", "已过期", "过期作废").contains(status)) {
      var expires =
          module.equals("maintenance")
              ? (row.get("expires_date") == null ? null : time(row.get("expires_date")).plusDays(1))
              : time(row.get("expires_at"));
      if (expires != null)
        for (String scene : List.of("BEFORE_DUE", "AFTER_DUE"))
          stages.put(scene, new ReminderTiming.Stage(scene, expires));
    }
    if (Set.of("待审核", "待验收").contains(status) && row.get("submitted_at") != null)
      stages.put(
          "WAIT_REVIEW", new ReminderTiming.Stage("WAIT_REVIEW", time(row.get("submitted_at"))));
    return stages;
  }

  private LocalDateTime trigger(ReminderTiming.Stage stage, NotifyRule rule) {
    long seconds = ReminderTiming.seconds(rule.getOffsetValue(), rule.getOffsetUnit());
    return stage.scene().equals("BEFORE_DUE")
        ? stage.anchor().minusSeconds(seconds)
        : stage.trigger(seconds);
  }

  public static String sceneName(String scene) {
    return switch (scene) {
      case "WAIT_DISPATCH" -> "报修后未派工";
      case "REPORT_UNCLAIMED" -> "报修后无人接单";
      case "WAIT_CLAIM" -> "派工后未接单";
      case "PROCESS" -> "接单后未处理完成";
      case "WAIT_REVIEW" -> "提交后未审核或验收";
      case "BEFORE_DUE" -> "任务即将过期";
      case "AFTER_DUE" -> "任务已过期";
      default -> scene;
    };
  }

  private LocalDateTime cutoff(LocalDateTime bindingAt, NotifyPolicy policy, NotifyRule rule) {
    var at = policy.getEffectiveAt();
    if (bindingAt != null && bindingAt.isAfter(at)) at = bindingAt;
    return rule.getEffectiveAt().isAfter(at) ? rule.getEffectiveAt() : at;
  }

  /** 一次扫描内同一收件人只校验一次；分批限制 IN 大小，只读取身份字段。 */
  private Set<Long> activeRecipients(long tenant, List<Long> selected) {
    final int batchSize = 500;
    var active = new HashSet<Long>();
    for (int offset = 0; offset < selected.size(); offset += batchSize) {
      users
          .activeIdentities(
              tenant, selected.subList(offset, Math.min(offset + batchSize, selected.size())))
          .stream()
          .map(SystemUser::getId)
          .forEach(active::add);
    }
    return active;
  }

  @Transactional
  public void scan(String module, long tenant, long id) {
    var row = repo.source(module, tenant, id, true);
    if (row.isEmpty()) return;
    String kind = kind(module, row);
    Long plan = module.equals("repair") ? null : number(row.get("plan_id"));
    var binding = plan == null ? null : policies.planBinding(tenant, kind, plan);
    var policy = policies.effectivePolicy(tenant, kind, binding);
    var bindingAt = binding == null ? null : binding.reminderEffectiveAt();
    if (policy == null) return;
    var stages = stages(module, tenant, id, row);
    if (yes(row.get("deleted")) || (module.equals("repair") && row.get("status").equals("挂起")))
      return;
    long pid = policy.getId();
    var configuredRules = rules.list(tenant, pid);
    var recipients = people.list(tenant, pid);
    var byRule = recipients.stream().collect(Collectors.groupingBy(p -> p.ruleId()));
    Set<Long> active = null;
    var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    for (var rule : configuredRules) {
      if (rule.getEnabled() != 1) continue;
      var stage = stages.get(rule.getScene());
      if (stage == null) continue;
      var trigger = trigger(stage, rule);
      if (trigger == null
          || trigger.isAfter(now)
          || trigger.isBefore(cutoff(bindingAt, policy, rule))) continue;
      if (stage.scene().equals("BEFORE_DUE") && !now.isBefore(stage.anchor())) continue;
      String title = sceneName(stage.scene()) + "：" + equipment(row),
          content = content(module, row, stage, trigger);
      if (active == null) {
        active =
            activeRecipients(
                tenant, recipients.stream().map(p -> Long.valueOf(p.userId())).distinct().toList());
      }
      for (var person : byRule.getOrDefault(rule.getId().toString(), List.of())) {
        long user = Long.parseLong(person.userId());
        if (!active.contains(user)) continue;
        String key = "reminder:" + module + ":" + id + ":" + rule.getId() + ":" + user;
        repo.message(
            tenant,
            key,
            module,
            id,
            rule.getId(),
            policy.getRowVersion(),
            stage.scene(),
            title,
            content,
            user,
            person.recipientKind(),
            trigger);
        notifications.deliveries(tenant, key);
      }
    }
  }

  private Map<String, Object> snapshot(Map<String, Object> row) {
    try {
      return row.get("snapshot") == null
          ? Map.of()
          : json.readValue(row.get("snapshot").toString(), new TypeReference<>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }

  private String equipment(Map<String, Object> row) {
    return Objects.toString(
        row.getOrDefault("equipment_name", snapshot(row).get("equipmentName")), "");
  }

  private String content(
      String module, Map<String, Object> row, ReminderTiming.Stage stage, LocalDateTime trigger) {
    var snap = snapshot(row);
    return sceneName(stage.scene())
        + "\n编号："
        + Objects.toString(row.getOrDefault("order_no", row.get("code")), "")
        + "\n设备："
        + equipment(row)
        + "\n计划："
        + Objects.toString(snap.get("planName"), "")
        + "\n班次："
        + Objects.toString(snap.get("shiftName"), "")
        + "\n当前状态："
        + row.get("status")
        + "\n计时起点："
        + stage.anchor()
        + "\n提醒时间："
        + trigger
        + (row.get("status").equals("过期作废") ? "\n超期未执行，已自动作废" : "");
  }

  public boolean stillValid(Map<String, Object> message) {
    if (message.get("rule_id") == null) return true;
    long tenant = number(message.get("tenant_id")), id = number(message.get("object_id"));
    String module = message.get("module").toString();
    var row = repo.source(module, tenant, id, false);
    if (row.isEmpty()
        || yes(row.get("deleted"))
        || (module.equals("repair") && row.get("status").equals("挂起"))) return false;
    String kind = kind(module, row);
    Long plan = module.equals("repair") ? null : number(row.get("plan_id"));
    var binding = plan == null ? null : policies.planBinding(tenant, kind, plan);
    var policy = policies.effectivePolicy(tenant, kind, binding);
    var bindingAt = binding == null ? null : binding.reminderEffectiveAt();
    if (policy == null) return false;
    var rule =
        rules.list(tenant, policy.getId()).stream()
            .filter(
                r ->
                    r.getId().toString().equals(message.get("rule_id").toString())
                        && r.getEnabled() == 1)
            .findFirst()
            .orElse(null);
    if (rule == null) return false;
    var stage = stages(module, tenant, id, row).get(rule.getScene());
    if (stage == null) return false;
    var trigger = trigger(stage, rule);
    var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    return trigger != null
        && !trigger.isAfter(now)
        && !trigger.isBefore(cutoff(bindingAt, policy, rule))
        && (!stage.scene().equals("BEFORE_DUE") || now.isBefore(stage.anchor()))
        && people.list(tenant, policy.getId()).stream()
            .anyMatch(
                p ->
                    p.ruleId().equals(rule.getId().toString())
                        && p.userId().equals(message.get("recipient_id").toString()));
  }
}
