package com.windasoft.eam.scheduler.inspection;

import com.windasoft.eam.service.inspection.InspectionGenerationService;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InspectionScheduler {
  private final InspectionGenerationService generation;

  public InspectionScheduler(InspectionGenerationService generation) {
    this.generation = generation;
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 10000)
  public void generate() {
    for (var p : generation.generationTargets()) {
      long
          tenant = com.windasoft.eam.common.inspection.InspectionSupport.number(p.get("tenant_id")),
          id = com.windasoft.eam.common.inspection.InspectionSupport.number(p.get("id"));
      try {
        generation.generate(tenant, id);
      } catch (Exception e) {
        LoggerFactory.getLogger(getClass()).error("点巡检任务生成失败 tenant={} plan={}", tenant, id, e);
      }
    }
  }
}
