package com.windasoft.eam.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** wf_node_role 的持久化字段；复合关系保持真实业务键，不伪造单列主键。 */
@Data
@TableName("wf_node_role")
public class WorkflowNodeRole {
  private Long tenantId;
  private Long nodeId;
  private Long roleId;
}
