package com.windasoft.eam.entity.i18n;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 既有eam_language_event持久化字段；复合键按完整租户和业务键访问。 */
@Data
@TableName("eam_language_event")
public class LanguageEvent {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private String locale;
  private String actionCode;
  private Long actorId;
  private Integer rowVersion;
  private Integer messageCount;
  private LocalDateTime createdAt;
}
