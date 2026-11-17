package com.windasoft.eam.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** wf_node 的持久化字段；复合关系保持真实业务键，不伪造单列主键。 */
@Data
@TableName("wf_node")
public class WorkflowNode {
  @TableId private Long id;
  private Long tenantId;
  private Long versionId;
  private String nodeKey;
  private String nodeType;
  private String name;
  private String nextKey;
  private String approvalMode;
  private Integer sortNo;
  private String parentKey;
}
