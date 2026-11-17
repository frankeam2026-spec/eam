package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.workflow.WorkflowNodeRole;
import java.util.List;
import org.springframework.stereotype.Repository;

/** WorkflowNodeRole 主仓储，仅访问本表；跨表顺序与事务由流程服务协调。 */
@Repository
public class WorkflowNodeRoleRepository {
  private final WorkflowNodeRoleMapper mapper;

  public WorkflowNodeRoleRepository(WorkflowNodeRoleMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(WorkflowNodeRole row) {
    mapper.insert(row);
  }

  /** 批量读取同一租户的关联，不随节点数逐条查询。 */
  public List<WorkflowNodeRole> list(long tenant, List<Long> nodes) {
    if (nodes.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<WorkflowNodeRole>()
            .eq(WorkflowNodeRole::getTenantId, tenant)
            .in(WorkflowNodeRole::getNodeId, nodes)
            .orderByAsc(WorkflowNodeRole::getRoleId));
  }
}
