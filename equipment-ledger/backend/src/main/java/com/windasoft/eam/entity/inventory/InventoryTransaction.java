package com.windasoft.eam.entity.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("inv_transaction")
public class InventoryTransaction {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long companyId;
  private Long warehouseId;
  private Long balanceId;
  private Long documentId;
  private Long sourceLineId;
  private String kind;
  private BigDecimal quantity;
  private BigDecimal amount;
  private Long actorId;
  private LocalDateTime createdAt;
}
