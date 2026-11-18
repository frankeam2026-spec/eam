package com.windasoft.eam.entity.business;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 动态字段值的真实复合键包含租户、业务对象、记录、字段及序号，不伪造单列主键。 */
@Data
@TableName("eam_business_value")
public class BusinessValue {
  private Long tenantId;
  private String objectKey;
  private Long recordId;
  private String fieldId;
  private Integer ordinalNo;
  private String textValue;
  private BigDecimal numberValue;
  private LocalDate dateValue;
  private LocalDateTime datetimeValue;
  private Integer boolValue;
  private String referenceId;
  private String referenceName;
}
