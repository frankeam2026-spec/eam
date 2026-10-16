package com.windasoft.eam.service.experience;

import com.windasoft.eam.service.system.SystemExecution;

@org.springframework.stereotype.Service
public class RepairPlanGenerationService {
  final com.windasoft.eam.repository.experience.RepairPlanScheduleRepository repository;
  final AnomalyService service;

  public RepairPlanGenerationService(
      com.windasoft.eam.repository.experience.RepairPlanScheduleRepository repository,
      AnomalyService service) {
    this.repository = repository;
    this.service = service;
  }

  public void scan() {
    for (var row : repository.generationTargets()) {
      long id = ((Number) row.get("id")).longValue(),
          tenant = ((Number) row.get("tenant_id")).longValue();
      try {
        var user = repository.findCreator(((Number) row.get("creator_id")).longValue());
        if (user == null || user.getTenantId() != tenant)
          throw new IllegalArgumentException("计划创建人不可用");
        SystemExecution.run(
            user,
            () -> {
              service.generate(id);
              return true;
            });
      } catch (Exception e) {
        String message =
            e instanceof IllegalArgumentException ? e.getMessage() : "生成失败，请检查创建人权限及关联设备后重试";
        service.generationFailed(
            tenant, id, message.length() > 500 ? message.substring(0, 500) : message);
      }
    }
  }
}
