package com.windasoft.eam.scheduler.inspection;

import com.windasoft.eam.service.inspection.InspectionService;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InspectionScheduler {
  private final InspectionService service;

  public InspectionScheduler(InspectionService service) {
    this.service = service;
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 10000)
  public void generate() {
    for (var p : service.generationTargets()) {
      long tenant = InspectionService.number(p.get("tenant_id")),
          id = InspectionService.number(p.get("id"));
      try {
        service.generate(tenant, id);
      } catch (Exception e) {
        LoggerFactory.getLogger(getClass()).error("点巡检任务生成失败 tenant={} plan={}", tenant, id, e);
      }
    }
  }
}
