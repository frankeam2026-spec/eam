package com.windasoft.eam.entity.business;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 发布版本以租户、业务对象和版本组成主键，发布后不可变。 */
@Data
@TableName("eam_business_schema_version")
public class BusinessSchemaVersion {
  private Long tenantId;
  private String objectKey;
  private Integer version;
  private String configuration;
  private Long actorId;
  private LocalDateTime createdAt;
}
