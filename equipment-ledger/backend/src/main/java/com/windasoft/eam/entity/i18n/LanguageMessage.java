package com.windasoft.eam.entity.i18n;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 既有eam_language_message持久化字段；复合键按完整租户和业务键访问。 */
@Data
@TableName("eam_language_message")
public class LanguageMessage {
  private Long tenantId;
  private String locale;
  private String messageKey;
  private String messageText;
  private String moduleKey;
}
