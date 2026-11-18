package com.windasoft.eam.repository.knowledge;

import com.windasoft.eam.entity.knowledge.KnowledgeChunk;
import java.util.List;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeChunkRepository {
  private final KnowledgeChunkMapper mapper;
  private final JdbcTemplate db;

  public KnowledgeChunkRepository(KnowledgeChunkMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
  }

  public void insert(List<KnowledgeChunk> chunks) {
    if (!chunks.isEmpty()) mapper.insert(chunks, 100);
  }

  /** 按版本和游标一次读取10个切片，不逐切片回查页面；调用方先校验文档授权。 */
  public List<KnowledgeChunk> indexed(long tenant, long version, long after) {
    return db.query(
        "SELECT c.id,c.text_content FROM eam_knowledge_chunk c JOIN eam_knowledge_page p ON"
            + " p.tenant_id=c.tenant_id AND p.id=c.page_id WHERE c.tenant_id=? AND p.version_id=?"
            + " AND c.id>? ORDER BY c.id LIMIT 10",
        BeanPropertyRowMapper.newInstance(KnowledgeChunk.class),
        tenant,
        version,
        after);
  }
}
