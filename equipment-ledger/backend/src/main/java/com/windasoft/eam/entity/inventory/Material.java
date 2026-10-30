package com.windasoft.eam.entity.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 物料主表模型；本批仅用于只读候选查询，不改变现有库存写入入口。 */
@Data
@TableName("inv_material")
public class Material {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private String code;
  private String name;
  private String model;
  private String unit;
  private String category;
  private String usageType;
  private BigDecimal referencePrice;
  private Boolean batchEnabled;
  private Boolean expiryEnabled;
  private String allocationRule;
  private Boolean enabled;
  private String remark;
  @Version private Integer rowVersion;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private Long unitId;
  private Long categoryId;
}
