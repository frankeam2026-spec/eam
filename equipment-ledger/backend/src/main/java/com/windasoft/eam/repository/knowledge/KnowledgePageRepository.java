package com.windasoft.eam.repository.knowledge;

import com.windasoft.eam.dto.knowledge.KnowledgeViews;
import com.windasoft.eam.entity.knowledge.KnowledgePage;
import java.util.List;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgePageRepository {
  private final KnowledgePageMapper mapper;
  private final JdbcTemplate db;

  public KnowledgePageRepository(KnowledgePageMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
  }

  public void insert(List<KnowledgePage> pages) {
    if (!pages.isEmpty()) mapper.insert(pages, 100);
  }

  public List<KnowledgeViews.Page> pages(long tenant, long document) {
    return db.query(
        "SELECT p.page_no pageNo,p.needs_ocr needsOcr,p.text_content textContent,(SELECT COUNT(*)"
            + " FROM eam_knowledge_chunk c WHERE c.tenant_id=p.tenant_id AND c.page_id=p.id)"
            + " chunkCount FROM eam_knowledge_page p JOIN eam_knowledge_version v ON"
            + " v.tenant_id=p.tenant_id AND v.id=p.version_id WHERE v.tenant_id=? AND"
            + " v.document_id=? AND v.version_no=(SELECT MAX(v2.version_no) FROM"
            + " eam_knowledge_version v2 WHERE v2.tenant_id=v.tenant_id AND"
            + " v2.document_id=v.document_id) ORDER BY p.page_no",
        DataClassRowMapper.newInstance(KnowledgeViews.Page.class),
        tenant,
        document);
  }
}
