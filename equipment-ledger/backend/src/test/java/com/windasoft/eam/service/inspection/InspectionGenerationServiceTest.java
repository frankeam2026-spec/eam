package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.repository.system.NotificationRepository;
import com.windasoft.eam.service.system.PermissionService;
import java.util.*;
import org.junit.jupiter.api.Test;

/** A disabled cycle type stops future generation, not the lifecycle of tasks already created. */
class InspectionGenerationServiceTest {
  private static final long TENANT = 31L;
  private static final long PLAN_ID = 42L;
  private static final long TYPE_ID = 77L;
  private static final long TASK_ID = 88L;

  private final InspectionRepository repository = mock(InspectionRepository.class);
  private final InspectionProjectService projectSource = mock(InspectionProjectService.class);
  private final InspectionViewAssembler views = mock(InspectionViewAssembler.class);
  private final InspectionTaskJournal journal = mock(InspectionTaskJournal.class);
  private final NotificationRepository notificationPolicies = mock(NotificationRepository.class);
  private final InspectionGenerationService service =
      new InspectionGenerationService(
          repository,
          new ObjectMapper(),
          mock(PermissionService.class),
          projectSource,
          views,
          journal,
          mock(InspectionNumbers.class),
          notificationPolicies,
          mock(DailyArrangementService.class),
          mock(DailyArrangementRepository.class));

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
}
