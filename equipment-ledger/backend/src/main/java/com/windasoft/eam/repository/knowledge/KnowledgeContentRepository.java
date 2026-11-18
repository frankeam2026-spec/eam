package com.windasoft.eam.repository.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL for immutable parsed content; callers must authorize the parent document first. */
@Repository
public class KnowledgeContentRepository {
  private final JdbcTemplate db;

  public KnowledgeContentRepository(JdbcTemplate db) {
    this.db = db;
  }

  public boolean lockParsingVersion(long tenant, long version) {
    var rows =
        db.queryForList(
            "SELECT v.id FROM eam_knowledge_version v JOIN eam_knowledge_document d ON"
                + " d.tenant_id=v.tenant_id AND d.id=v.document_id WHERE v.tenant_id=? AND v.id=?"
                + " AND v.parse_status='RUNNING' AND (d.published_version_id IS NULL OR"
                + " d.published_version_id<>v.id) FOR UPDATE",
            tenant,
            version);
    if (rows.size() != 1) return false;
    Integer count =
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_knowledge_page WHERE tenant_id=? AND version_id=?",
            Integer.class,
            tenant,
            version);
    return count != null && count == 0;
  }

  public boolean lockParsingAttempt(long tenant, long version, int attempt) {
    return lockParsingVersion(tenant, version)
        && db.queryForObject(
                "SELECT COUNT(*) FROM eam_knowledge_version WHERE tenant_id=? AND id=? AND"
                    + " parse_attempts=? AND parse_lease_until>CURRENT_TIMESTAMP(6)",
                Integer.class,
                tenant,
                version,
                attempt)
            == 1;
  }
}
