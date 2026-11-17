package com.windasoft.eam.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** wf_node_script 的持久化字段；复合关系保持真实业务键，不伪造单列主键。 */
@Data
@TableName("wf_node_script")
public class WorkflowNodeScript {
  private Long tenantId;
  private Long nodeId;
  private String language;
  private String sourceCode;
  private String outputKey;
  private String outputType;
}
