package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.workflow.WorkflowValue;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 类型化变量快照主仓储，动态变量目录的组装属于变量服务。 */
@Repository
public class WorkflowValueRepository {
  private final WorkflowValueMapper mapper;

  public WorkflowValueRepository(WorkflowValueMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(WorkflowValue row) {
    mapper.insert(row);
  }

  public void upsertComputed(WorkflowValue row) {
    mapper.upsertComputed(row);
  }

  public List<WorkflowValue> list(long tenant, long instance) {
    return mapper.selectList(
        new LambdaQueryWrapper<WorkflowValue>()
            .eq(WorkflowValue::getTenantId, tenant)
            .eq(WorkflowValue::getInstanceId, instance)
            .orderByAsc(WorkflowValue::getLineNo)
            .orderByAsc(WorkflowValue::getFieldKey));
  }
}
