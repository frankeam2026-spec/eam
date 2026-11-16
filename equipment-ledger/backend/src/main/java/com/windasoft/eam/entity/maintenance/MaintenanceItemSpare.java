package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/** eam_maintenance_item_spare的真实复合键明细，不使用虚构的单列ID。 */
@Data
@TableName("eam_maintenance_item_spare")
public class MaintenanceItemSpare {
  private Long tenantId;
  private Long standardId;
  private String itemKey;
  private Integer sortNo;
  private Long stockId;
  private BigDecimal quantity;
}
