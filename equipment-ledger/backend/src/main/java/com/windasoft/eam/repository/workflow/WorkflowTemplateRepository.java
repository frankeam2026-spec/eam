package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.workflow.WorkflowTemplate;
import org.springframework.stereotype.Repository;

/** 模板主体主仓储，不保存版本图或决定公司权限。 */
@Repository
public class WorkflowTemplateRepository {
  private final WorkflowTemplateMapper mapper;

  public WorkflowTemplateRepository(WorkflowTemplateMapper mapper) {
    this.mapper = mapper;
  }

  public WorkflowTemplate find(long tenant, long id, boolean lock) {
    var row =
        mapper.selectOne(
            new LambdaQueryWrapper<WorkflowTemplate>()
                .eq(WorkflowTemplate::getTenantId, tenant)
                .eq(WorkflowTemplate::getId, id)
                .last(lock ? "FOR UPDATE" : ""));
    if (row == null) {
      throw new IllegalArgumentException("流程模板不存在");
    }
    return row;
  }

  public void insert(WorkflowTemplate row) {
    mapper.insert(row);
  }

  /** 乐观锁及数据库时钟与旧接口一致，不能用先读取后覆盖替代原子更新。 */
  public boolean update(
      long tenant, long id, long version, String name, String description, long actor) {
    return mapper.update(
            null,
            new LambdaUpdateWrapper<WorkflowTemplate>()
                .eq(WorkflowTemplate::getTenantId, tenant)
                .eq(WorkflowTemplate::getId, id)
                .eq(WorkflowTemplate::getRowVersion, version)
                .set(WorkflowTemplate::getName, name)
                .set(WorkflowTemplate::getDescription, description)
                .set(WorkflowTemplate::getUpdatedBy, actor)
                .setSql("row_version=row_version+1,updated_at=NOW(3)"))
        == 1;
  }
}
