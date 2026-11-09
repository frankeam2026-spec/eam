package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.repository.system.NotificationRepository;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

/** A disabled cycle type stops future generation, not the lifecycle of tasks already created. */
class InspectionGenerationServiceTest {
  private static final long TENANT = 31L;
  private static final long PLAN_ID = 42L;
  private static final long TYPE_ID = 77L;
  private static final long TASK_ID = 88L;

  private final InspectionRepository repository = mock(InspectionRepository.class);
  private final PermissionService permissions = mock(PermissionService.class);
  private final InspectionProjectService projectSource = mock(InspectionProjectService.class);
  private final InspectionNumbers numbers = mock(InspectionNumbers.class);
  private final InspectionViewAssembler views = mock(InspectionViewAssembler.class);
  private final InspectionTaskJournal journal = mock(InspectionTaskJournal.class);
  private final NotificationRepository notificationPolicies = mock(NotificationRepository.class);
  private final DailyArrangementRepository dailyStorage = mock(DailyArrangementRepository.class);
  private final InspectionGenerationService service =
      new InspectionGenerationService(
          repository,
          new ObjectMapper(),
          projectSource,
          views,
          new InspectionTaskLifecycleService(repository, notificationPolicies, journal, new ObjectMapper()),
          mock(DailyArrangementService.class),
          dailyStorage,
          new InspectionTaskCreationService(
              repository,
              new ObjectMapper(),
              permissions,
              projectSource,
              journal,
              numbers,
              mock(DailyArrangementService.class),
              dailyStorage));

  @Test
  void reusesEquipmentAndProjectLookupsAcrossCyclesInOneGenerationRun() {
    var today = now().toLocalDate();
    LocalDate firstDate = today.minusDays(2);
    LocalDate secondDate = firstDate.plusDays(1);
    LocalDateTime firstDue = firstDate.atTime(8, 0);

    var plan = new HashMap<String, Object>();
    plan.put("id", PLAN_ID);
    plan.put("name", "周期巡检计划");
    plan.put("code", "IP-0001");
    plan.put("department_id", 21L);
    plan.put("kind", "periodic-plans");
    plan.put(
        "payload",
        "{"
            + "\"enabled\":true,"
            + "\"typeId\":\"77\","
            + "\"startDate\":\"" + firstDate + "\","
            + "\"endDate\":\"" + secondDate + "\","
            + "\"period\":1,"
            + "\"unit\":\"日\","
            + "\"validHours\":1,"
            + "\"validUnit\":\"时\","
            + "\"advanceHours\":0,"
            + "\"advanceUnit\":\"时\","
            + "\"remindHours\":0,"
            + "\"remindUnit\":\"时\","
            + "\"owners\":[\"10\"],"
            + "\"reviewers\":[],"
            + "\"randomPhotoCount\":0,"
            + "\"details\":[{"
            + "\"equipmentId\":\"101\","
            + "\"projectRefs\":[{\"standardId\":\"1\",\"itemId\":\"2\"}]"
            + "}]"
            + "}");
    when(repository.lockPlan(TENANT, PLAN_ID)).thenReturn(List.of(plan));
    when(repository.lockSchedule(TENANT, PLAN_ID))
        .thenReturn(
            Map.of(
                "next_at", firstDue.toString(),
                "anchor_at", firstDue.toString(),
                "cycle_index", 0));
    when(projectSource.raw("types", TENANT, TYPE_ID, true))
        .thenReturn(Map.of("id", TYPE_ID, "name", "周期类型"));
    when(views.definition(any())).thenReturn(Map.of("enabled", true, "name", "周期类型"));
    when(
            projectSource.equipment(TENANT, 101L))
        .thenReturn(
            Map.of(
                "id", 101L,
                "equipment_code", "EQ-101",
                "name", "数控车床",
                "model", "CNC",
                "department", "加工部",
                "function_location", "车间A",
                "company_org_id", 1L,
                "department_org_id", 21L));
    when(projectSource.projects(anyLong(), any(), eq(false)))
        .thenReturn(
            List.of(
                Map.of(
                    "id", "1:2",
                    "name", "温度",
                    "content", "主轴温度",
                    "valueType", "数值",
                    "required", true)));
    when(repository.findOrganizationName(TENANT, 21L)).thenReturn("加工部");
    when(permissions.companyOfOrganization(TENANT, 21L)).thenReturn(1L);
    when(numbers.nextCode(TENANT, "XJ")).thenReturn("XJ-0001");
    when(repository.insertTask(
            anyLong(),
            eq(TENANT),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyString()))
        .thenReturn(1);
    when(repository.lockOpenPlanTasks(TENANT, PLAN_ID)).thenReturn(List.of());

    assertEquals(2, service.generate(TENANT, PLAN_ID));

    verify(projectSource, times(1)).equipment(TENANT, 101L);
    verify(projectSource, times(1)).projects(anyLong(), any(), eq(false));
    verify(projectSource, times(1)).raw("types", TENANT, TYPE_ID, true);
    verify(repository, times(1)).findOrganizationName(TENANT, 21L);
    verify(permissions, times(1)).companyOfOrganization(TENANT, 21L);
    verify(repository, never()).findOrganizationParent(TENANT, 21L);
    verify(repository, times(2))
        .insertTask(
            anyLong(),
            eq(TENANT),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyString());
  }

