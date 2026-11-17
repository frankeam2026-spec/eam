package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.workflow.WorkflowNodeScript;
import java.util.List;
import org.springframework.stereotype.Repository;

/** WorkflowNodeScript 主仓储，仅访问本表；跨表顺序与事务由流程服务协调。 */
@Repository
public class WorkflowNodeScriptRepository {
  private final WorkflowNodeScriptMapper mapper;

  public WorkflowNodeScriptRepository(WorkflowNodeScriptMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(WorkflowNodeScript row) {
    mapper.insert(row);
  }

  /** 批量读取同一租户的关联，不随节点数逐条查询。 */
  public List<WorkflowNodeScript> list(long tenant, List<Long> nodes) {
    if (nodes.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<WorkflowNodeScript>()
            .eq(WorkflowNodeScript::getTenantId, tenant)
            .in(WorkflowNodeScript::getNodeId, nodes));
  }
}
