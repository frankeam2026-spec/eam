package com.windasoft.eam.repository.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.dto.knowledge.KnowledgeViews;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.knowledge.KnowledgeDocument;
import java.util.ArrayList;
import java.util.Collections;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 文档本表读写；版本、适用设备、案例来源及索引任务由各自仓储维护。 */
@Repository
public class KnowledgeDocumentRepository {
  private final JdbcTemplate db;
  private final KnowledgeDocumentMapper mapper;

  public KnowledgeDocumentRepository(JdbcTemplate db, KnowledgeDocumentMapper mapper) {
    this.db = db;
    this.mapper = mapper;
  }

  /** 列表需同页取得版本及最新索引状态，保留必要只读联查及原授权/稳定排序。 */
  public KnowledgeViews.Documents list(
      long tenant, PermissionScope scope, String query, int page, int size) {
    var args = new ArrayList<Object>();
    args.add(tenant);
    String where = " WHERE d.tenant_id=?";
    if (!scope.all()) {
      if (scope.companyIds().isEmpty()) where += " AND 1=0";
      else {
        where +=
            " AND d.company_id IN ("
                + String.join(",", Collections.nCopies(scope.companyIds().size(), "?"))
                + ")";
        args.addAll(scope.companyIds());
      }
    }
    if (!query.isBlank()) {
      where += " AND LOCATE(?,d.title)>0";
      args.add(query);
    }
    Long total =
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_knowledge_document d" + where, Long.class, args.toArray());
    args.add(size);
    args.add((page - 1L) * size);
    return new KnowledgeViews.Documents(
        db.query(
            "SELECT CAST(d.id AS CHAR) id,CAST(d.company_id AS CHAR)"
                + " companyId,d.title,d.document_type documentType,d.status,d.row_version"
                + " rowVersion,d.updated_at updatedAt,(SELECT v.parse_status FROM"
                + " eam_knowledge_version v WHERE v.tenant_id=d.tenant_id AND v.document_id=d.id"
                + " ORDER BY v.version_no DESC LIMIT 1) parseStatus,(SELECT j.status FROM"
                + " eam_knowledge_index_job j JOIN eam_knowledge_version v ON"
                + " v.tenant_id=j.tenant_id AND v.id=j.version_id WHERE v.tenant_id=d.tenant_id AND"
                + " v.document_id=d.id ORDER BY j.id DESC LIMIT 1) indexStatus FROM"
                + " eam_knowledge_document d"
                + where
                + " ORDER BY d.updated_at DESC,d.id DESC LIMIT ? OFFSET ?",
            DataClassRowMapper.newInstance(KnowledgeViews.Document.class),
            args.toArray()),
        total == null ? 0 : total);
  }

  public KnowledgeDocument document(long tenant, long id) {
    return document(tenant, id, false);
  }

  public KnowledgeDocument document(long tenant, long id, boolean lock) {
    var q =
        new LambdaQueryWrapper<KnowledgeDocument>()
            .eq(KnowledgeDocument::getTenantId, tenant)
            .eq(KnowledgeDocument::getId, id);
    if (lock) q.last("FOR UPDATE");
    return mapper.selectOne(q);
  }

  public void insert(KnowledgeDocument row) {
    mapper.insert(row);
  }

  private LambdaUpdateWrapper<KnowledgeDocument> version(long tenant, long id, long expected) {
    return new LambdaUpdateWrapper<KnowledgeDocument>()
        .eq(KnowledgeDocument::getTenantId, tenant)
        .eq(KnowledgeDocument::getId, id)
        .eq(KnowledgeDocument::getRowVersion, expected);
  }

  public boolean advance(long tenant, long id, long expected) {
    return mapper.update(
            null,
            version(tenant, id, expected)
                .setSql("row_version=row_version+1,updated_at=CURRENT_TIMESTAMP(6)"))
        == 1;
  }

  public boolean disable(long tenant, long id, long expected) {
    return mapper.update(
            null,
            version(tenant, id, expected)
                .set(KnowledgeDocument::getStatus, "DISABLED")
                .setSql("row_version=row_version+1,updated_at=CURRENT_TIMESTAMP(6)"))
        == 1;
  }

  public boolean bind(long tenant, long id, long expected, boolean all) {
    return mapper.update(
            null,
            version(tenant, id, expected)
                .set(KnowledgeDocument::getAppliesToAll, all)
                .set(KnowledgeDocument::getStatus, "DRAFT")
                .set(KnowledgeDocument::getPublishedVersionId, null)
                .setSql("row_version=row_version+1,updated_at=CURRENT_TIMESTAMP(6)"))
        == 1;
  }

  /** 发布仍在同一条件更新中核对同租户、同文档且已解析版本，不能只信前面的读取。 */
  public boolean publish(long tenant, long document, long contentVersion, long expected) {
    return mapper.update(
            null,
            version(tenant, document, expected)
                .set(KnowledgeDocument::getStatus, "PUBLISHED")
                .set(KnowledgeDocument::getPublishedVersionId, contentVersion)
                .setSql("row_version=row_version+1,updated_at=CURRENT_TIMESTAMP(6)")
                .exists(
                    "SELECT 1 FROM eam_knowledge_version v WHERE v.tenant_id={0} AND v.id={1} AND"
                        + " v.document_id={2} AND v.parse_status='READY'",
                    tenant, contentVersion, document))
        == 1;
  }
}
