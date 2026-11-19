package com.windasoft.eam.entity.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("inv_reservation")
public class InventoryReservation {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long requisitionId;
  private Long requisitionLineId;
  private Long balanceId;
  private BigDecimal quantity;
  private BigDecimal remainingQuantity;
  private LocalDateTime createdAt;
}
