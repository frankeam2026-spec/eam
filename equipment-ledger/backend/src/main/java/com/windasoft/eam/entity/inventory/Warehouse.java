package com.windasoft.eam.entity.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("inv_warehouse")
public class Warehouse {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long companyId;
  private String code;
  private String name;
  private Long managerId;
  private String allocationRule;
  private Boolean enabled;
  private String remark;
  @Version private Integer rowVersion;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
