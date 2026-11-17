package com.windasoft.eam.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** wf_script_run 真实持久化字段；固定结构不通过任意Map传递。 */
@Data
@TableName("wf_script_run")
public class WorkflowScriptRun {
  @TableId private Long id;
  private Long tenantId;
  private Long instanceId;
  private String nodeKey;
  private String executionId;
  private String sourceHash;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
  private String valueType;
  private BigDecimal numberValue;
  private String textValue;
  private Boolean booleanValue;
}
