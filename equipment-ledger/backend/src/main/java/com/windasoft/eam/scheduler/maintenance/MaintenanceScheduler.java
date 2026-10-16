package com.windasoft.eam.scheduler.maintenance;

import com.windasoft.eam.service.maintenance.MaintenanceService;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class MaintenanceScheduler {
  private final MaintenanceService service;

  public MaintenanceScheduler(MaintenanceService service) {
    this.service = service;
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 10000)
  public void generate() {
    for (var plan : service.generationTargets()) {
      long tenant = MaintenanceService.number(plan.get("tenant_id")),
          id = MaintenanceService.number(plan.get("id"));
      try {
        service.generate(tenant, id);
      } catch (Exception e) {
        LoggerFactory.getLogger(getClass()).error("保养计划生成失败 tenant={} plan={}", tenant, id, e);
      }
    }
  }
}
