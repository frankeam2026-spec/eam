package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 按真实复合键定位，不伪造单列主键。 */
@Data
@TableName("eam_notify_default")
public class NotifyDefault {
  private Long tenantId;
  private String businessKind;
  private Long policyId;
  private Integer rowVersion;
  private LocalDateTime effectiveAt;
}
