package com.windasoft.eam.service.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.service.system.AuthContext;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MaintenanceGenerationServiceTest {
  @Test
  void dueDateMinusAdvanceControlsGenerationAndCursorMakesTheNextRunIdempotent() {
    var repo = mock(MaintenanceRepository.class);
    var projects = mock(MaintenanceProjectService.class);
    var views = mock(MaintenanceViewAssembler.class);
    var journal = mock(MaintenanceTaskJournal.class);
    var json = new ObjectMapper();
    var service =
        new MaintenanceGenerationService(
            repo, json, mock(AuthContext.class), projects, views, journal);
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
    when(projects.raw(eq("types"), eq(10001L), eq(40L), eq(false)))
        .thenReturn(Map.of("payload", "{\"enabled\":true}"));
    when(views.definition(anyMap())).thenReturn(Map.of("id", "40", "name", "周"));
    when(projects.equipment(10001L, 30L))
        .thenReturn(
            Map.of(
                "id",
                30L,
                "equipment_code",
                "QA-EQ",
                "name",
                "工程设备",
                "company_org_id",
                71002L,
                "department_org_id",
                71003L));
    when(projects.planProjects(anyMap(), eq(10001L), anyMap(), eq(false)))
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
