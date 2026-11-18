package com.windasoft.eam.entity.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 文档元数据为知识权威，沿用既有BIGINT版本，不缩窄为int。 */
@Data
@TableName("eam_knowledge_document")
public class KnowledgeDocument {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long companyId;
  private String title;
  private String documentType;
  private String status;
  private Long publishedVersionId;
  private Long createdBy;
  private Long rowVersion;
  private Boolean appliesToAll;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
