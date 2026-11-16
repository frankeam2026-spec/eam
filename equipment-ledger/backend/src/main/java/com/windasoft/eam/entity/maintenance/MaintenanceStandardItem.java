package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/** 按租户、标准和项目键定位；不伪造单列主键。 */
@Data
@TableName("eam_maintenance_standard_item")
public class MaintenanceStandardItem {
  private Long tenantId;
  private Long standardId;
  private String itemKey;
  private Integer sortNo;
  private String code;
  private String moduleName;
  private String name;
  private String content;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Long typeId;

  private String valueType;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private BigDecimal lowerBound;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private BigDecimal upperBound;

  private Boolean required;
  private Boolean photoRequired;
  private String remark;
  private String spareDescription;
}
