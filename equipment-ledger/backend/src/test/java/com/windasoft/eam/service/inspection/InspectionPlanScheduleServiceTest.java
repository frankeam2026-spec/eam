package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspectionPlanScheduleServiceTest {
  private InspectionRepository repository;
  private InspectionGenerationService generation;
  private InspectionPlanScheduleService service;

  @BeforeEach
  void setUp() {
    repository = mock(InspectionRepository.class);
    generation = mock(InspectionGenerationService.class);
    service = new InspectionPlanScheduleService(repository, generation, new ObjectMapper());
  }

  @Test
  void dailyPlanCreatesCursorRefreshesExistingAssignmentsAndGeneratesTasks() {
    when(repository.lockPlanSchedules(11L, 22L)).thenReturn(List.of());

    assertDoesNotThrow(
        () ->
            service.maintain(
                "daily-plans",
                11L,
                22L,
                Map.of("startDate", "2026-11-09"),
                Map.of("id", "22"),
                33L));

    verify(repository).setPlanType(11L, 22L, null);
    verify(repository).insertSchedule(anyLong(), org.mockito.ArgumentMatchers.eq(11L),
        org.mockito.ArgumentMatchers.eq(22L), any(), any());
    verify(generation).refreshAssignments(11L, 22L, 33L);
    verify(generation).generate(11L, 22L);
  }

  @Test
  void nonPlanDefinitionDoesNotTouchScheduleOrGeneration() {
    service.maintain("standards", 11L, 22L, Map.of(), null, 33L);

    verify(repository, never()).setPlanType(anyLong(), anyLong(), any());
    verify(repository, never()).lockPlanSchedules(anyLong(), anyLong());
    verify(generation, never()).generate(anyLong(), anyLong());
  }
}
