package com.windasoft.eam.entity.equipment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** eam_equipment_spare 的持久化对象；业务状态与事务由服务层维护。 */
@Data
@TableName("eam_equipment_spare")
public class EquipmentSpare {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long equipmentId;
  private Long stockId;
  private Long orderId;
  private BigDecimal quantity;
  private String status;
  private LocalDateTime installedAt;
  private LocalDateTime removedAt;
}
