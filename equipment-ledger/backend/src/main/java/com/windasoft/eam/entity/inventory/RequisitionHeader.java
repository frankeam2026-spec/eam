package com.windasoft.eam.entity.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "inv_requisition", autoResultMap = true)
public class RequisitionHeader {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long tenantId;
  private String code;
  private Long companyId;
  private Long warehouseId;
  private String taskKind;
  private Long taskId;
  private String taskCode;
  private Long applicantId;
  private String applicantName;
  private String remark;
  private com.windasoft.eam.dto.inventory.RequisitionStatus status;
  private Integer rowVersion;
  private LocalDateTime createdAt;
  @TableLogic private Integer deleted;
  private String approvalGate;
}
