package com.windasoft.eam.service.knowledge;

import com.windasoft.eam.repository.knowledge.KnowledgeSearchRepository;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeRetrievalService {
  @org.springframework.beans.factory.annotation.Autowired private com.windasoft.eam.service.equipment.EquipmentQueryService equipmentQueries;
  private final KnowledgeSearchRepository repository;
  private final KnowledgeEmbeddingClient embeddings;
  private final KnowledgeVectorClient vectors;
  private final AuthContext auth;
  private final PermissionService permissions;
  public KnowledgeRetrievalService(KnowledgeSearchRepository repository,KnowledgeEmbeddingClient embeddings,
      KnowledgeVectorClient vectors,AuthContext auth,PermissionService permissions){
    this.repository=repository;this.embeddings=embeddings;this.vectors=vectors;this.auth=auth;this.permissions=permissions;
  }
  public List<Map<String,Object>> retrieve(String question) throws Exception {
    return retrieve(question,null);
  }
  public List<Map<String,Object>> retrieve(String question,Long equipment) throws Exception {
    if(question==null || question.isBlank() || question.length()>2000)throw new IllegalArgumentException("问题须为1至2000个字符");
    var scope=permissions.scope("knowledge","查看");
    String model=embeddings.model();
    if(equipment!=null)equipmentQueries.get(equipment);
    var versions=equipment==null?repository.versions(auth.tenantId(),scope,model):repository.versions(auth.tenantId(),scope,model,equipment);
    if(versions.isEmpty())return List.of();
    var query=embeddings.embed(List.of(question)).getFirst();
    if(!model.equals(embeddings.model()))throw new IllegalStateException("向量模型配置已改变，请重试");
    var semantic=vectors.search(model,query,auth.tenantId(),versions);
    var lexical=repository.keyword(auth.tenantId(),versions,terms(question));
    var scores=new HashMap<Long,Double>();
    for(var ranking:List.of(semantic,lexical))for(int i=0;i<ranking.size();i++)scores.merge(ranking.get(i),1.0/(60+i+1),Double::sum);
    var ids=scores.keySet().stream().sorted(Comparator.<Long>comparingDouble(scores::get).reversed().thenComparingLong(Long::longValue)).limit(12).toList();
    // Never trust vector payload authorization; re-read current publication and grants.
    var rows=repository.chunks(auth.tenantId(),permissions.scope("knowledge","查看"),ids);
    var byId=new HashMap<String,Map<String,Object>>();rows.forEach(r->byId.put(r.get("chunkId").toString(),r));
    return ids.stream().map(id->byId.get(Long.toString(id))).filter(Objects::nonNull).limit(6).toList();
  }
  static List<String> terms(String question){
    var terms=new LinkedHashSet<String>();var identifiers=java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{1,63}").matcher(question);while(identifiers.find()&&terms.size()<6)terms.add(identifiers.group());
    var chinese=java.util.regex.Pattern.compile("[\\p{IsHan}]{2,}").matcher(question);while(chinese.find()&&terms.size()<12){String word=chinese.group();if(word.length()<=8)terms.add(word);else for(int i=0;i+2<=word.length()&&terms.size()<12;i+=2)terms.add(word.substring(i,i+2));}
    return List.copyOf(terms);
  }
  public void recheck(List<Map<String,Object>> sources){
    recheck(sources,null);
  }
  public void recheck(List<Map<String,Object>> sources,Long equipment){
    var ids=sources.stream().map(s->Long.parseLong(s.get("chunkId").toString())).toList();
    if(repository.chunks(auth.tenantId(),permissions.scope("knowledge","查看"),ids).size()!=ids.size())
      throw new IllegalStateException("资料权限或发布版本已改变，请重新提问");
    if(equipment!=null){equipmentQueries.get(equipment);var versions=repository.versions(auth.tenantId(),permissions.scope("knowledge","查看"),embeddings.model(),equipment);if(sources.stream().anyMatch(s->!versions.contains(Long.parseLong(s.get("versionId").toString()))))throw new IllegalStateException("资料适用设备已改变，请重新提问");}
  }
}
