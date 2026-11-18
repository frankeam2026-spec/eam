package com.windasoft.eam.entity.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 知识管理持久化事实，权限和事务由所属Service协调。 */
@Data
@TableName("eam_knowledge_index_job")
public class KnowledgeIndexJob {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long versionId;
  private String embeddingModel;
  private String status;
  private Integer attempts;
  private LocalDateTime nextAttemptAt;
  private LocalDateTime leaseUntil;
  private String leaseToken;
  private String errorMessage;
  private Long createdBy;
  private Long documentRowVersion;
}
