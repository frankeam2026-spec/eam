package com.windasoft.eam.basedata;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("eam_base_item")
public class BaseItem {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long tenantId;
  private Long companyOrgId;
  private String itemType;
  private String code;
  private String name;
  private Long parentId;
  private Integer decimalPrecision;
  private Integer sortNo;
  private Integer status;
  @TableLogic private Integer deleted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
