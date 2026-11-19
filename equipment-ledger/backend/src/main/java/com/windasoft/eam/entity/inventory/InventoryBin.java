package com.windasoft.eam.entity.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

@Data
@TableName("inv_bin")
public class InventoryBin {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long warehouseId;
  private String code;
  private String name;
  private Boolean enabled;
  private String remark;
  @Version private Integer rowVersion;
}
