package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 预览可截断展示行，但不能截断总数或越过计算上限，更不能调用正式任务创建。 */
class InspectionPreviewCalculationTest {
  private final InspectionRepository repository = mock(InspectionRepository.class);
  private final InspectionTaskCreationService creation = mock(InspectionTaskCreationService.class);
  private final InspectionTaskLifecycleService lifecycle =
      mock(InspectionTaskLifecycleService.class);
  private final DailyArrangementRepository storage = mock(DailyArrangementRepository.class);
  private final DailyArrangementService arrangements =
      new DailyArrangementService(
          storage,
          mock(PermissionService.class),
          mock(AuthContext.class),
          mock(InspectionValidationService.class));
  private final InspectionGenerationService generation =
      new InspectionGenerationService(
          repository,
          new ObjectMapper(),
          mock(InspectionProjectService.class),
          mock(InspectionViewAssembler.class),
          lifecycle,
          creation,
          arrangements,
          storage);

  private Map<String, Object> plan(int devices, int shifts, int days) {
    LocalDate start = LocalDate.now().plusDays(1);
    var details = new ArrayList<Map<String, Object>>();
    for (int i = 1; i <= devices; i++) {
      details.add(
          Map.of(
              "equipmentId",
              Integer.toString(i),
              "projectRefs",
              List.of(Map.of("standardId", "1", "itemId", "1"))));
    }
    var slots = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < shifts; i++) {
      slots.add(
          Map.of(
              "id",
              Integer.toString(i),
              "name",
              "班次" + i,
              "start",
              LocalTime.of(i, 0).toString(),
              "end",
              LocalTime.of((i + 1) % 24, 0).toString()));
    }
    return new LinkedHashMap<>(
        Map.of(
            "startDate",
            start.toString(),
            "endDate",
            start.plusDays(days - 1).toString(),
            "details",
            details,
            "arrangements",
            List.of(
                Map.of(
                    "id",
                    "1",
                    "name",
                    "开放点检",
                    "frequency",
                    "shift",
                    "personMode",
                    "open",
                    "slots",
                    slots))));
  }

  @Test
  void displayLimitDoesNotTruncateTotalOrTreatOpenInspectionAsUnstaffed() {
    var result = generation.preview(7L, plan(250, 3, 1), 7);
    assertEquals(750L, result.get("total"));
    assertEquals(500, ((List<?>) result.get("rows")).size());
    assertEquals(0L, result.get("unstaffed"));
    verifyNoInteractions(repository, creation, lifecycle);
  }

  @Test
  void calculationLimitRejectsInsteadOfReturningAPartialTotal() {
    assertThrows(
        IllegalArgumentException.class, () -> generation.preview(7L, plan(500, 24, 30), 30));
    verifyNoInteractions(repository, creation, lifecycle);
  }
}
