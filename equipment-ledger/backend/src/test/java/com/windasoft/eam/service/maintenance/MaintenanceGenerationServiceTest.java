package com.windasoft.eam.service.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.maintenance.MaintenancePlan;
import com.windasoft.eam.entity.maintenance.MaintenanceTask;
import com.windasoft.eam.entity.maintenance.MaintenanceType;
import com.windasoft.eam.repository.common.TaskSourceRelationsRepository;
import com.windasoft.eam.repository.maintenance.MaintenancePlanDeviceRepository;
import com.windasoft.eam.repository.maintenance.MaintenancePlanRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTaskRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTypeRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class MaintenanceGenerationServiceTest {
  @Test
  void dueDateMinusAdvanceControlsGenerationAndCursorMakesTheNextRunIdempotent() {
    var repo = mock(MaintenanceRepository.class);
    var tasks = mock(MaintenanceTaskRepository.class);
    var types = mock(MaintenanceTypeRepository.class);
    var schedules = mock(MaintenancePlanDeviceRepository.class);
    var planDefinitions = mock(MaintenancePlanRepository.class);
    var projects = mock(MaintenanceProjectService.class);
    var views = mock(MaintenanceViewAssembler.class);
    var journal = mock(MaintenanceTaskJournal.class);
    var json = new ObjectMapper();
    var service =
        new MaintenanceGenerationService(
            repo,
            tasks,
            schedules,
            planDefinitions,
            types,
            mock(MaintenanceExecutionService.class),
            mock(TaskSourceRelationsRepository.class),
            json,
            mock(AuthContext.class),
            mock(PermissionService.class),
            projects,
            views,
            journal);
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    var plan =
        Map.<String, Object>of(
            "id",
            10L,
            "name",
            "工程计划",
            "payload",
            "{\"enabled\":true,\"advanceDays\":1,\"owners\":[\"73001\"],\"reviewers\":[\"73001\"],\"remindDays\":1}");
    var due = today.plusDays(1);
    var schedule =
        new HashMap<String, Object>(
            Map.of(
                "id",
                20L,
                "equipment_id",
                30L,
                "type_id",
                40L,
                "next_date",
                java.sql.Date.valueOf(due),
                "anchor_date",
                java.sql.Date.valueOf(due),
                "cycle_index",
                0,
                "payload",
                "{\"period\":1,\"unit\":\"周\",\"validDays\":7}"));
    when(repo.planRowsForGeneration(10001L, 10L)).thenReturn(List.of(plan));
    when(repo.scheduleRowsForGeneration(10001L, 10L)).thenReturn(List.of(schedule));
    var type = new MaintenanceType();
    type.setId(40L);
    type.setName("周");
    type.setEnabled(true);
    when(types.find(10001L, 40L, false)).thenReturn(type);
    when(views.historicalSnapshot(anyMap())).thenAnswer(call -> call.getArgument(0));
    var device = new Equipment();
    device.setId(30L);
    device.setEquipmentCode("QA-EQ");
    device.setName("工程设备");
    device.setCompanyOrgId(71002L);
    device.setDepartmentOrgId(71003L);
    when(projects.equipment(10001L, 30L)).thenReturn(device);
    when(projects.planProjects(anyMap(), eq(10001L), any(Equipment.class), eq(false)))
        .thenReturn(List.of(Map.of("id", "item-1", "name", "检查", "sourceStandardName", "标准")));
    when(repo.currentNumber()).thenReturn(1L);
    when(tasks.insertTask(any(MaintenanceTask.class))).thenReturn(1);
    assertEquals(1, service.generate(10001L, 10L));
    var locks = inOrder(planDefinitions, repo, schedules);
    locks.verify(planDefinitions).lockForGeneration(10001L, 10L);
    locks.verify(repo).planRowsForGeneration(10001L, 10L);
    locks.verify(schedules).lockForGeneration(10001L, 10L);
    locks.verify(repo).scheduleRowsForGeneration(10001L, 10L);
    verify(schedules).advance(10001L, 20L, due.plusWeeks(1), 1);
    verify(journal)
        .notify(eq(10001L), anyLong(), eq(List.of(73001L)), eq("保养待执行"), eq("工程设备"), eq(true));
    schedule.put("next_date", java.sql.Date.valueOf(due.plusWeeks(1)));
    schedule.put("cycle_index", 1);
    assertEquals(0, service.generate(10001L, 10L));
    var task = ArgumentCaptor.forClass(MaintenanceTask.class);
    verify(tasks, times(1)).insertTask(task.capture());
    assertEquals(10001L, task.getValue().getTenantId());
    assertEquals(10L, task.getValue().getPlanId());
    assertEquals(20L, task.getValue().getScheduleId());
    assertEquals(30L, task.getValue().getEquipmentId());
    assertEquals(40L, task.getValue().getTypeId());
    assertEquals(71002L, task.getValue().getCompanyId());
    assertEquals(71003L, task.getValue().getDepartmentId());
    assertEquals(due, task.getValue().getDueDate());
    assertEquals(due.plusDays(7), task.getValue().getExpiresDate());
  }

  @Test
  void manualGenerationChecksOriginalPlanEditScopeBeforeLockingOrGenerating() {
    var repo = mock(MaintenanceRepository.class);
    var tasks = mock(MaintenanceTaskRepository.class);
    var schedules = mock(MaintenancePlanDeviceRepository.class);
    var plans = mock(MaintenancePlanRepository.class);
    var auth = mock(AuthContext.class);
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(70001L);
    var permissions = mock(PermissionService.class);
    var plan = new MaintenancePlan();
    plan.setId(10L);
    plan.setDepartmentId(71003L);
    plan.setCreatorId(70002L);
    when(plans.find(10001L, 10L, false)).thenReturn(plan);
    when(permissions.maintenance("plans", "编辑"))
        .thenReturn(new PermissionScope(false, null, Set.of(71004L), false));
    var service =
        new MaintenanceGenerationService(
            repo,
            tasks,
            schedules,
            plans,
            mock(MaintenanceTypeRepository.class),
            mock(MaintenanceExecutionService.class),
            mock(TaskSourceRelationsRepository.class),
            new ObjectMapper(),
            auth,
            permissions,
            mock(MaintenanceProjectService.class),
            mock(MaintenanceViewAssembler.class),
            mock(MaintenanceTaskJournal.class));
    assertEquals(
        403,
        assertThrows(ResponseStatusException.class, () -> service.generatePlan(10L))
            .getStatusCode()
            .value());
    verify(plans, never()).lockForGeneration(anyLong(), any());
    verifyNoInteractions(repo, schedules);
    when(permissions.maintenance("plans", "编辑"))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    when(repo.planRowsForGeneration(10001L, 10L)).thenReturn(List.of());
    assertEquals(0, service.generatePlan(10L));
    verify(plans).lockForGeneration(10001L, 10L);
  }
}
