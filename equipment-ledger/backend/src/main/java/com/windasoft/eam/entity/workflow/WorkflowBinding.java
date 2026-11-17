package com.windasoft.eam.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** wf_binding 真实持久化字段；固定结构不通过任意Map传递。 */
@Data
@TableName("wf_binding")
public class WorkflowBinding {
  private Long tenantId;
  private Long companyId;
  private String businessType;
  private Long versionId;
  private Integer enabled;
  private Long rowVersion;
  private Integer requiresApproval;
}
