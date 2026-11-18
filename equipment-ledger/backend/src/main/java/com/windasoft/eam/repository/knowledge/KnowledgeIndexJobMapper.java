package com.windasoft.eam.repository.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.knowledge.KnowledgeIndexJob;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeIndexJobMapper extends BaseMapper<KnowledgeIndexJob> {
  /** 唯一版本/模型键负责初始化争用，失败重试不覆盖其他尝试的租约。 */
  @Insert(
      "INSERT IGNORE INTO"
          + " eam_knowledge_index_job(id,tenant_id,version_id,embedding_model,next_attempt_at,created_by,document_row_version)"
          + " VALUES(#{row.id},#{row.tenantId},#{row.versionId},#{row.embeddingModel},CURRENT_TIMESTAMP(6),#{row.createdBy},#{row.documentRowVersion})")
  int ensure(@Param("row") KnowledgeIndexJob row);
}
