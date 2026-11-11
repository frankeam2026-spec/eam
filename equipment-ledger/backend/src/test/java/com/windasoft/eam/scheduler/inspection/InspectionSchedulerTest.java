package com.windasoft.eam.scheduler.inspection;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.windasoft.eam.dto.inspection.InspectionGenerationTarget;
import com.windasoft.eam.service.inspection.InspectionGenerationService;
import com.windasoft.eam.service.inspection.InspectionTaskLifecycleService;
import java.util.List;
import org.junit.jupiter.api.Test;

class InspectionSchedulerTest {
  private final InspectionGenerationService generation = mock(InspectionGenerationService.class);
  private final InspectionTaskLifecycleService lifecycle =
      mock(InspectionTaskLifecycleService.class);
  private final InspectionScheduler scheduler = new InspectionScheduler(generation, lifecycle);

  @Test
  void generationFailureFallsBackBeforeProceedingToNextPlan() {
    when(generation.generationTargets())
        .thenReturn(
            List.of(
                new InspectionGenerationTarget(7L, 11L), new InspectionGenerationTarget(8L, 12L)));
    when(generation.generate(7L, 11L)).thenThrow(new IllegalStateException("创建失败"));
    scheduler.generate();
    var order = inOrder(generation, lifecycle);
    order.verify(generation).generationTargets();
    order.verify(generation).generate(7L, 11L);
    order.verify(lifecycle).maintainAfterGenerationFailure(7L, 11L);
    order.verify(generation).generate(8L, 12L);
  }

  @Test
  void fallbackFailureDoesNotBlockAnotherTenantsPlan() {
    when(generation.generationTargets())
        .thenReturn(
            List.of(
                new InspectionGenerationTarget(7L, 11L), new InspectionGenerationTarget(8L, 12L)));
    when(generation.generate(7L, 11L)).thenThrow(new IllegalStateException("创建失败"));
    doThrow(new IllegalStateException("维护失败"))
        .when(lifecycle)
        .maintainAfterGenerationFailure(7L, 11L);
    scheduler.generate();
    inOrder(generation).verify(generation).generate(8L, 12L);
  }

  @Test
  void successfulGenerationDoesNotDuplicateMaintenance() {
    when(generation.generationTargets())
        .thenReturn(List.of(new InspectionGenerationTarget(7L, 11L)));
    scheduler.generate();
    verifyNoInteractions(lifecycle);
  }
}
