package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 品牌变更审计沿用历史JSON前后快照，图片历史引用仍依据afterJson保留。 */
@Data
@TableName("eam_branding_event")
public class BrandingEvent {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long userId;
  private String beforeJson;
  private String afterJson;
  private LocalDateTime createdAt;
}
