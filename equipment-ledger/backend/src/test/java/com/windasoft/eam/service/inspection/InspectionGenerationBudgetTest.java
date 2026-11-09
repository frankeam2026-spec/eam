package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 预算只限制本批新增任务；日常日期和周期索引各自保留独立的续跑语义。 */
class InspectionGenerationBudgetTest {
  private static final long TENANT = 31L;
  private static final long PLAN = 42L;
  private final LocalDate day = now().toLocalDate().minusDays(2);
  private final InspectionRepository repository = mock(InspectionRepository.class);
  private final DailyArrangementRepository storage = mock(DailyArrangementRepository.class);
  private final DailyArrangementService arrangements = mock(DailyArrangementService.class);
  private final InspectionTaskCreationService creation = mock(InspectionTaskCreationService.class);
  private final InspectionProjectService projects = mock(InspectionProjectService.class);
  private final InspectionViewAssembler views = mock(InspectionViewAssembler.class);
  private final InspectionGenerationService generation =
      new InspectionGenerationService(
          repository,
          new ObjectMapper(),
          projects,
          views,
          mock(InspectionTaskLifecycleService.class),
          creation,
          arrangements,
          storage);
  private final Set<String> created = new HashSet<>();
  private LocalDateTime next = day.atStartOfDay();
  private LocalDateTime anchor = next;
  private long index;

  private void configure(String kind, Map<String, Object> data) throws Exception {
    when(repository.lockPlan(TENANT, PLAN))
        .thenReturn(
            List.of(
                Map.of(
                    "id",
                    PLAN,
                    "name",
                    "预算回归",
                    "kind",
                    kind,
                    "payload",
                    new ObjectMapper().writeValueAsString(data))));
    when(repository.lockSchedule(TENANT, PLAN))
        .thenAnswer(call -> Map.of("next_at", next, "anchor_at", anchor, "cycle_index", index));
    when(repository.advanceSchedule(any(), any(), eq(TENANT), eq(PLAN)))
        .thenAnswer(
            call -> {
              next = call.getArgument(0);
              index = ((Number) call.getArgument(1)).longValue();
              return 1;
            });
    doAnswer(
            call -> {
              List<Map<String, Object>> details = call.getArgument(3);
              return created.add(
                      call.getArgument(4)
                          + "/"
                          + call.getArgument(5)
                          + "/"
                          + details.getFirst().get("equipmentId"))
                  ? 1
                  : 0;
            })
        .when(creation)
        .createTask(
            anyLong(),
            anyMap(),
            anyMap(),
            anyList(),
            any(),
            anyString(),
            anyString(),
            any(),
            any());
  }

  private static List<Map<String, Object>> details(int count) {
    var rows = new ArrayList<Map<String, Object>>();
    for (int i = 1; i <= count; i++) rows.add(Map.of("equipmentId", i));
    return rows;
  }

  private Map<String, Object> daily(boolean arranged, int equipmentCount, int shiftCount)
      throws Exception {
    var data = new LinkedHashMap<String, Object>();
    data.put("enabled", true);
    data.put("startDate", day.toString());
    data.put("endDate", day.toString());
    data.put("details", details(equipmentCount));
    var shifts = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < shiftCount; i++)
      shifts.add(Map.of("id", Integer.toString(i), "name", "班次", "start", "08:00", "end", "16:00"));
    if (arranged) {
      var group =
          Map.<String, Object>of(
              "id", "1", "frequency", "shift", "personMode", "specified", "slots", shifts);
      when(storage.load(TENANT, PLAN)).thenReturn(List.of(group));
      when(arrangements.details(anyMap(), anyMap())).thenReturn(details(equipmentCount));
    } else data.put("shifts", shifts);
    configure("daily-plans", data);
    return data;
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void partialDayResumesAcrossBudgetWithoutDuplicates(boolean arranged) throws Exception {
    daily(arranged, 499, 5);
    assertEquals(2000, generation.generate(TENANT, PLAN));
    assertEquals(day.atStartOfDay(), next, "尚有设备未处理，必须保留当天");
    assertEquals(495, generation.generate(TENANT, PLAN));
    assertEquals(2495, created.size());
    assertEquals(day.plusDays(1).atStartOfDay(), next);
    assertEquals(0, generation.generate(TENANT, PLAN));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void exactBudgetFinishesDayAndContinuesNextDayOnRetry(boolean arranged) throws Exception {
    var data = daily(arranged, 500, 4);
    data.put("endDate", day.plusDays(1).toString());
    configure("daily-plans", data);
    assertEquals(2000, generation.generate(TENANT, PLAN));
    assertEquals(day.plusDays(1).atStartOfDay(), next, "恰好完成当天，不应把当天反复扫描");
    assertEquals(2000, generation.generate(TENANT, PLAN));
    assertEquals(day.plusDays(2).atStartOfDay(), next);
    assertEquals(4000, created.size());
  }

  @Test
  void periodicHundredCycleBoundaryRetainsIndependentCursor() throws Exception {
    anchor = day.minusDays(249).atStartOfDay();
    next = anchor;
    var data = new LinkedHashMap<String, Object>();
    data.put("enabled", true);
    data.put("typeId", "77");
    data.put("period", 1);
    data.put("unit", "日");
    data.put("startDate", anchor.toLocalDate().toString());
    data.put("endDate", day.toString());
    data.put("details", details(1));
    configure("periodic-plans", data);
    when(projects.raw("types", TENANT, 77L, true)).thenReturn(Map.of());
    when(views.definition(any())).thenReturn(Map.of("enabled", true));
    assertEquals(100, generation.generate(TENANT, PLAN));
    assertEquals(100, generation.generate(TENANT, PLAN));
    assertEquals(50, generation.generate(TENANT, PLAN));
    assertEquals(250, index);
    assertEquals(250, created.size());
    assertTrue(next.isAfter(day.atTime(23, 59, 59)));
  }
}
