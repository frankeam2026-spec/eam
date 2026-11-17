package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.entity.workflow.WorkflowEvent;
import org.springframework.stereotype.Repository;

/** 流程事件主仓储；发生时间沿用数据库默认毫秒时钟。 */
@Repository
public class WorkflowEventRepository {
  private final WorkflowEventMapper mapper;

  public WorkflowEventRepository(WorkflowEventMapper mapper) {
    this.mapper = mapper;
  }

  public void event(long tenant, long instance, String node, String type, long actor, String text) {
    var row = new WorkflowEvent();
    row.setId(IdWorker.getId());
    row.setTenantId(tenant);
    row.setInstanceId(instance);
    row.setNodeKey(node);
    row.setEventType(type);
    row.setActorId(actor);
    row.setDescription(text);
    mapper.insert(row);
  }
}
