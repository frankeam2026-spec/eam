package com.windasoft.eam.entity.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/** eam_spare_stock 的持久化对象；业务状态与事务由服务层维护。 */
@Data
@TableName("eam_spare_stock")
public class LegacySpareStock {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long companyId;
  private String code;
  private String name;
  private String model;
  private BigDecimal quantity;
}
