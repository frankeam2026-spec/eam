package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("eam_org_unit")
public class OrgUnit {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long tenantId;
  private String name;
  private String code;
  private String orgType;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String displayType;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Long parentId;

  private String manager;
  private Integer status;
  @TableLogic private Integer deleted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
