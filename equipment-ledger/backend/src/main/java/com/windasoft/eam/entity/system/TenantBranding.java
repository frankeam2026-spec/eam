package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 租户品牌配置以租户为真实主键，版本由条件更新递增。 */
@Data
@TableName("eam_tenant_branding")
public class TenantBranding {
  @TableId(type = IdType.INPUT)
  private Long tenantId;

  private String systemName;
  private String logoFileId;
  private String backgroundFileId;
  private String backgroundPosition;
  private Integer rowVersion;
  private Long updatedBy;
  private LocalDateTime updatedAt;
}
