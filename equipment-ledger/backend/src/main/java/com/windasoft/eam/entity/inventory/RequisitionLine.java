package com.windasoft.eam.entity.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

@Data
@TableName("inv_requisition_line")
public class RequisitionLine {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long tenantId;
  private Long requisitionId;
  private Integer lineNo;
  private Long materialId;
  private BigDecimal quantity;
  private BigDecimal issuedQuantity;
}
