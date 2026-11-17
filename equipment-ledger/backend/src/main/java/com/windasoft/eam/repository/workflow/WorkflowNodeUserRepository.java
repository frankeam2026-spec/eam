package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.workflow.WorkflowNodeUser;
import java.util.List;
import org.springframework.stereotype.Repository;

/** WorkflowNodeUser 主仓储，仅访问本表；跨表顺序与事务由流程服务协调。 */
@Repository
public class WorkflowNodeUserRepository {
  private final WorkflowNodeUserMapper mapper;

  public WorkflowNodeUserRepository(WorkflowNodeUserMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(WorkflowNodeUser row) {
    mapper.insert(row);
  }

  /** 批量读取同一租户的关联，不随节点数逐条查询。 */
  public List<WorkflowNodeUser> list(long tenant, List<Long> nodes) {
    if (nodes.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<WorkflowNodeUser>()
            .eq(WorkflowNodeUser::getTenantId, tenant)
            .in(WorkflowNodeUser::getNodeId, nodes)
            .orderByAsc(WorkflowNodeUser::getUserId));
  }
}
