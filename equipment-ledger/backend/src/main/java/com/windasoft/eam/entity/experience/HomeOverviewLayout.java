package com.windasoft.eam.entity.experience;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** eam_home_overview_layout的持久化字段；画布为既有受校验JSON，元数据和关联保持结构化。 */
@Data
@TableName("eam_home_overview_layout")
public class HomeOverviewLayout {
  private Long tenantId;
  private Long userId;
  private Long templateId;
  private Integer templateVersion;
  private String payload;
  private Integer rowVersion;
  private LocalDateTime updatedAt;
}
