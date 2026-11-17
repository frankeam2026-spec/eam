package com.windasoft.eam.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** wf_approval 持久化对象，业务权限与状态推进由Service处理。 */
@Data
@TableName("wf_approval")
public class WorkflowApproval {
  @TableId private Long id;
  private Long tenantId;
  private Long instanceId;
  private String nodeKey;
  private String engineTaskId;
  private Long userId;
  private String state;
  private String opinion;
  private LocalDateTime actedAt;
}
