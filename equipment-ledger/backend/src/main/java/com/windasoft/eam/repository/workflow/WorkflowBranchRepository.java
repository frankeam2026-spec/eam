package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.workflow.WorkflowBranch;
import java.util.List;
import org.springframework.stereotype.Repository;

/** WorkflowBranch 主仓储，仅访问本表；跨表顺序与事务由流程服务协调。 */
@Repository
public class WorkflowBranchRepository {
  private final WorkflowBranchMapper mapper;

  public WorkflowBranchRepository(WorkflowBranchMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(WorkflowBranch row) {
    mapper.insert(row);
  }

  /** 批量读取同一租户的关联，不随节点数逐条查询。 */
  public List<WorkflowBranch> list(long tenant, List<Long> nodes) {
    if (nodes.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<WorkflowBranch>()
            .eq(WorkflowBranch::getTenantId, tenant)
            .in(WorkflowBranch::getNodeId, nodes)
            .orderByAsc(WorkflowBranch::getSortNo)
            .orderByAsc(WorkflowBranch::getId));
  }
}
