package com.windasoft.eam.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** wf_todo 持久化对象，业务权限与状态推进由Service处理。 */
@Data
@TableName("wf_todo")
public class WorkflowTodo {
  private Long tenantId;
  private Long approvalId;
  private Long inboxId;
  private String sourceKind;
}
