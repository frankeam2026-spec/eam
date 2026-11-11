package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.dateTime;
import static com.windasoft.eam.common.inspection.InspectionSupport.executionTodoEligible;
import static com.windasoft.eam.common.inspection.InspectionSupport.ids;
import static com.windasoft.eam.common.inspection.InspectionSupport.now;
import static com.windasoft.eam.common.inspection.InspectionSupport.nullable;
import static com.windasoft.eam.common.inspection.InspectionSupport.number;
import static com.windasoft.eam.common.inspection.InspectionSupport.shiftDuration;
import static com.windasoft.eam.common.inspection.InspectionSupport.text;
import static com.windasoft.eam.common.inspection.InspectionSupport.yes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.repository.system.NotificationRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the lifecycle of already-created inspection tasks after a generation pass.
 *
 * <p>This is intentionally separate from creating tasks: disabling a plan or its periodic type must
 * stop future generation without preventing existing tasks from being activated, expired, or
 * receiving the appropriate reminders.
 */
@Service
public class InspectionTaskLifecycleService {
  private final InspectionRepository repository;
  private final NotificationRepository notificationPolicies;
  private final InspectionTaskJournal journal;
  private final ObjectMapper json;

  public InspectionTaskLifecycleService(
      InspectionRepository repository,
      NotificationRepository notificationPolicies,
      InspectionTaskJournal journal,
      ObjectMapper json) {
    this.repository = repository;
    this.notificationPolicies = notificationPolicies;
    this.journal = journal;
    this.json = json;
  }

  /** 参与计划生成事务，任务状态、事件和待办一并提交或回滚。待执行通知复用创建阶段的时间资格； 超期跟进使用独立资格，不能因为任务禁止补检就一并取消跟进提醒。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void maintainOpenTasks(long tenant, long planId) {
    var tasks = repository.lockOpenPlanTasks(tenant, planId);
    if (tasks.isEmpty()) return;
    String kind = text(tasks.getFirst(), "kind");
    boolean newReminders = notificationPolicies.replacesLegacy(tenant, kind, planId);
    for (var task : tasks) {
      long id = number(task.get("id"));
      var snap = decode(task.get("snapshot"));
      String state = text(task, "status");
      LocalDateTime due = dateTime(task.get("due_at")), expires = dateTime(task.get("expires_at"));
      if (state.equals(InspectionTaskStatus.NOT_STARTED.code()) && !due.isAfter(now())) {
        repository.activateTask(tenant, id);
        String previous = state;
        state = InspectionTaskStatus.PENDING.code();
        journal.event(tenant, id, "任务到期开始", null, "系统", Map.of("原状态", previous, "新状态", state));
      }
      if (now().isAfter(expires)
          && Set.of(InspectionTaskStatus.NOT_STARTED.code(), InspectionTaskStatus.PENDING.code())
              .contains(state)) {
        String next =
            text(task, "kind").equals("daily") && task.get("arrangement_id") != null
                ? InspectionTaskStatus.EXPIRED.code()
                : (yes(snap.get("allowLate"))
                    ? InspectionTaskStatus.EXPIRED.code()
                    : InspectionTaskStatus.EXPIRED_CANCELLED.code());
        String previous = state;
        repository.changeTaskStatus(next, tenant, id);
        journal.event(tenant, id, next, null, "系统", Map.of("原状态", previous, "新状态", next));
        if (next.equals(InspectionTaskStatus.EXPIRED_CANCELLED.code()))
          journal.closeTodos(tenant, id);
        state = next;
      }
      Long followUp = nullable(task.get("follow_up_user_id"));
      // 历史任务可能保存了0而非null；不能为无效ID创建通知，正常跟进人仍保留超期提醒。
      if (!newReminders
          && text(task, "kind").equals("daily")
          && followUp != null
          && followUp > 0
          && now().isAfter(expires)
          && Set.of(
                  InspectionTaskStatus.PENDING.code(),
                  InspectionTaskStatus.EXPIRED.code(),
                  InspectionTaskStatus.IN_PROGRESS.code(),
                  InspectionTaskStatus.SUSPENDED.code(),
                  InspectionTaskStatus.REVIEW_REJECTED.code())
              .contains(state))
        journal.notify(
            tenant, id, List.of(followUp), "点巡检超期提醒", text(snap, "equipmentName"), false, true);
      boolean lateExecutionAllowed = yes(snap.get("allowLate"));
      if (state.equals(InspectionTaskStatus.EXPIRED.code()) && !lateExecutionAllowed)
        journal.closeTodos(tenant, id);
      if (executionTodoEligible(state, due, expires, lateExecutionAllowed, now()))
        journal.notify(
            tenant,
            id,
            ids(snap.get("owners")),
            "点巡检待执行",
            text(snap, "equipmentName"),
            true,
            false);
      if (state.equals(InspectionTaskStatus.NOT_STARTED.code())
          && number(snap.get("remindHours")) > 0
          && !now().isBefore(shiftDuration(due, snap, "remindHours", "remindUnit", -1)))
        journal.notify(
            tenant,
            id,
            ids(snap.get("owners")),
            "点巡检开始提醒",
            text(snap, "equipmentName"),
            false,
            true);
      if (!newReminders
          && Set.of(
                  InspectionTaskStatus.PENDING.code(),
                  InspectionTaskStatus.IN_PROGRESS.code(),
                  InspectionTaskStatus.SUSPENDED.code(),
                  InspectionTaskStatus.REVIEW_REJECTED.code())
              .contains(state)
          && number(snap.get("expireRemindHours")) > 0
          && now().isBefore(expires)
          && !now()
              .isBefore(shiftDuration(expires, snap, "expireRemindHours", "expireRemindUnit", -1)))
        journal.notify(
            tenant,
            id,
            ids(snap.get("owners")),
            "点巡检即将过期",
            text(snap, "equipmentName"),
            false,
            true);
    }
  }

  private Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new LocalizedArgumentException("server.inspection.message0012");
    }
  }
}
