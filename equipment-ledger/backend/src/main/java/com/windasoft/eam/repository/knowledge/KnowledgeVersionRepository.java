package com.windasoft.eam.repository.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.dto.knowledge.KnowledgeViews;
import com.windasoft.eam.entity.knowledge.KnowledgeVersion;
import java.util.List;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeVersionRepository {
  private final KnowledgeVersionMapper mapper;
  private final JdbcTemplate db;

  public KnowledgeVersionRepository(KnowledgeVersionMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
  }

  public void insert(KnowledgeVersion row) {
    mapper.insert(row);
  }

  public KnowledgeVersion latest(long tenant, long document) {
    return mapper.selectOne(
        new LambdaQueryWrapper<KnowledgeVersion>()
            .eq(KnowledgeVersion::getTenantId, tenant)
            .eq(KnowledgeVersion::getDocumentId, document)
            .orderByDesc(KnowledgeVersion::getVersionNo)
            .last("LIMIT 1"));
  }

  /** 在Service持有文档主体锁后取得下个内容版本，防并发产生相同版本号。 */
  public int nextNumber(long tenant, long document) {
    return mapper
        .selectOne(
            new QueryWrapper<KnowledgeVersion>()
                .select("COALESCE(MAX(version_no),0)+1 AS version_no")
                .eq("tenant_id", tenant)
                .eq("document_id", document))
        .getVersionNo();
  }

  public KnowledgeViews.Versions list(long tenant, long document, int page, int size) {
    long count =
        mapper.selectCount(
            new LambdaQueryWrapper<KnowledgeVersion>()
                .eq(KnowledgeVersion::getTenantId, tenant)
                .eq(KnowledgeVersion::getDocumentId, document));
    var rows =
        db.query(
            """
SELECT CAST(v.id AS CHAR) id,v.version_no versionNo,v.parse_status parseStatus,
  v.parser_version parserVersion,v.error_message errorMessage,v.created_at createdAt,
  (d.published_version_id=v.id) published
FROM eam_knowledge_version v JOIN eam_knowledge_document d ON d.tenant_id=v.tenant_id AND d.id=v.document_id
WHERE v.tenant_id=? AND v.document_id=? ORDER BY v.version_no DESC LIMIT ? OFFSET ?
""",
            DataClassRowMapper.newInstance(KnowledgeViews.Version.class),
            tenant,
            document,
            size,
            (page - 1L) * size);
    return new KnowledgeViews.Versions(rows, count);
  }

  public String publishedFile(long tenant, long document, long version) {
    var rows =
        db.queryForList(
            """
SELECT v.file_key FROM eam_knowledge_version v JOIN eam_knowledge_document d
 ON d.tenant_id=v.tenant_id AND d.id=v.document_id AND d.published_version_id=v.id
WHERE v.tenant_id=? AND d.id=? AND v.id=? AND d.status='PUBLISHED' AND v.has_original=TRUE
""",
            String.class,
            tenant,
            document,
            version);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private LambdaUpdateWrapper<KnowledgeVersion> target(long tenant, long version) {
    return new LambdaUpdateWrapper<KnowledgeVersion>()
        .eq(KnowledgeVersion::getTenantId, tenant)
        .eq(KnowledgeVersion::getId, version);
  }

  public boolean retryParse(long tenant, long version, long user) {
    return mapper.update(
            null,
            target(tenant, version)
                .eq(KnowledgeVersion::getParseStatus, "FAILED")
                .set(KnowledgeVersion::getParseStatus, "PENDING")
                .set(KnowledgeVersion::getParseAttempts, 0)
                .set(KnowledgeVersion::getParseLeaseUntil, null)
                .set(KnowledgeVersion::getErrorMessage, null)
                .set(KnowledgeVersion::getCreatedBy, user))
        == 1;
  }

  public List<KnowledgeVersion> parsing() {
    return mapper.selectList(
        new LambdaQueryWrapper<KnowledgeVersion>()
            .isNotNull(KnowledgeVersion::getCreatedBy)
            .lt(KnowledgeVersion::getParseAttempts, 3)
            .and(
                q ->
                    q.eq(KnowledgeVersion::getParseStatus, "PENDING")
                        .or()
                        .eq(KnowledgeVersion::getParseStatus, "RUNNING")
                        .apply("parse_lease_until<CURRENT_TIMESTAMP(6)"))
            .orderByAsc(KnowledgeVersion::getId)
            .last("LIMIT 3"));
  }

  /** 条件领取与尝试次数一起推进，恢复过期租约时不接管仍有效的解析。 */
  public int claim(long tenant, long version) {
    if (mapper.update(
            null,
            target(tenant, version)
                .lt(KnowledgeVersion::getParseAttempts, 3)
                .and(
                    q ->
                        q.eq(KnowledgeVersion::getParseStatus, "PENDING")
                            .or()
                            .eq(KnowledgeVersion::getParseStatus, "RUNNING")
                            .apply("parse_lease_until<CURRENT_TIMESTAMP(6)"))
                .set(KnowledgeVersion::getParseStatus, "RUNNING")
                .set(KnowledgeVersion::getErrorMessage, null)
                .setSql(
                    "parse_attempts=parse_attempts+1,parse_lease_until=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL"
                        + " 5 MINUTE)"))
        != 1) return -1;
    return mapper
        .selectOne(
            new LambdaQueryWrapper<KnowledgeVersion>()
                .select(KnowledgeVersion::getParseAttempts)
                .eq(KnowledgeVersion::getTenantId, tenant)
                .eq(KnowledgeVersion::getId, version))
        .getParseAttempts();
  }

  public void heartbeat(long tenant, long version, int attempt) {
    if (mapper.update(
            null,
            target(tenant, version)
                .eq(KnowledgeVersion::getParseStatus, "RUNNING")
                .eq(KnowledgeVersion::getParseAttempts, attempt)
                .apply("parse_lease_until>CURRENT_TIMESTAMP(6)")
                .setSql("parse_lease_until=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 5 MINUTE)"))
        != 1) throw new IllegalStateException("解析任务租约已失效");
  }

  public void failed(long tenant, long version, int attempt) {
    mapper.update(
        null,
        target(tenant, version)
            .eq(KnowledgeVersion::getParseStatus, "RUNNING")
            .eq(KnowledgeVersion::getParseAttempts, attempt)
            .set(KnowledgeVersion::getParseStatus, "FAILED")
            .set(KnowledgeVersion::getErrorMessage, "解析失败，请检查PDF、OCR配置和权限后重试")
            .set(KnowledgeVersion::getParseLeaseUntil, null));
  }

  public void failExhausted() {
    mapper.update(
        null,
        new LambdaUpdateWrapper<KnowledgeVersion>()
            .eq(KnowledgeVersion::getParseStatus, "RUNNING")
            .ge(KnowledgeVersion::getParseAttempts, 3)
            .apply("parse_lease_until<CURRENT_TIMESTAMP(6)")
            .set(KnowledgeVersion::getParseStatus, "FAILED")
            .set(KnowledgeVersion::getErrorMessage, "解析重试达到上限，请检查后手工重试"));
  }

  public void finishParsed(long tenant, long version, boolean needsOcr) {
    if (mapper.update(
            null,
            target(tenant, version)
                .eq(KnowledgeVersion::getParseStatus, "RUNNING")
                .set(KnowledgeVersion::getParseStatus, needsOcr ? "NEEDS_OCR" : "READY")
                .set(KnowledgeVersion::getErrorMessage, null))
        != 1) throw new IllegalStateException("知识文档解析状态已改变");
  }
}
