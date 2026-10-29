package com.windasoft.eam.service.knowledge;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.repository.knowledge.*;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeIndexService {
  @org.springframework.beans.factory.annotation.Autowired private KnowledgeJobRepository pending;
  private final KnowledgeDocumentRepository documents;
  private final KnowledgeIndexRepository jobs;
  private final KnowledgeEmbeddingClient embeddings;
  private final KnowledgeVectorClient vectors;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final TransactionTemplate tx;
  public KnowledgeIndexService(KnowledgeDocumentRepository documents,KnowledgeIndexRepository jobs,
      KnowledgeEmbeddingClient embeddings,KnowledgeVectorClient vectors,AuthContext auth,PermissionService permissions,TransactionTemplate tx){
    this.documents=documents;this.jobs=jobs;this.embeddings=embeddings;this.vectors=vectors;this.auth=auth;this.permissions=permissions;this.tx=tx;
  }
  private Map<String,Object> check(long id,String action,long expected){
    var scope=permissions.scope("knowledge",action);
    var doc=documents.document(auth.tenantId(),id);
    if(doc==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"知识文档不存在");
    long company=((Number)doc.get("company_id")).longValue();
    if(!scope.all() && !scope.companyIds().contains(company))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无权管理该公司知识资料");
    if(!documents.companyExists(auth.tenantId(),company))throw new IllegalArgumentException("所属公司已失效");
    if(((Number)doc.get("row_version")).longValue()!=expected)throw new ResponseStatusException(HttpStatus.CONFLICT,"文档已变更，请刷新后重试");
    return doc;
  }
  public Object queue(long id,long expected){
    var document=check(id,"编辑",expected);if(!Boolean.TRUE.equals(document.get("applies_to_all"))&&!"1".equals(Objects.toString(document.get("applies_to_all")))&&documents.equipmentCount(auth.tenantId(),id)==0)throw new IllegalArgumentException("请先设置资料适用范围");var version=jobs.latest(auth.tenantId(),id);
    if(version==null||!"READY".equals(version.get("parse_status")))throw new IllegalArgumentException("文档尚未完成解析");
    String model=embeddings.model();if(model.isBlank())throw new IllegalStateException("请先配置向量模型");
    pending.queue(IdWorker.getId(),auth.tenantId(),auth.userId(),((Number)version.get("id")).longValue(),model,expected);return Map.of("status","PENDING");
  }
  public Object indexJob(long id,long expected,String model) throws Exception {if(!model.equals(embeddings.model()))throw new IllegalStateException("索引任务模型已改变");return index(id,expected);}
  public Object index(long id,long expected) throws Exception {
    var doc=check(id,"编辑",expected);
    var version=jobs.latest(auth.tenantId(),id);
    if(version==null || !"READY".equals(version.get("parse_status")))throw new IllegalArgumentException("请先完成所有页面的文字解析");
    if(embeddings.model().isBlank())throw new IllegalStateException("请先配置向量模型");
    long tenant=auth.tenantId(),vid=((Number)version.get("id")).longValue(),company=((Number)doc.get("company_id")).longValue();
    String model=embeddings.model();
    if(jobs.ready(tenant,vid,model))return Map.of("status","READY");
    int attempt=Objects.requireNonNull(tx.execute(s->jobs.claim(IdWorker.getId(),tenant,vid,model)));
    try {
      long after=0;int count=0;
      while(true){
        var chunks=jobs.chunks(tenant,vid,after);if(chunks.isEmpty())break;
        check(id,"编辑",expected);jobs.heartbeat(tenant,vid,model,attempt);
        var values=embeddings.embed(chunks.stream().map(c->c.get("text_content").toString()).toList());
        if(!model.equals(embeddings.model()))throw new IllegalStateException("向量模型配置已改变，请重新建立索引");
        vectors.ensure(model,values.getFirst().size());
        var points=new ArrayList<KnowledgeVectorClient.Point>();
        for(int i=0;i<chunks.size();i++)points.add(new KnowledgeVectorClient.Point(((Number)chunks.get(i).get("id")).longValue(),tenant,company,vid,values.get(i)));
        vectors.upsert(model,points);
        after=((Number)chunks.getLast().get("id")).longValue();count+=chunks.size();
      }
      if(count==0)throw new IllegalStateException("文档没有可索引切片");
      jobs.heartbeat(tenant,vid,model,attempt);
      jobs.finish(tenant,vid,model,attempt,true);
      return Map.of("status","READY","chunkCount",count);
    }catch(Exception error){
      try{jobs.finish(tenant,vid,model,attempt,false);}catch(Exception cleanup){error.addSuppressed(cleanup);}
      throw error;
    }
  }
  public Object publish(long id,long expected){
    return tx.execute(s->{
      var doc=check(id,"发布",expected);
      if(!Boolean.TRUE.equals(doc.get("applies_to_all"))&&!"1".equals(Objects.toString(doc.get("applies_to_all")))&&documents.equipmentCount(auth.tenantId(),id)==0)throw new IllegalArgumentException("发布前请设置资料适用范围");
      var version=jobs.latest(auth.tenantId(),id);
      if(version==null)throw new IllegalArgumentException("文档版本不存在");
      long vid=((Number)version.get("id")).longValue();
      if(!jobs.ready(auth.tenantId(),vid,embeddings.model()))throw new IllegalArgumentException("当前模型的向量索引尚未完成");
      if(!jobs.publish(auth.tenantId(),id,vid,expected))throw new ResponseStatusException(HttpStatus.CONFLICT,"文档已变更或解析未完成");
      return Map.of("status","PUBLISHED","rowVersion",expected+1);
    });
  }
}
