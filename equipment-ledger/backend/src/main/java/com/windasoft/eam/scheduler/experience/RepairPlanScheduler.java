package com.windasoft.eam.scheduler.experience;

import com.windasoft.eam.service.experience.RepairPlanGenerationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RepairPlanScheduler {
  private final RepairPlanGenerationService service;

  public RepairPlanScheduler(RepairPlanGenerationService service) {
    this.service = service;
  }

  @Scheduled(fixedDelay = 60000)
  public void scan() {
    service.scan();
  }
}
