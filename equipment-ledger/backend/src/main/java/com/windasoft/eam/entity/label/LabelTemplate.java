package com.windasoft.eam.entity.label;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("eam_label_template")
public class LabelTemplate {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long tenantId;
  private String name;
  private BigDecimal widthMm;
  private BigDecimal heightMm;
  private String remark;
  private String elementsJson;

  // 清空网格布局时回到自由元素模板，更新必须将旧布局写为 SQL NULL。
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String layoutJson;

  @Version private Integer rowVersion;
  @TableLogic private Integer deleted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
