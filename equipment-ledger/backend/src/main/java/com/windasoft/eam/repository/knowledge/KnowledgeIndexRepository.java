package com.windasoft.eam.repository.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.dto.knowledge.KnowledgeViews;
import com.windasoft.eam.entity.knowledge.KnowledgeIndexJob;
import java.util.List;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 索引任务主仓储；不修改文档发布指针、版本或切片内容。 */
@Repository
public class KnowledgeIndexRepository {
  private final KnowledgeIndexJobMapper mapper;
  private final JdbcTemplate db;

  public KnowledgeIndexRepository(KnowledgeIndexJobMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
  }

  private void ensure(
      long id, long tenant, long version, String model, Long user, Long rowVersion) {
    var row = new KnowledgeIndexJob();
    row.setId(id);
    row.setTenantId(tenant);
    row.setVersionId(version);
    row.setEmbeddingModel(model);
    row.setCreatedBy(user);
    row.setDocumentRowVersion(rowVersion);
    mapper.ensure(row);
  }

  private LambdaUpdateWrapper<KnowledgeIndexJob> target(long tenant, long version, String model) {
    return new LambdaUpdateWrapper<KnowledgeIndexJob>()
        .eq(KnowledgeIndexJob::getTenantId, tenant)
        .eq(KnowledgeIndexJob::getVersionId, version)
        .eq(KnowledgeIndexJob::getEmbeddingModel, model);
  }

  /** 首次入队和失败重置保持原条件，不重置有效RUNNING/READY任务或其他模型的索引。 */
  public void queue(long id, long tenant, long user, long version, String model, long rowVersion) {
    ensure(id, tenant, version, model, user, rowVersion);
    mapper.update(
        null,
        target(tenant, version, model)
            .eq(KnowledgeIndexJob::getStatus, "FAILED")
            .set(KnowledgeIndexJob::getStatus, "PENDING")
            .set(KnowledgeIndexJob::getAttempts, 0)
            .set(KnowledgeIndexJob::getCreatedBy, user)
            .set(KnowledgeIndexJob::getDocumentRowVersion, rowVersion)
            .setSql("next_attempt_at=CURRENT_TIMESTAMP(6)"));
  }

  public int claim(long id, long tenant, long version, String model) {
    ensure(id, tenant, version, model, null, null);
    if (mapper.update(
            null,
            target(tenant, version, model)
                .and(
                    q ->
                        q.in(KnowledgeIndexJob::getStatus, List.of("PENDING", "FAILED"))
                            .or()
                            .eq(KnowledgeIndexJob::getStatus, "RUNNING")
                            .apply("lease_until<CURRENT_TIMESTAMP(6)"))
                .set(KnowledgeIndexJob::getStatus, "RUNNING")
                .set(KnowledgeIndexJob::getErrorMessage, null)
                .setSql(
                    "attempts=attempts+1,lease_until=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 5"
                        + " MINUTE)"))
        != 1) throw new IllegalStateException("索引已完成或正在建立，请刷新状态");
    return mapper
        .selectOne(
            new LambdaQueryWrapper<KnowledgeIndexJob>()
                .select(KnowledgeIndexJob::getAttempts)
                .eq(KnowledgeIndexJob::getTenantId, tenant)
                .eq(KnowledgeIndexJob::getVersionId, version)
                .eq(KnowledgeIndexJob::getEmbeddingModel, model))
        .getAttempts();
  }

  public void heartbeat(long tenant, long version, String model, int attempt) {
    if (mapper.update(
            null,
            target(tenant, version, model)
                .eq(KnowledgeIndexJob::getAttempts, attempt)
                .eq(KnowledgeIndexJob::getStatus, "RUNNING")
                .apply("lease_until>CURRENT_TIMESTAMP(6)")
                .setSql("lease_until=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 5 MINUTE)"))
        != 1) throw new IllegalStateException("索引任务租约已失效");
  }

  public void finish(long tenant, long version, String model, int attempt, boolean success) {
    if (mapper.update(
            null,
            target(tenant, version, model)
                .eq(KnowledgeIndexJob::getAttempts, attempt)
                .eq(KnowledgeIndexJob::getStatus, "RUNNING")
                .set(KnowledgeIndexJob::getStatus, success ? "READY" : "FAILED")
                .set(KnowledgeIndexJob::getLeaseUntil, null)
                .set(
                    KnowledgeIndexJob::getErrorMessage,
                    success ? null : "索引建立失败，请检查AI与Qdrant配置后重试"))
        != 1) throw new IllegalStateException("索引任务状态已改变");
  }

  public boolean ready(long tenant, long version, String model) {
    return mapper.selectCount(
            new LambdaQueryWrapper<KnowledgeIndexJob>()
                .eq(KnowledgeIndexJob::getTenantId, tenant)
                .eq(KnowledgeIndexJob::getVersionId, version)
                .eq(KnowledgeIndexJob::getEmbeddingModel, model)
                .eq(KnowledgeIndexJob::getStatus, "READY"))
        == 1;
  }

  public List<KnowledgeViews.IndexCandidate> indexing() {
    return db.query(
        "SELECT"
            + " j.id,j.tenant_id,j.version_id,j.embedding_model,j.created_by,j.document_row_version,v.document_id"
            + " FROM eam_knowledge_index_job j JOIN eam_knowledge_version v ON"
            + " v.tenant_id=j.tenant_id AND v.id=j.version_id WHERE j.created_by IS NOT NULL AND"
            + " j.attempts<3 AND (j.status='PENDING' OR (j.status='RUNNING' AND"
            + " j.lease_until<CURRENT_TIMESTAMP(6))) ORDER BY j.id LIMIT 3",
        DataClassRowMapper.newInstance(KnowledgeViews.IndexCandidate.class));
  }

  public void failExhausted() {
    mapper.update(
        null,
        new LambdaUpdateWrapper<KnowledgeIndexJob>()
            .eq(KnowledgeIndexJob::getStatus, "RUNNING")
            .ge(KnowledgeIndexJob::getAttempts, 3)
            .apply("lease_until<CURRENT_TIMESTAMP(6)")
            .set(KnowledgeIndexJob::getStatus, "FAILED")
            .set(KnowledgeIndexJob::getErrorMessage, "索引重试达到上限，请检查后手工重试"));
  }

  public void failIndex(long tenant, long id) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<KnowledgeIndexJob>()
            .eq(KnowledgeIndexJob::getTenantId, tenant)
            .eq(KnowledgeIndexJob::getId, id)
            .and(
                q ->
                    q.eq(KnowledgeIndexJob::getStatus, "PENDING")
                        .or()
                        .eq(KnowledgeIndexJob::getStatus, "RUNNING")
                        .apply("lease_until<CURRENT_TIMESTAMP(6)"))
            .set(KnowledgeIndexJob::getStatus, "FAILED")
            .set(KnowledgeIndexJob::getErrorMessage, "索引失败，请检查配置、权限和文档版本"));
  }
}
