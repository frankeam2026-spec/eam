package com.windasoft.eam.entity.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import lombok.Data;

@Data
@TableName("inv_balance")
public class InventoryBalance {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long companyId;
  private Long warehouseId;
  private Long binId;
  private Long materialId;
  private Long batchId;
  private String stockStatus;
  private BigDecimal quantity;
  private BigDecimal reservedQuantity;
  @Version private Integer rowVersion;
}
