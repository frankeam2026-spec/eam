package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 统一通知配置数据，状态和关联事务由Service校验编排。 */
@Data
@TableName("eam_notify_rule")
public class NotifyRule {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long policyId;
  private String scene;
  private Integer offsetValue;
  private String offsetUnit;
  private Integer enabled;
  private Integer archived;
  private Integer sortNo;
  private LocalDateTime effectiveAt;
}
