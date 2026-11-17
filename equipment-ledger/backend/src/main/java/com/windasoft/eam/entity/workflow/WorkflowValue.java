package com.windasoft.eam.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/** wf_value 真实持久化字段；固定结构不通过任意Map传递。 */
@Data
@TableName("wf_value")
public class WorkflowValue {
  @TableId private Long id;
  private Long tenantId;
  private Long instanceId;
  private String fieldKey;
  private Integer lineNo;
  private String valueType;
  private Boolean isNull;
  private BigDecimal numberValue;
  private String textValue;
  private Long referenceValue;
  private Boolean booleanValue;
}
