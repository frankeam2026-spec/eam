package com.windasoft.eam.inspection;

import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InspectionScheduler {
  private final JdbcTemplate db;
  private final InspectionService service;

  public InspectionScheduler(JdbcTemplate db, InspectionService service) {
    this.db = db;
    this.service = service;
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 10000)
  public void generate() {
    for (var p :
        db.queryForList(
            "SELECT tenant_id,id FROM eam_inspection_definition WHERE kind IN"
                + " ('daily-plans','periodic-plans') ORDER BY tenant_id,id")) {
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
