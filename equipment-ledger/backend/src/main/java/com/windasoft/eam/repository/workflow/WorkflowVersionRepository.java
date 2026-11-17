package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.workflow.WorkflowVersion;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 流程版本主仓储；版本图不可变性和模板锁由调用方业务事务保证。 */
@Repository
public class WorkflowVersionRepository {
  private final WorkflowVersionMapper mapper;

  public WorkflowVersionRepository(WorkflowVersionMapper mapper) {
    this.mapper = mapper;
  }

  public WorkflowVersion find(long tenant, long id) {
    var row =
        mapper.selectOne(
            new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getTenantId, tenant)
                .eq(WorkflowVersion::getId, id));
    if (row == null) {
      throw new IllegalArgumentException("流程版本不存在");
    }
    return row;
  }

  public List<WorkflowVersion> list(long tenant, long template) {
    return mapper.selectList(
        new LambdaQueryWrapper<WorkflowVersion>()
            .eq(WorkflowVersion::getTenantId, tenant)
            .eq(WorkflowVersion::getTemplateId, template)
            .orderByDesc(WorkflowVersion::getVersionNo));
  }

  public WorkflowVersion latest(long tenant, long template) {
    var row =
        mapper.selectOne(
            new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getTenantId, tenant)
                .eq(WorkflowVersion::getTemplateId, template)
                .orderByDesc(WorkflowVersion::getVersionNo)
                .last("LIMIT 1"));
    if (row == null) {
      throw new IllegalArgumentException("流程版本不存在");
    }
    return row;
  }

  public void insert(WorkflowVersion row) {
    mapper.insert(row);
  }

  /** 只能把草稿原子切换为已发布；旧执行版本仍保留原产物。 */
  public boolean publish(long tenant, long id, long actor, String xml, String engineId) {
    return mapper.update(
            null,
            new LambdaUpdateWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getTenantId, tenant)
                .eq(WorkflowVersion::getId, id)
                .eq(WorkflowVersion::getState, "DRAFT")
                .set(WorkflowVersion::getState, "PUBLISHED")
                .set(WorkflowVersion::getExecutionVersion, 2)
                .set(WorkflowVersion::getBpmnXml, xml)
                .set(WorkflowVersion::getEngineDefinitionId, engineId)
                .set(WorkflowVersion::getPublishedBy, actor)
                .setSql("published_at=NOW(3)"))
        == 1;
  }

  /** 调用前已锁定模板；唯一约束继续作为版本号并发冲突的最后保障。 */
  public int nextNumber(long tenant, long template) {
    var last =
        mapper.selectOne(
            new LambdaQueryWrapper<WorkflowVersion>()
                .select(WorkflowVersion::getVersionNo)
                .eq(WorkflowVersion::getTenantId, tenant)
                .eq(WorkflowVersion::getTemplateId, template)
                .orderByDesc(WorkflowVersion::getVersionNo)
                .last("LIMIT 1"));
    return last == null ? 1 : Math.addExact(last.getVersionNo(), 1);
  }
}
