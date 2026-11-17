package com.windasoft.eam.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** wf_template 的持久化字段；复合关系保持真实业务键，不伪造单列主键。 */
@Data
@TableName("wf_template")
public class WorkflowTemplate {
  @TableId private Long id;
  private Long tenantId;
  private Long companyId;
  private String businessType;
  private String name;
  private String description;
  private Long rowVersion;
  private Long createdBy;
  private Long updatedBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
