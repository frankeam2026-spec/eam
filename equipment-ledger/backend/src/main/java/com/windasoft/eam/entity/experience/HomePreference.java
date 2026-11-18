package com.windasoft.eam.entity.experience;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** eam_home_preference的持久化字段；画布为既有受校验JSON，元数据和关联保持结构化。 */
@Data
@TableName("eam_home_preference")
public class HomePreference {
  private Long tenantId;
  private Long userId;
  private Long defaultTemplateId;
  private String recentJson;
}
