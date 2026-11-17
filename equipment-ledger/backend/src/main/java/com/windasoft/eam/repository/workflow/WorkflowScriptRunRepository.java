package com.windasoft.eam.repository.workflow;

import com.windasoft.eam.entity.workflow.WorkflowScriptRun;
import org.springframework.stereotype.Repository;

/** 脚本执行审计主仓储，不连带覆盖变量快照。 */
@Repository
public class WorkflowScriptRunRepository {
  private final WorkflowScriptRunMapper mapper;

  public WorkflowScriptRunRepository(WorkflowScriptRunMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(WorkflowScriptRun row) {
    mapper.insertAtDatabaseTime(row);
  }
}
