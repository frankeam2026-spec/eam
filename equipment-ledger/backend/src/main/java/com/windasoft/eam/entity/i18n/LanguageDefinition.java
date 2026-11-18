package com.windasoft.eam.entity.i18n;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 既有eam_language持久化字段；复合键按完整租户和业务键访问。 */
@Data
@TableName("eam_language")
public class LanguageDefinition {
  private Long tenantId;
  private String locale;
  private String nativeName;
  private String direction;
  private Integer builtin;
  private Integer enabled;
  private Integer rowVersion;
  private Long updatedBy;
  private LocalDateTime updatedAt;
}
