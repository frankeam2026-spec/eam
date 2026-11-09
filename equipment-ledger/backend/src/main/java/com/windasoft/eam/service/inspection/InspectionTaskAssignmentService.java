
package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles pending daily inspection task owners with the current arrangement staffing rules.
 *
 * <p>Only unclaimed tasks that have not started or been transferred are eligible. The plan
 * schedule service owns when a persisted plan change triggers an applied refresh; plan preview
 * can use the same calculation without writing changes.
 */
@Service
public class InspectionTaskAssignmentService {
  private final InspectionRepository repository;
  private final DailyArrangementRepository dailyStorage;
  private final DailyArrangementService arrangements;
  private final InspectionTaskJournal journal;
  private final ObjectMapper json;

  public InspectionTaskAssignmentService(
      InspectionRepository repository,
      DailyArrangementRepository dailyStorage,
      DailyArrangementService arrangements,
      InspectionTaskJournal journal,
      ObjectMapper json) {
    this.repository = repository;
    this.dailyStorage = dailyStorage;
    this.arrangements = arrangements;
    this.journal = journal;
    this.json = json;
  }

  /** Applies staffing changes inside the caller's plan-persistence transaction. */
  @Transactional(propagation = Propagation.MANDATORY)
  public int refreshPendingAssignments(long tenant, long planId, long actor) {
    return reconcilePendingAssignments(tenant, planId, actor, null, true);
  }

  /** Calculates the impact of a draft arrangement without changing existing tasks. */
  @Transactional(propagation = Propagation.MANDATORY)
  public int assignmentImpact(long tenant, long planId, Map<String, Object> draft) {
    return reconcilePendingAssignments(tenant, planId, 0, draft, false);
  }

  private int reconcilePendingAssignments(
      long tenant, long planId, long actor, Map<String, Object> draft, boolean apply) {
    var locked = repository.lockPlan(tenant, planId);
    if (locked.isEmpty()) return 0;
    var plan = draft == null ? decode(locked.getFirst().get("payload")) : draft;
    var current =
        draft == null ? dailyStorage.load(tenant, planId) : rows(draft.get("arrangements"));
    int changed = 0;
    for (var task : repository.lockOpenPlanTasks(tenant, planId)) {
      if (!Set.of(
              InspectionTaskStatus.NOT_STARTED.code(),
              InspectionTaskStatus.PENDING.code(),
              InspectionTaskStatus.EXPIRED.code())
          .contains(text(task, "status"))
          || task.get("owner_id") != null) continue;
      var result = decode(task.get("result"));
      if (!text(result, "startedAt").isBlank()
          || journal.hasAction(tenant, number(task.get("id")), "转派")) continue;
      var arrangement =
          current.stream()
              .filter(x -> text(x, "id").equals(text(task, "arrangement_id")))
              .findFirst()
              .orElse(null);
      if (arrangement == null) continue;
      var detail =
          arrangements.details(plan, arrangement).stream()
              .filter(d -> number(d.get("equipmentId")) == number(task.get("equipment_id")))
              .findFirst()
              .orElse(null);
      if (detail == null) continue;
      var dueAt = dateTime(task.get("due_at"));
      String slot = "";
      if (Set.of("shift", "slots").contains(text(arrangement, "frequency"))) {
        String key = text(task, "shift_id"), prefix = text(arrangement, "id") + ":";
        if (!key.startsWith(prefix)) continue;
        slot = key.substring(prefix.length());
        if (rows(arrangement.get("slots")).stream()
            .noneMatch(x -> key.equals(prefix + text(x, "id")))) continue;
      }
      var owners =
          arrangements.ownersAt(
              tenant, plan, arrangement, detail,
              LocalDate.parse(text(task, "business_date")), slot, dueAt);
      String mode = arrangements.open(arrangement, detail) ? "现场直接点检" : "指定负责人";
      var previous = decode(task.get("snapshot"));
      Long followUp = nullable(arrangement.get("followUpUserId"));
      if (new HashSet<>(ids(previous.get("owners"))).equals(new HashSet<>(owners))
          && text(previous, "personMode").equals(mode)
          && Objects.equals(nullable(task.get("follow_up_user_id")), followUp)) continue;
      if (apply) {
        long taskId = number(task.get("id"));
        dailyStorage.reassignPending(tenant, taskId, owners, mode, followUp);
        journal.closeTodos(tenant, taskId);
        journal.event(
            tenant,
            taskId,
            "调整执行人员",
            actor,
            "",
            Map.of(
                "原人员", ids(previous.get("owners")),
                "新人员", owners,
                "原方式", text(previous, "personMode"),
                "新方式", mode));
      }
      changed++;
    }
    return changed;
  }

  private Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0012");
    }
  }
}
