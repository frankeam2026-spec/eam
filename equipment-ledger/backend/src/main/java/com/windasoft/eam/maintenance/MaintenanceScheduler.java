package com.windasoft.eam.maintenance;

import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class MaintenanceScheduler {
  private final JdbcTemplate db;
  private final MaintenanceService service;

  public MaintenanceScheduler(JdbcTemplate db, MaintenanceService service) {
    this.db = db;
    this.service = service;
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 10000)
  public void generate() {
    for (var plan :
        db.queryForList("SELECT tenant_id,id FROM eam_maintenance_definition WHERE kind='plans'")) {
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