  @Test
  void disabledCycleTypeStopsNewGenerationButStillExpiresExistingTasks() {
    var plan = new HashMap<String, Object>();
    plan.put("kind", "periodic-plans");
    plan.put("payload", "{\"enabled\":true,\"typeId\":\"77\"}");
    when(repository.lockPlan(TENANT, PLAN_ID)).thenReturn(List.of(plan));
    when(projectSource.raw("types", TENANT, TYPE_ID, true)).thenReturn(Map.of("id", TYPE_ID));
    when(views.definition(any())).thenReturn(Map.of("enabled", false));

    var now = now();
    var task = new HashMap<String, Object>();
    task.put("id", TASK_ID);
    task.put("kind", "periodic");
    task.put("status", InspectionTaskStatus.PENDING.code());
    task.put("snapshot", "{\"allowLate\":false,\"owners\":[]}");
    task.put("due_at", now.minusHours(2).toString());
    task.put("expires_at", now.minusMinutes(1).toString());
    when(repository.lockOpenPlanTasks(TENANT, PLAN_ID)).thenReturn(List.of(task));

    assertEquals(0, service.generate(TENANT, PLAN_ID));

    verify(repository, never()).lockSchedule(TENANT, PLAN_ID);
    verify(repository)
        .changeTaskStatus(InspectionTaskStatus.EXPIRED_CANCELLED.code(), TENANT, TASK_ID);
    verify(journal).closeTodos(TENANT, TASK_ID);
  }
 
  @Test
  void expiredDailyTaskWithoutLateExecutionClosesTodoAndSkipsExecutionReminders() {
    stubDisabledDailyPlan();
    var task = expiredDailyTask(false);
    when(repository.lockOpenPlanTasks(TENANT, PLAN_ID)).thenReturn(List.of(task));

    assertEquals(0, service.generate(TENANT, PLAN_ID));

    verify(repository).changeTaskStatus(InspectionTaskStatus.EXPIRED.code(), TENANT, TASK_ID);
    verify(journal).closeTodos(TENANT, TASK_ID);
    verify(journal)
        .event(
            eq(TENANT),
            eq(TASK_ID),
            eq(InspectionTaskStatus.EXPIRED.code()),
            isNull(),
            eq("系统"),
            eq(
                Map.of(
                    "原状态", InspectionTaskStatus.PENDING.code(),
                    "新状态", InspectionTaskStatus.EXPIRED.code())));
    verify(journal, never())
        .notify(
            eq(TENANT), eq(TASK_ID), anyList(), eq("点巡检待执行"), anyString(), eq(true), eq(false));
    verify(journal, never())
        .notify(
            eq(TENANT), eq(TASK_ID), anyList(), eq("点巡检即将过期"), anyString(), eq(false), eq(true));
  }

