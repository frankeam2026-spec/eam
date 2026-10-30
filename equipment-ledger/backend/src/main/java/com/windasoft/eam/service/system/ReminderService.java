package com.windasoft.eam.service.system;

import static com.windasoft.eam.repository.system.NotificationRepository.yes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.notification.ReminderTiming;
import com.windasoft.eam.repository.system.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderService {
  private final ReminderRepository repo;
  private final NotificationRepository notifications;
  private final ObjectMapper json;

  public ReminderService(
      ReminderRepository repo, NotificationRepository notifications, ObjectMapper json) {
    this.repo = repo;
    this.notifications = notifications;
    this.json = json;
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

  private LocalDateTime trigger(ReminderTiming.Stage stage, Map<String, Object> rule) {
    long seconds =
        ReminderTiming.seconds(
            ((Number) rule.get("value")).intValue(), rule.get("unit").toString());
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

  private LocalDateTime cutoff(
      long tenant, String kind, Long plan, Map<String, Object> policy, Map<String, Object> rule) {
    var at = time(policy.get("effective_at"));
    var r = time(rule.get("effectiveAt"));
    if (r.isAfter(at)) at = r;
    if (plan != null) {
      var p = repo.plan(tenant, kind, plan);
      if (p.get("reminder_effective_at") != null
          && time(p.get("reminder_effective_at")).isAfter(at))
        at = time(p.get("reminder_effective_at"));
    }
    return at;
  }

  @Transactional
  public void scan(String module, long tenant, long id) {
    var row = repo.source(module, tenant, id, true);
    if (row.isEmpty()) return;
    String kind = kind(module, row);
    Long plan = module.equals("repair") ? null : number(row.get("plan_id"));
    var policy = notifications.effectivePolicy(tenant, kind, plan);
    if (policy.isEmpty() || policy.containsKey("off")) return;
    var stages = stages(module, tenant, id, row);
    if (yes(row.get("deleted")) || (module.equals("repair") && row.get("status").equals("挂起")))
      return;
    long pid = number(policy.get("id"));
    var rules = notifications.rules(tenant, pid);
    var people = notifications.people(tenant, pid);
    var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    for (var rule : rules) {
      if (!yes(rule.get("enabled"))) continue;
      var stage = stages.get(rule.get("scene"));
      if (stage == null) continue;
      var trigger = trigger(stage, rule);
      if (trigger == null
          || trigger.isAfter(now)
          || trigger.isBefore(cutoff(tenant, kind, plan, policy, rule))) continue;
      if (stage.scene().equals("BEFORE_DUE") && !now.isBefore(stage.anchor())) continue;
      String title = sceneName(stage.scene()) + "：" + equipment(row),
          content = content(module, row, stage, trigger);
      for (var person : people)
        if (person.get("ruleId").equals(rule.get("id"))) {
          long user = number(person.get("userId"));
          if (!notifications.activeUser(tenant, user)) continue;
          String key = "reminder:" + module + ":" + id + ":" + rule.get("id") + ":" + user;
          repo.message(
              tenant,
              key,
              module,
              id,
              number(rule.get("id")),
              ((Number) policy.get("row_version")).intValue(),
              stage.scene(),
              title,
              content,
              user,
              person.get("recipientKind").toString(),
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
    var policy = notifications.effectivePolicy(tenant, kind, plan);
    if (policy.isEmpty() || policy.containsKey("off")) return false;
    var rule =
        notifications.rules(tenant, number(policy.get("id"))).stream()
            .filter(
                r ->
                    r.get("id").toString().equals(message.get("rule_id").toString())
                        && yes(r.get("enabled")))
            .findFirst()
            .orElse(null);
    if (rule == null) return false;
    var stage = stages(module, tenant, id, row).get(rule.get("scene"));
    if (stage == null) return false;
    var trigger = trigger(stage, rule);
    var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    return trigger != null
        && !trigger.isAfter(now)
        && !trigger.isBefore(cutoff(tenant, kind, plan, policy, rule))
        && (!stage.scene().equals("BEFORE_DUE") || now.isBefore(stage.anchor()))
        && notifications.people(tenant, number(policy.get("id"))).stream()
            .anyMatch(
                p ->
                    p.get("ruleId").toString().equals(rule.get("id").toString())
                        && p.get("userId")
                            .toString()
                            .equals(message.get("recipient_id").toString()));
  }
}
