package com.windasoft.eam.scheduler.inspection;

import com.windasoft.eam.service.inspection.InspectionGenerationService;
import com.windasoft.eam.service.inspection.InspectionTaskLifecycleService;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InspectionScheduler {
  private final InspectionGenerationService generation;
  private final InspectionTaskLifecycleService lifecycle;

  public InspectionScheduler(
      InspectionGenerationService generation, InspectionTaskLifecycleService lifecycle) {
    this.generation = generation;
    this.lifecycle = lifecycle;
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 10000)
  public void generate() {
    for (var p : generation.generationTargets()) {
      long tenant = p.tenantId(), id = p.planId();
      try {
        generation.generate(tenant, id);
      } catch (Exception e) {
        LoggerFactory.getLogger(getClass()).error("点巡检任务生成失败 tenant={} plan={}", tenant, id, e);
        // 此处已退出生成事务代理，回退维护才能独立提交；正常生成仍只维护一次。
        try {
          lifecycle.maintainAfterGenerationFailure(tenant, id);
        } catch (Exception maintenanceFailure) {
          LoggerFactory.getLogger(getClass())
              .error("点巡检既有任务维护回退失败 tenant={} plan={}", tenant, id, maintenanceFailure);
        }
      }
    }
  }
}
