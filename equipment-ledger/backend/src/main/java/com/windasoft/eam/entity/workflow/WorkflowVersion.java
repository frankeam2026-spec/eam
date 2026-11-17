package com.windasoft.eam.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** wf_version 的持久化字段；复合关系保持真实业务键，不伪造单列主键。 */
@Data
@TableName("wf_version")
public class WorkflowVersion {
  @TableId private Long id;
  private Long tenantId;
  private Long templateId;
  private Integer versionNo;
  private String state;
  private String bpmnXml;
  private String engineDefinitionId;
  private Long publishedBy;
  private LocalDateTime publishedAt;
  private String diagramXml;
  private Integer executionVersion;
}
