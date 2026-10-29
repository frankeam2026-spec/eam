package com.windasoft.eam.repository.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL for immutable parsed content; callers must authorize the parent document first. */
@Repository
public class KnowledgeContentRepository {
  private final JdbcTemplate db;

  public KnowledgeContentRepository(JdbcTemplate db) { this.db = db; }

  public boolean lockParsingVersion(long tenant, long version) {
    var rows = db.queryForList(
        "SELECT v.id FROM eam_knowledge_version v JOIN eam_knowledge_document d"
            + " ON d.tenant_id=v.tenant_id AND d.id=v.document_id"
            + " WHERE v.tenant_id=? AND v.id=? AND v.parse_status='RUNNING'"
            + " AND (d.published_version_id IS NULL OR d.published_version_id<>v.id) FOR UPDATE",
        tenant, version);
    if (rows.size() != 1) return false;
    Integer count = db.queryForObject(
        "SELECT COUNT(*) FROM eam_knowledge_page WHERE tenant_id=? AND version_id=?",
        Integer.class, tenant, version);
    return count != null && count == 0;
  }
  public boolean lockParsingAttempt(long tenant,long version,int attempt){
    return lockParsingVersion(tenant,version)&&db.queryForObject("SELECT COUNT(*) FROM eam_knowledge_version WHERE tenant_id=? AND id=? AND parse_attempts=? AND parse_lease_until>CURRENT_TIMESTAMP(6)",Integer.class,tenant,version,attempt)==1;
  }

  public record Chunk(long id, int sequence, String text) {}
  public record Page(long id, int pageNo, String text, boolean needsOcr, List<Chunk> chunks) {}

  public void insertPages(long tenant, long version, List<Page> pages) {
    db.batchUpdate(
        "INSERT INTO eam_knowledge_page(id,tenant_id,version_id,page_no,text_content,needs_ocr) VALUES(?,?,?,?,?,?)",
        pages, 100, (ps, p) -> {
          ps.setLong(1, p.id()); ps.setLong(2, tenant); ps.setLong(3, version);
          ps.setInt(4, p.pageNo()); ps.setString(5, p.text()); ps.setBoolean(6, p.needsOcr());
        });
    for (var p : pages) db.batchUpdate(
        "INSERT INTO eam_knowledge_chunk(id,tenant_id,page_id,sequence_no,text_content,content_hash) VALUES(?,?,?,?,?,?)",
        p.chunks(), 100, (ps, c) -> {
          ps.setLong(1, c.id()); ps.setLong(2, tenant); ps.setLong(3, p.id());
          ps.setInt(4, c.sequence()); ps.setString(5, c.text()); ps.setString(6, hash(c.text()));
        });
    int changed = db.update(
        "UPDATE eam_knowledge_version SET parse_status=?,error_message=NULL WHERE tenant_id=? AND id=? AND parse_status='RUNNING'",
        pages.stream().anyMatch(Page::needsOcr) ? "NEEDS_OCR" : "READY", tenant, version);
    if (changed != 1) throw new IllegalStateException("知识文档解析状态已改变");
  }

  static String hash(String text) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
