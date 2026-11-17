package com.windasoft.eam.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** wf_instance 真实持久化字段；固定结构不通过任意Map传递。 */
@Data
@TableName("wf_instance")
public class WorkflowInstance {
  @TableId private Long id;
  private Long tenantId;
  private Long versionId;
  private String businessType;
  private Long businessId;
  private Long companyId;
  private Long applicantId;
  private Integer roundNo;
  private String engineInstanceId;
  private String state;
  private Long rowVersion;
  private LocalDateTime startedAt;
  private LocalDateTime endedAt;
}
