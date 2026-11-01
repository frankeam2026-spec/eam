package com.windasoft.eam.entity.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 库存实际使用/安装记录，对应 inv_usage 单表。 */
@Data
@TableName("inv_usage")
public class InventoryUsage {
  @TableId(type = IdType.INPUT)
  private Long id;
  private Long tenantId;
  private Long allocationId;
  private Long requisitionId;
  private Long equipmentId;
  private Long materialId;
  private BigDecimal quantity;
  private String kind;
  private String destination;
  private String confirmationKey;
  private String status;
  private String actorName;
  private LocalDateTime createdAt;
  private LocalDateTime removedAt;
  private Long actorId;
  private String attribution;
  private BigDecimal costAmount;
  private String currency;
  private String equipmentCodeSnapshot;
  private String equipmentNameSnapshot;
  private Long companyIdSnapshot;
  private Long departmentIdSnapshot;
}
