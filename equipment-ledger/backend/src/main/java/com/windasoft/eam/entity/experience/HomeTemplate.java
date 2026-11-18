package com.windasoft.eam.entity.experience;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** eam_home_template的持久化字段；画布为既有受校验JSON，元数据和关联保持结构化。 */
@Data
@TableName("eam_home_template")
public class HomeTemplate {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private String name;
  private String presetKey;
  private String draft;
  private Integer priority;
  private Integer publishedVersion;
  private Integer enabled;
  private Integer rowVersion;
  private Long createdBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private String overviewDraft;
}
