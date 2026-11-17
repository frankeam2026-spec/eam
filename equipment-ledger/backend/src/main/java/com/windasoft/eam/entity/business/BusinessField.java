package com.windasoft.eam.entity.business;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 字段UUID在全局唯一，租户及对象归属一经建立不能被覆盖。 */
@Data
@TableName("eam_business_field")
public class BusinessField {
  @TableId(type = IdType.INPUT)
  private String id;

  private Long tenantId;
  private String objectKey;
  private String dataType;
  private String sourceKey;
  private Integer everPublished;
}
