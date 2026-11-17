package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.domain.workflow.WorkflowApprovalStatus;
import com.windasoft.eam.entity.workflow.WorkflowApproval;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 人员审批任务主仓储，只更新当前租户的任务，不创建或关闭业务收件箱。 */
@Repository
public class WorkflowApprovalRepository {
  private final WorkflowApprovalMapper mapper;

  public WorkflowApprovalRepository(WorkflowApprovalMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(WorkflowApproval row) {
    mapper.insert(row);
  }

  public List<WorkflowApproval> list(long tenant, long instance) {
    return mapper.selectList(
        new LambdaQueryWrapper<WorkflowApproval>()
            .eq(WorkflowApproval::getTenantId, tenant)
            .eq(WorkflowApproval::getInstanceId, instance)
            .orderByAsc(WorkflowApproval::getId));
  }

  public boolean registered(long tenant, long instance, String task) {
    return mapper.selectCount(
            new LambdaQueryWrapper<WorkflowApproval>()
                .eq(WorkflowApproval::getTenantId, tenant)
                .eq(WorkflowApproval::getInstanceId, instance)
                .eq(WorkflowApproval::getEngineTaskId, task))
        > 0;
  }

  /** 已锁定实例后，仍以PENDING条件更新，重复办理不能覆盖已有结果。 */
  public boolean act(long tenant, long id, String state, String opinion) {
    return mapper.update(
            null,
            new LambdaUpdateWrapper<WorkflowApproval>()
                .eq(WorkflowApproval::getTenantId, tenant)
                .eq(WorkflowApproval::getId, id)
                .eq(WorkflowApproval::getState, WorkflowApprovalStatus.PENDING.code())
                .set(WorkflowApproval::getState, state)
                .set(WorkflowApproval::getOpinion, opinion)
                .setSql("acted_at=NOW(3)"))
        == 1;
  }

  public void cancel(long tenant, long instance, String task) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<WorkflowApproval>()
            .eq(WorkflowApproval::getTenantId, tenant)
            .eq(WorkflowApproval::getInstanceId, instance)
            .eq(task != null, WorkflowApproval::getEngineTaskId, task)
            .eq(WorkflowApproval::getState, WorkflowApprovalStatus.PENDING.code())
            .set(WorkflowApproval::getState, WorkflowApprovalStatus.CANCELLED.code()));
  }
}
