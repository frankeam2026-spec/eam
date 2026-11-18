package com.windasoft.eam.entity.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 内容版本独立于文档行版本；文件键受控，解析租约由原业务流程维护。 */
@Data
@TableName("eam_knowledge_version")
public class KnowledgeVersion {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long documentId;
  private Integer versionNo;
  private String fileKey;
  private String sha256;
  private String parseStatus;
  private String parserVersion;
  private String errorMessage;
  private Long createdBy;
  private Integer parseAttempts;
  private LocalDateTime parseLeaseUntil;
  private String parseLeaseToken;
  private Boolean hasOriginal;
  private LocalDateTime createdAt;
}
