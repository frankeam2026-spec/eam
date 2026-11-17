package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.workflow.WorkflowNode;
import java.util.List;
import org.springframework.stereotype.Repository;

/** WorkflowNode 主仓储，仅访问本表；跨表顺序与事务由流程服务协调。 */
@Repository
public class WorkflowNodeRepository {
  private final WorkflowNodeMapper mapper;

  public WorkflowNodeRepository(WorkflowNodeMapper mapper) {
    this.mapper = mapper;
  }

  /** 跨轮次拓扑按版本集合批量读取，仍限制同一租户。 */
  public List<WorkflowNode> versions(long tenant, List<Long> versions) {
    if (versions.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<WorkflowNode>()
            .eq(WorkflowNode::getTenantId, tenant)
            .in(WorkflowNode::getVersionId, versions)
            .orderByAsc(WorkflowNode::getSortNo)
            .orderByAsc(WorkflowNode::getId));
  }

  public void insert(WorkflowNode row) {
    mapper.insert(row);
  }

  /** 批量读取同一租户的关联，不随节点数逐条查询。 */
  public List<WorkflowNode> list(long tenant, long version) {
    return mapper.selectList(
        new LambdaQueryWrapper<WorkflowNode>()
            .eq(WorkflowNode::getTenantId, tenant)
            .eq(WorkflowNode::getVersionId, version)
            .orderByAsc(WorkflowNode::getSortNo)
            .orderByAsc(WorkflowNode::getId));
  }
}
