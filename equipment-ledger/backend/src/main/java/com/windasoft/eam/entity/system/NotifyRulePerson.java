package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 按真实复合键定位，不伪造单列主键。 */
@Data
@TableName("eam_notify_rule_person")
public class NotifyRulePerson {
  private Long tenantId;
  private Long ruleId;
  private Long userId;
  private String recipientKind;
}
