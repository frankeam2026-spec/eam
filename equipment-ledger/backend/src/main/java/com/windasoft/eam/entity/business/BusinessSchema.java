package com.windasoft.eam.entity.business;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 配置当前版本以租户和业务对象组成真实主键；JSON仅保存可配置字段定义，不是业务记录。 */
@Data
@TableName("eam_business_schema")
public class BusinessSchema {
  private Long tenantId;
  private String objectKey;
  private String draft;
  private String published;
  private Integer publishedVersion;
  private Integer rowVersion;
  private Long updatedBy;
  private LocalDateTime updatedAt;
}
