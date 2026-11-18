package com.windasoft.eam.entity.experience;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** eam_experience_page_preference的持久化字段；画布为既有受校验JSON，元数据和关联保持结构化。 */
@Data
@TableName("eam_experience_page_preference")
public class HomePagePreference {
  private Long tenantId;
  private Long userId;
  private String pageKey;
  private String preferences;
  private LocalDateTime updatedAt;
}
