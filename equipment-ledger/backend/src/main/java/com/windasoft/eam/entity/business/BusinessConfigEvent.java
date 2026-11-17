package com.windasoft.eam.entity.business;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 配置审计保留数据库生成时间及原JSON文本语义。 */
@Data
@TableName("eam_business_config_event")
public class BusinessConfigEvent {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private String objectKey;
  private Long recordId;
  private Long actorId;
  private String actorName;
  private String action;
  private String details;
  private LocalDateTime createdAt;
}
