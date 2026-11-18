package com.windasoft.eam.scheduler.knowledge;

import com.windasoft.eam.repository.knowledge.KnowledgeIndexRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeVersionRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import com.windasoft.eam.service.knowledge.KnowledgeDocumentService;
import com.windasoft.eam.service.knowledge.KnowledgeIndexService;
import com.windasoft.eam.service.system.SystemExecution;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 后台任务仍以发起人当前身份执行；仅领取过期租约或待处理任务，不绕过公司授权。 */
@Component
public class KnowledgeScheduler {
  private final KnowledgeVersionRepository versions;
  private final KnowledgeIndexRepository jobs;
  private final KnowledgeDocumentService documents;
  private final KnowledgeIndexService indexes;
  private final SystemUserRepository users;

  public KnowledgeScheduler(
      KnowledgeVersionRepository versions,
      KnowledgeIndexRepository jobs,
      KnowledgeDocumentService documents,
      KnowledgeIndexService indexes,
      SystemUserRepository users) {
    this.versions = versions;
    this.jobs = jobs;
    this.documents = documents;
    this.indexes = indexes;
    this.users = users;
  }

  @Scheduled(fixedDelay = 10000, initialDelay = 15000, scheduler = "knowledgeTaskScheduler")
  public void parse() {
    versions.failExhausted();
    jobs.failExhausted();
    for (var row : versions.parsing()) {
      long tenant = row.getTenantId(), id = row.getId();
      int attempt = versions.claim(tenant, id);
      if (attempt < 0) continue;
      try {
        var user = users.activeUser(tenant, row.getCreatedBy());
        SystemExecution.run(
            user,
            () -> {
              try {
                documents.parseVersion(row, attempt);
                return null;
              } catch (Exception error) {
                throw new IllegalStateException("资料解析失败", error);
              }
            });
      } catch (Exception error) {
        versions.failed(tenant, id, attempt);
      }
    }
  }

  @Scheduled(fixedDelay = 10000, initialDelay = 20000, scheduler = "knowledgeTaskScheduler")
  public void index() {
    for (var row : jobs.indexing()) {
      long tenant = row.tenantId();
      try {
        var user = users.activeUser(tenant, row.createdBy());
        SystemExecution.run(
            user,
            () -> {
              try {
                return indexes.indexJob(
                    row.documentId(), row.documentRowVersion(), row.embeddingModel());
              } catch (Exception error) {
                throw new IllegalStateException("资料索引失败", error);
              }
            });
      } catch (Exception error) {
        jobs.failIndex(tenant, row.id());
      }
    }
  }
}
