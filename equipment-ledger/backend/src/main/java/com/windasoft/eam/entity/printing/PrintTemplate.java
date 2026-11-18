package com.windasoft.eam.entity.printing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 打印模板：画布由现有打印校验器约束，默认范围仍由数据库唯一键保障。 */
@Data
@TableName("eam_print_template")
public class PrintTemplate {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long companyId;
  private String name;
  private String documentType;
  private String configJson;
  private Integer enabled;
  private Integer isDefault;
  private Integer rowVersion;
  private Integer deleted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @TableField(exist = false)
  private String companyName;
}
