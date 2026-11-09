package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspectionTaskAssignmentServiceTest {
  private static final long TENANT = 31L;
  private static final long PLAN_ID = 42L;
  private static final long TASK_ID = 88L;

  private final InspectionRepository repository = mock(InspectionRepository.class);
  private final DailyArrangementRepository storage = mock(DailyArrangementRepository.class);
  private final DailyArrangementService arrangements = mock(DailyArrangementService.class);
  private final InspectionTaskJournal journal = mock(InspectionTaskJournal.class);
  private final InspectionTaskAssignmentService service =
      new InspectionTaskAssignmentService(
          repository, storage, arrangements, journal, new ObjectMapper());

  private Map<String, Object> plan;
  private Map<String, Object> arrangement;
  private Map<String, Object> detail;

  @BeforeEach
  void setUp() {
    plan = new HashMap<>();
    plan.put("payload", "{\"enabled\":true}");
    arrangement = new HashMap<>();
    arrangement.put("id", "9");
    arrangement.put("frequency", "daily");
    arrangement.put("followUpUserId", 30L);
    detail = Map.of("equipmentId", 101L);
    when(repository.lockPlan(TENANT, PLAN_ID)).thenReturn(List.of(plan));
    when(storage.load(TENANT, PLAN_ID)).thenReturn(List.of(arrangement));
    when(arrangements.details(anyMap(), anyMap())).thenReturn(List.of(detail));
    when(arrangements.ownersAt(
            eq(TENANT), anyMap(), anyMap(), anyMap(), any(LocalDate.class), eq(""), any(LocalDateTime.class)))
        .thenReturn(List.of(11L));
    when(arrangements.open(anyMap(), anyMap())).thenReturn(false);
    when(journal.hasAction(TENANT, TASK_ID, "转派")).thenReturn(false);
  }

  @Test
  void impactCountsChangedAssignmentsWithoutMutatingTasks() {
    var task = eligibleTask();
    when(repository.lockOpenPlanTasks(TENANT, PLAN_ID)).thenReturn(List.of(task));

    assertEquals(1, service.assignmentImpact(TENANT, PLAN_ID, draft()));

    verify(storage, never()).reassignPending(anyLong(), anyLong(), anyList(), anyString(), any());
    verify(journal, never()).closeTodos(anyLong(), anyLong());
    verify(journal, never()).event(anyLong(), anyLong(), anyString(), any(), anyString(), any());
  }

  @Test
  void refreshAppliesChangedOwnersAndRecordsTheAssignmentChange() {
    var task = eligibleTask();
    when(repository.lockOpenPlanTasks(TENANT, PLAN_ID)).thenReturn(List.of(task));

    assertEquals(1, service.refreshPendingAssignments(TENANT, PLAN_ID, 77L));

    verify(storage).reassignPending(TENANT, TASK_ID, List.of(11L), "指定负责人", 30L);
    verify(journal).closeTodos(TENANT, TASK_ID);
    verify(journal).event(
        eq(TENANT),
        eq(TASK_ID),
        eq("调整执行人员"),
        eq(77L),
        eq(""),
        eq(Map.of(
            "原人员", List.of(10L),
            "新人员", List.of(11L),
            "原方式", "指定负责人",
            "新方式", "指定负责人")));
  }

  @Test
  void doesNotReassignClaimedStartedTransferredOrNonPendingTasks() {
    var claimed = eligibleTask();
    claimed.put("owner_id", 99L);
    var started = eligibleTask();
    started.put("id", TASK_ID + 1);
    started.put("result", "{\"startedAt\":\"2026-11-09T08:00:00\"}");
    var transferred = eligibleTask();
    transferred.put("id", TASK_ID + 2);
    var completed = eligibleTask();
    completed.put("id", TASK_ID + 3);
    completed.put("status", InspectionTaskStatus.COMPLETED.code());
    when(journal.hasAction(TENANT, TASK_ID + 2, "转派")).thenReturn(true);
    when(repository.lockOpenPlanTasks(TENANT, PLAN_ID))
        .thenReturn(List.of(claimed, started, transferred, completed));

    assertEquals(0, service.refreshPendingAssignments(TENANT, PLAN_ID, 77L));

    verify(arrangements, never()).details(anyMap(), anyMap());
    verify(storage, never()).reassignPending(anyLong(), anyLong(), anyList(), anyString(), any());
    verify(journal, never()).closeTodos(anyLong(), anyLong());
  }

  private Map<String, Object> eligibleTask() {
    var task = new HashMap<String, Object>();
    task.put("id", TASK_ID);
    task.put("status", InspectionTaskStatus.PENDING.code());
    task.put("owner_id", null);
    task.put("arrangement_id", "9");
    task.put("equipment_id", 101L);
    task.put("business_date", "2026-11-09");
    task.put("due_at", "2026-11-09T08:00:00");
    task.put("shift_id", "");
    task.put("follow_up_user_id", 20L);
    task.put("result", "{\"startedAt\":\"\"}");
    task.put("snapshot", "{\"owners\":[10],\"personMode\":\"指定负责人\"}");
    return task;
  }

  private Map<String, Object> draft() {
    var draft = new HashMap<String, Object>();
    draft.put("arrangements", List.of(arrangement));
    return draft;
  }
}
