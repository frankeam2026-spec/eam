package com.windasoft.eam.repository.workflow;

import com.windasoft.eam.entity.workflow.WorkflowTodo;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 审批任务与站内待办关联主仓储，保留真实复合键和消息来源。 */
@Repository
public class WorkflowTodoRepository {
  private final WorkflowTodoMapper mapper;
  private final JdbcTemplate db;

  public WorkflowTodoRepository(WorkflowTodoMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
  }

  public void insert(WorkflowTodo row) {
    mapper.insert(row);
  }

  public record InboxLink(long inboxId, String sourceKind) {}

  /** 只读取当前实例中已结束的审批关联；不按单据整批关闭其他轮次或仓库待办。 */
  public List<InboxLink> closedApprovals(long tenant, long instance) {
    return db.query(
        """
        SELECT t.inbox_id,t.source_kind FROM wf_todo t
        JOIN wf_approval a ON a.tenant_id=t.tenant_id AND a.id=t.approval_id
        WHERE a.tenant_id=? AND a.instance_id=? AND a.state<>'PENDING'
        """,
        (r, n) -> new InboxLink(r.getLong("inbox_id"), r.getString("source_kind")),
        tenant,
        instance);
  }
}
