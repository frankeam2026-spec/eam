package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.workflow.WorkflowCondition;
import java.util.List;
import org.springframework.stereotype.Repository;

/** WorkflowCondition 主仓储，仅访问本表；跨表顺序与事务由流程服务协调。 */
@Repository
public class WorkflowConditionRepository {
  private final WorkflowConditionMapper mapper;

  public WorkflowConditionRepository(WorkflowConditionMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(WorkflowCondition row) {
    mapper.insert(row);
  }

  /** 批量读取同一租户的关联，不随节点数逐条查询。 */
  public List<WorkflowCondition> list(long tenant, long version) {
    return mapper.selectList(
        new LambdaQueryWrapper<WorkflowCondition>()
            .eq(WorkflowCondition::getTenantId, tenant)
            .eq(WorkflowCondition::getVersionId, version)
            .orderByAsc(WorkflowCondition::getSortNo)
            .orderByAsc(WorkflowCondition::getId));
  }
}
