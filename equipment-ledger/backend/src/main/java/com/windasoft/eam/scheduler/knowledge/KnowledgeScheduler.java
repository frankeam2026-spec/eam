package com.windasoft.eam.scheduler.knowledge;
import com.windasoft.eam.repository.knowledge.KnowledgeJobRepository;
import com.windasoft.eam.repository.system.AuthorizationRepository;
import com.windasoft.eam.service.knowledge.*;
import com.windasoft.eam.service.system.SystemExecution;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component
public class KnowledgeScheduler {
  private final KnowledgeJobRepository jobs;private final KnowledgeDocumentService documents;private final KnowledgeIndexService indexes;private final AuthorizationRepository users;
  public KnowledgeScheduler(KnowledgeJobRepository jobs,KnowledgeDocumentService documents,KnowledgeIndexService indexes,AuthorizationRepository users){this.jobs=jobs;this.documents=documents;this.indexes=indexes;this.users=users;}
  @Scheduled(fixedDelay=10000,initialDelay=15000,scheduler="knowledgeTaskScheduler")
  public void parse(){jobs.failExhausted();for(var row:jobs.parsing()){
    long tenant=((Number)row.get("tenant_id")).longValue(),id=((Number)row.get("id")).longValue();int attempt=jobs.claim(tenant,id);if(attempt<0)continue;
    try{var user=users.activeUser(tenant,((Number)row.get("created_by")).longValue());SystemExecution.run(user,()->{try{documents.parseVersion(row,attempt,jobs);return null;}catch(Exception error){throw new IllegalStateException("资料解析失败",error);}});}
    catch(Exception error){jobs.failed(tenant,id,attempt);}
  }}
  @Scheduled(fixedDelay=10000,initialDelay=20000,scheduler="knowledgeTaskScheduler")
  public void index(){for(var row:jobs.indexing()){
    long tenant=((Number)row.get("tenant_id")).longValue();
    try{var user=users.activeUser(tenant,((Number)row.get("created_by")).longValue());SystemExecution.run(user,()->{try{return indexes.indexJob(((Number)row.get("document_id")).longValue(),((Number)row.get("document_row_version")).longValue(),row.get("embedding_model").toString());}catch(Exception error){throw new IllegalStateException("资料索引失败",error);}});}
    catch(Exception error){jobs.failIndex(tenant,((Number)row.get("id")).longValue());}
  }}
}
