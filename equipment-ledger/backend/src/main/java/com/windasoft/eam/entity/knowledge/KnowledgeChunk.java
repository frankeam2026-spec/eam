package com.windasoft.eam.entity.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 知识管理持久化事实，权限和事务由所属Service协调。 */
@Data
@TableName("eam_knowledge_chunk")
public class KnowledgeChunk {
  @TableId(type = IdType.INPUT)
  private Long id;

  private Long tenantId;
  private Long pageId;
  private Integer sequenceNo;
  private String textContent;
  private String contentHash;
}
