package com.windasoft.eam.entity.system;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 既有eam_user_preference持久化字段；复合键按完整租户和业务键访问。 */
@Data
@TableName("eam_user_preference")
public class UserPreference {
  private Long tenantId;
  private Long userId;
  private String theme;
  private String locale;
  private LocalDateTime updatedAt;
}
