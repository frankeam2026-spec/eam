package com.windasoft.eam.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** wf_branch 的持久化字段；复合关系保持真实业务键，不伪造单列主键。 */
@Data
@TableName("wf_branch")
public class WorkflowBranch {
  @TableId private Long id;
  private Long tenantId;
  private Long nodeId;
  private String branchKey;
  private String name;
  private Integer sortNo;
  private Boolean fallback;
  private String targetKey;
  private Long conditionId;
  private String expressionSource;
}
