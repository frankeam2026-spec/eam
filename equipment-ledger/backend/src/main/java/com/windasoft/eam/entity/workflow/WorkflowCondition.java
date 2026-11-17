package com.windasoft.eam.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/** wf_condition 的持久化字段；复合关系保持真实业务键，不伪造单列主键。 */
@Data
@TableName("wf_condition")
public class WorkflowCondition {
  @TableId private Long id;
  private Long tenantId;
  private Long versionId;
  private Long parentId;
  private Integer sortNo;
  private String operator;
  private String fieldKey;
  private String valueType;
  private BigDecimal numberValue;
  private String textValue;
  private Long referenceValue;
  private Boolean booleanValue;
}