  @Test
  void expiredDailyTaskWithLateExecutionKeepsTodoButDoesNotSendExpiryWarning() {
    stubDisabledDailyPlan();
    var task = expiredDailyTask(true);
    when(repository.lockOpenPlanTasks(TENANT, PLAN_ID)).thenReturn(List.of(task));

    assertEquals(0, service.generate(TENANT, PLAN_ID));

    verify(repository).changeTaskStatus(InspectionTaskStatus.EXPIRED.code(), TENANT, TASK_ID);
    verify(journal, never()).closeTodos(TENANT, TASK_ID);
    verify(journal)
        .notify(
            eq(TENANT),
            eq(TASK_ID),
            eq(List.of(10L)),
            eq("点巡检待执行"),
            anyString(),
            eq(true),
            eq(false));
    verify(journal, never())
        .notify(
            eq(TENANT), eq(TASK_ID), anyList(), eq("点巡检即将过期"), anyString(), eq(false), eq(true));
  }

  private void stubDisabledDailyPlan() {
    var plan = new HashMap<String, Object>();
    plan.put("kind", "daily-plans");
    plan.put("payload", "{\"enabled\":false}");
    when(repository.lockPlan(TENANT, PLAN_ID)).thenReturn(List.of(plan));
    when(dailyStorage.load(TENANT, PLAN_ID)).thenReturn(List.of());
    when(dailyStorage.workdays(TENANT, PLAN_ID)).thenReturn(List.of());
    when(dailyStorage.exemptions(TENANT, PLAN_ID)).thenReturn(List.of());
    when(dailyStorage.activePeople(TENANT)).thenReturn(List.of());
  }

  private Map<String, Object> expiredDailyTask(boolean allowLate) {
    var now = now();
    var task = new HashMap<String, Object>();
    task.put("id", TASK_ID);
    task.put("kind", "daily");
    task.put("arrangement_id", 123L);
    task.put("status", InspectionTaskStatus.PENDING.code());
    task.put(
        "snapshot",
        "{\"allowLate\":" + allowLate
            + ",\"owners\":[\"10\"],"
            + "\"expireRemindHours\":2,\"expireRemindUnit\":\"时\"}");
    task.put("due_at", now.minusHours(3).toString());
    task.put("expires_at", now.minusMinutes(1).toString());
    return task;
  }

  @Test
  void resolvesNotificationPolicyOnceForAllTasksInOnePlanMaintenanceRun() {
    var plan = new HashMap<String, Object>();
    plan.put("kind", "periodic-plans");
    plan.put("payload", "{\"enabled\":false}");
    when(repository.lockPlan(TENANT, PLAN_ID)).thenReturn(List.of(plan));

    var now = now();
    var first = openPeriodicTask(TASK_ID, now);
    var second = openPeriodicTask(TASK_ID + 1, now);
    when(repository.lockOpenPlanTasks(TENANT, PLAN_ID)).thenReturn(List.of(first, second));

    assertEquals(0, service.generate(TENANT, PLAN_ID));

    verify(notificationPolicies, times(1)).replacesLegacy(TENANT, "periodic", PLAN_ID);
  }

  private Map<String, Object> openPeriodicTask(long id, LocalDateTime now) {
    var task = new HashMap<String, Object>();
    task.put("id", id);
    task.put("kind", "periodic");
    task.put("status", InspectionTaskStatus.PENDING.code());
    task.put("snapshot", "{\"allowLate\":false,\"owners\":[]}");
    task.put("due_at", now.minusMinutes(5).toString());
    task.put("expires_at", now.plusHours(1).toString());
    return task;
  }
}
