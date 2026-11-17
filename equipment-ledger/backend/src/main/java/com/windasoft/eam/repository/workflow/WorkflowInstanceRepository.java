package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.domain.workflow.WorkflowInstanceStatus;
import com.windasoft.eam.entity.workflow.WorkflowInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 实例主体主仓储，不写审批人员、变量、通知或业务单据。 */
@Repository
public class WorkflowInstanceRepository {
  private final WorkflowInstanceMapper mapper;
  private final JdbcTemplate db;

  public WorkflowInstanceRepository(WorkflowInstanceMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
  }

  public WorkflowInstance instance(long tenant, long id, boolean lock) {
    var row =
        mapper.selectOne(
            new LambdaQueryWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getTenantId, tenant)
                .eq(WorkflowInstance::getId, id)
                .last(lock ? "FOR UPDATE" : ""));
    if (row == null) {
      throw new IllegalArgumentException("审批实例不存在");
    }
    return row;
  }

  public WorkflowInstance latest(long tenant, String business, long id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<WorkflowInstance>()
            .eq(WorkflowInstance::getTenantId, tenant)
            .eq(WorkflowInstance::getBusinessType, business)
            .eq(WorkflowInstance::getBusinessId, id)
            .orderByDesc(WorkflowInstance::getRoundNo)
            .last("LIMIT 1"));
  }

  /** 调用方先取得业务锁；轮次唯一约束仍防止并发重复提交，不以先查询代替锁。 */
  public long start(
      long tenant, long version, String business, long businessId, long company, long applicant) {
    var previous = latest(tenant, business, businessId);
    var row = new WorkflowInstance();
    row.setId(IdWorker.getId());
    row.setTenantId(tenant);
    row.setVersionId(version);
    row.setBusinessType(business);
    row.setBusinessId(businessId);
    row.setCompanyId(company);
    row.setApplicantId(applicant);
    row.setRoundNo(previous == null ? 1 : Math.addExact(previous.getRoundNo(), 1));
    row.setState(WorkflowInstanceStatus.RUNNING.code());
    mapper.insert(row);
    return row.getId();
  }

  public void engine(long tenant, long id, String process) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<WorkflowInstance>()
            .eq(WorkflowInstance::getTenantId, tenant)
            .eq(WorkflowInstance::getId, id)
            .set(WorkflowInstance::getEngineInstanceId, process));
  }

  public void finish(long tenant, long id, String state) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<WorkflowInstance>()
            .eq(WorkflowInstance::getTenantId, tenant)
            .eq(WorkflowInstance::getId, id)
            .eq(WorkflowInstance::getState, WorkflowInstanceStatus.RUNNING.code())
            .set(WorkflowInstance::getState, state)
            .setSql("row_version=row_version+1,ended_at=NOW(3)"));
  }

  public void touch(long tenant, long id) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<WorkflowInstance>()
            .eq(WorkflowInstance::getTenantId, tenant)
            .eq(WorkflowInstance::getId, id)
            .setSql("row_version=row_version+1"));
  }

  /** 参与资格需要跨表匹配业务对象，只读联查不授予仓库或流程配置权限。 */
  public boolean participant(long tenant, String business, long id, long user) {
    return db.queryForObject(
            """
SELECT COUNT(*) FROM wf_instance i JOIN wf_approval a ON a.tenant_id=i.tenant_id AND a.instance_id=i.id
WHERE i.tenant_id=? AND i.business_type=? AND i.business_id=? AND a.user_id=?
""",
            Integer.class,
            tenant,
            business,
            id,
            user)
        > 0;
  }
}
