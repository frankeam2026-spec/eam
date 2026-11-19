package com.windasoft.eam.service.knowledge;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.knowledge.KnowledgeViews;
import com.windasoft.eam.entity.knowledge.KnowledgeChunk;
import com.windasoft.eam.entity.knowledge.KnowledgeDocument;
import com.windasoft.eam.repository.knowledge.KnowledgeChunkRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeDocumentRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeEquipmentRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeIndexRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeVersionRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 索引可重建，文档与关系表仍是权威；向量调用在数据库事务外，租约及发布条件由数据库复核。 */
@Service
public class KnowledgeIndexService {
  private final KnowledgeDocumentRepository documents;
  private final KnowledgeVersionRepository versions;
  private final KnowledgeEquipmentRepository equipment;
  private final KnowledgeIndexRepository jobs;
  private final KnowledgeChunkRepository chunks;
  private final OrgUnitRepository organizations;
  private final KnowledgeEmbeddingClient embeddings;
  private final KnowledgeVectorClient vectors;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final TransactionTemplate tx;

  public KnowledgeIndexService(
      KnowledgeDocumentRepository documents,
      KnowledgeVersionRepository versions,
      KnowledgeEquipmentRepository equipment,
      KnowledgeIndexRepository jobs,
      KnowledgeChunkRepository chunks,
      OrgUnitRepository organizations,
      KnowledgeEmbeddingClient embeddings,
      KnowledgeVectorClient vectors,
      AuthContext auth,
      PermissionService permissions,
      TransactionTemplate tx) {
    this.documents = documents;
    this.versions = versions;
    this.equipment = equipment;
    this.jobs = jobs;
    this.chunks = chunks;
    this.organizations = organizations;
    this.embeddings = embeddings;
    this.vectors = vectors;
    this.auth = auth;
    this.permissions = permissions;
    this.tx = tx;
  }

  private KnowledgeDocument check(long id, PermissionAction action, long expected) {
    var scope = permissions.scope("knowledge", action);
    var d = documents.document(auth.tenantId(), id);
    if (d == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识文档不存在");
    long company = d.getCompanyId();
    if (!scope.all() && !scope.companyIds().contains(company))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权管理该公司知识资料");
    var org = organizations.company(auth.tenantId(), company);
    if (org == null || org.getStatus() != 1) throw new IllegalArgumentException("所属公司已失效");
    if (d.getRowVersion() != expected)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "文档已变更，请刷新后重试");
    return d;
  }

  public KnowledgeViews.Status queue(long id, long expected) {
    var d = check(id, PermissionAction.EDIT, expected);
    if (!Boolean.TRUE.equals(d.getAppliesToAll()) && equipment.count(auth.tenantId(), id) == 0)
      throw new IllegalArgumentException("请先设置资料适用范围");
    var v = versions.latest(auth.tenantId(), id);
    if (v == null || !"READY".equals(v.getParseStatus()))
      throw new IllegalArgumentException("文档尚未完成解析");
    String model = embeddings.model();
    if (model.isBlank()) throw new IllegalStateException("请先配置向量模型");
    jobs.queue(IdWorker.getId(), auth.tenantId(), auth.userId(), v.getId(), model, expected);
    return new KnowledgeViews.Status("PENDING");
  }

  public KnowledgeViews.IndexStatus indexJob(KnowledgeViews.IndexCandidate candidate)
      throws Exception {
    return index(candidate.documentId(), candidate.documentRowVersion(), candidate);
  }

  /** 每个批次重验文档授权和模型；失败只结束本尝试，不把向量写入伪装为数据库回滚。 */
  public KnowledgeViews.IndexStatus index(long id, long expected) throws Exception {
    return index(id, expected, null);
  }

  private KnowledgeViews.IndexStatus index(
      long id, long expected, KnowledgeViews.IndexCandidate candidate) throws Exception {
    var d = check(id, PermissionAction.EDIT, expected);
    var v = versions.latest(auth.tenantId(), id);
    if (v == null || !"READY".equals(v.getParseStatus()))
      throw new IllegalArgumentException("请先完成所有页面的文字解析");
    if (embeddings.model().isBlank()) throw new IllegalStateException("请先配置向量模型");
    long tenant = auth.tenantId(), vid = v.getId(), company = d.getCompanyId();
    String model = embeddings.model();
    if (candidate != null
        && (candidate.tenantId() != tenant
            || candidate.versionId() != vid
            || !candidate.embeddingModel().equals(model)))
      throw new IllegalStateException("索引任务版本或模型已改变");
    if (jobs.ready(tenant, vid, model)) return new KnowledgeViews.IndexStatus("READY", null);
    String token =
        Objects.requireNonNull(
            tx.execute(
                s ->
                    candidate == null
                        ? jobs.claim(IdWorker.getId(), tenant, vid, model)
                        : jobs.claim(candidate)));
    try {
      long after = 0;
      int count = 0;
      while (true) {
        var batch = chunks.indexed(tenant, vid, after);
        if (batch.isEmpty()) break;
        check(id, PermissionAction.EDIT, expected);
        jobs.heartbeat(tenant, vid, model, token);
        var values = embeddings.embed(batch.stream().map(KnowledgeChunk::getTextContent).toList());
        if (!model.equals(embeddings.model())) throw new IllegalStateException("向量模型配置已改变，请重新建立索引");
        vectors.ensure(model, values.getFirst().size());
        var points = new ArrayList<KnowledgeVectorClient.Point>();
        for (int i = 0; i < batch.size(); i++)
          points.add(
              new KnowledgeVectorClient.Point(
                  batch.get(i).getId(), tenant, company, vid, values.get(i)));
        // 模型调用可能耗时，写入外部索引前再次确认仍持有当前租约。
        jobs.heartbeat(tenant, vid, model, token);
        vectors.upsert(model, points);
        after = batch.getLast().getId();
        count += batch.size();
      }
      if (count == 0) throw new IllegalStateException("文档没有可索引切片");
      jobs.heartbeat(tenant, vid, model, token);
      jobs.finish(tenant, vid, model, token, true);
      return new KnowledgeViews.IndexStatus("READY", count);
    } catch (Exception error) {
      try {
        jobs.finish(tenant, vid, model, token, false);
      } catch (Exception cleanup) {
        error.addSuppressed(cleanup);
      }
      throw error;
    }
  }

  /** 发布仍用原事务，同时检查适用范围、当前模型索引就绪及文档/内容版本条件。 */
  public KnowledgeViews.StatusVersion publish(long id, long expected) {
    return tx.execute(
        s -> {
          var d = check(id, PermissionAction.PUBLISH, expected);
          if (!Boolean.TRUE.equals(d.getAppliesToAll())
              && equipment.count(auth.tenantId(), id) == 0)
            throw new IllegalArgumentException("发布前请设置资料适用范围");
          var v = versions.latest(auth.tenantId(), id);
          if (v == null) throw new IllegalArgumentException("文档版本不存在");
          if (!jobs.ready(auth.tenantId(), v.getId(), embeddings.model()))
            throw new IllegalArgumentException("当前模型的向量索引尚未完成");
          if (!documents.publish(auth.tenantId(), id, v.getId(), expected))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文档已变更或解析未完成");
          return new KnowledgeViews.StatusVersion("PUBLISHED", expected + 1);
        });
  }
}
