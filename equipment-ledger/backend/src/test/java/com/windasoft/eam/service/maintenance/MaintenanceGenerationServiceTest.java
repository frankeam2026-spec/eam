package com.windasoft.eam.service.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.maintenance.MaintenanceType;
import com.windasoft.eam.repository.common.TaskSourceRelationsRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceExecutionStorage;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTypeRepository;
import com.windasoft.eam.service.system.AuthContext;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MaintenanceGenerationServiceTest {
  @Test
  void dueDateMinusAdvanceControlsGenerationAndCursorMakesTheNextRunIdempotent() {
    var repo = mock(MaintenanceRepository.class);
    var types = mock(MaintenanceTypeRepository.class);
    var projects = mock(MaintenanceProjectService.class);
    var views = mock(MaintenanceViewAssembler.class);
    var journal = mock(MaintenanceTaskJournal.class);
    var json = new ObjectMapper();
    var service =
        new MaintenanceGenerationService(
            repo,
            types,
            mock(MaintenanceExecutionStorage.class),
            mock(TaskSourceRelationsRepository.class),
            json,
            mock(AuthContext.class),
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
    when(repo.lockPlans(10001L, 10L)).thenReturn(List.of(plan));
    when(repo.lockPlanSchedules(10001L, 10L)).thenReturn(List.of(schedule));
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
    when(repo.insertTask(
            anyLong(),
            eq(10001L),
            anyString(),
            eq(10L),
            eq(20L),
            eq(30L),
            eq(40L),
            eq(71002L),
            eq(71003L),
            eq(due),
            eq(due.plusDays(7)),
            anyString()))
        .thenReturn(1);
    assertEquals(1, service.generate(10001L, 10L));
    verify(repo).advanceSchedule(due.plusWeeks(1), 1L, 10001L, 20L);
    verify(journal)
        .notify(eq(10001L), anyLong(), eq(List.of(73001L)), eq("保养待执行"), eq("工程设备"), eq(true));
    schedule.put("next_date", java.sql.Date.valueOf(due.plusWeeks(1)));
    schedule.put("cycle_index", 1);
    assertEquals(0, service.generate(10001L, 10L));
    verify(repo, times(1))
        .insertTask(
            anyLong(),
            anyLong(),
            anyString(),
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
}
