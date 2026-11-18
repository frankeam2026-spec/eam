package com.windasoft.eam.repository.knowledge;

import java.util.Objects;
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
    return lockParsingVersion(tenant, version, null);
  }

  public boolean lockParsingAttempt(long tenant, long version, String token) {
    return lockParsingVersion(tenant, version, Objects.requireNonNull(token));
  }

  /** 在锁读中核对归属及到期时间，避免快照读到已经失效的旧租约。 */
  private boolean lockParsingVersion(long tenant, long version, String token) {
    String sql =
        """
        SELECT v.id FROM eam_knowledge_version v JOIN eam_knowledge_document d
          ON d.tenant_id=v.tenant_id AND d.id=v.document_id
        WHERE v.tenant_id=? AND v.id=? AND v.parse_status='RUNNING'
          AND (d.published_version_id IS NULL OR d.published_version_id<>v.id)
        """;
    var rows =
        token == null
            ? db.queryForList(
                sql + " AND v.parse_lease_token IS NULL FOR UPDATE", Long.class, tenant, version)
            : db.queryForList(
                sql
                    + " AND v.parse_lease_token=? AND v.parse_lease_until>CURRENT_TIMESTAMP(6) FOR"
                    + " UPDATE",
                Long.class,
                tenant,
                version,
                token);
    if (rows.size() != 1) return false;
    Integer count =
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_knowledge_page WHERE tenant_id=? AND version_id=?",
            Integer.class,
            tenant,
            version);
    return count != null && count == 0;
  }
}
