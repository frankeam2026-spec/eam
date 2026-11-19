package com.windasoft.eam.service.knowledge;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.repository.knowledge.KnowledgeConversationRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeSearchRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeConversationService {
  public static final class TurnFailed extends RuntimeException {
    public TurnFailed(Throwable cause){super("本次提问已失败",cause);}
  }
  private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(KnowledgeConversationService.class);
  @org.springframework.beans.factory.annotation.Autowired private com.windasoft.eam.service.equipment.EquipmentQueryService equipmentQueries;
  private final KnowledgeConversationRepository repository;
  private final KnowledgeSearchRepository sources;
  private final KnowledgeAnswerService answers;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final TransactionTemplate tx;
  public KnowledgeConversationService(KnowledgeConversationRepository repository,KnowledgeSearchRepository sources,
      KnowledgeAnswerService answers,AuthContext auth,PermissionService permissions,TransactionTemplate tx){
    this.repository=repository;this.sources=sources;this.answers=answers;this.auth=auth;this.permissions=permissions;this.tx=tx;
  }
  private void owner(long id,boolean lock){
    permissions.require("knowledge",PermissionAction.VIEW);
    if(!repository.owner(auth.tenantId(),auth.userId(),id,lock))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"对话不存在");
  }
  public Object create(){permissions.require("knowledge",PermissionAction.VIEW);long id=IdWorker.getId();repository.create(auth.tenantId(),auth.userId(),id);return Map.of("id",Long.toString(id));}
  public Object list(int page,int size){permissions.require("knowledge",PermissionAction.VIEW);return Map.of("data",repository.list(auth.tenantId(),auth.userId(),page,size),"total",repository.count(auth.tenantId(),auth.userId()));}
  public void delete(long id){tx.executeWithoutResult(s->{owner(id,true);repository.delete(auth.tenantId(),auth.userId(),id);});}
  public void bind(long id,long equipment){tx.executeWithoutResult(s->{owner(id,true);equipmentQueries.get(equipment);repository.equipment(auth.tenantId(),auth.userId(),id,equipment);});}
  private Map<String, Object> view(Map<String, Object> row) {
    long tenant = auth.tenantId();
    var ids = repository.citations(tenant, exchangeId(row));
    var citations = sources.chunks(tenant, permissions.scope("knowledge", PermissionAction.VIEW), ids);
    return view(row, ids.size(), citations);
  }

  private static Map<String, Object> view(
      Map<String, Object> row, int expected, List<Map<String, Object>> citations) {
    boolean revoked = citations.size() != expected;
    var result = new LinkedHashMap<String, Object>();
    result.put("id", row.get("id"));
    result.put("question", row.get("question"));
    result.put("status", row.get("status"));
    result.put(
        "answer", revoked ? "此回答的来源权限或发布版本已改变，请重新提问。" : Objects.toString(row.get("answer"), ""));
    result.put("citations", revoked ? List.of() : citations);
    result.put("restricted", revoked);
    return result;
  }

  private static long exchangeId(Map<String, Object> row) {
    return Long.parseLong(row.get("id").toString());
  }

  public void authorize(long id) {
    owner(id, false);
  }

  public Object history(long id) {
    return history(id, 1, 50);
  }

  /** 每页批量校验当前发布版本和知识查看权限，某条回答任一引用失效时整条隐藏。 */
  public Object history(long id, int page, int size) {
    owner(id, false);
    long tenant = auth.tenantId();
    var rows = new ArrayList<>(repository.exchanges(tenant, id, page, size));
    Collections.reverse(rows);
    Long equipment = repository.equipment(tenant, auth.userId(), id);
    if (equipment != null) {
      equipmentQueries.get(equipment);
    }
    var links =
        repository.citations(
            tenant, rows.stream().map(KnowledgeConversationService::exchangeId).toList());
    var chunkIds = links.values().stream().flatMap(List::stream).distinct().toList();
    var visible = new HashMap<Long, Map<String, Object>>();
    if (!chunkIds.isEmpty()) {
      for (var citation : sources.chunks(tenant, permissions.scope("knowledge", PermissionAction.VIEW), chunkIds)) {
        visible.put(Long.parseLong(citation.get("chunkId").toString()), citation);
      }
    }
    var data =
        rows.stream()
            .map(
                row -> {
                  var ids = links.getOrDefault(exchangeId(row), List.of());
                  var citations = ids.stream().map(visible::get).filter(Objects::nonNull).toList();
                  return view(row, ids.size(), citations);
                })
            .toList();
    return Map.of(
        "data",
        data,
        "total",
        repository.exchangeCount(tenant, id),
        "equipmentId",
        equipment == null ? "" : Long.toString(equipment));
  }

  @SuppressWarnings("unchecked")
  public Object ask(long conversation,String key,String question) throws Exception {
    return ask(conversation,key,question,null);
  }
  @SuppressWarnings("unchecked")
  public Object ask(long conversation,String key,String question,com.fasterxml.jackson.databind.JsonNode context) throws Exception {
    if(key==null || !key.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}") || question==null || question.isBlank() || question.length()>2000)
      throw new IllegalArgumentException("提问参数无效");
    long tenant=auth.tenantId(),exchange=IdWorker.getId();
    var existing=tx.execute(s->{
      owner(conversation,true);var row=repository.existing(tenant,conversation,key);
      if(row!=null){if(!question.equals(row.get("question")))throw new ResponseStatusException(HttpStatus.CONFLICT,"幂等键已用于其他问题");return row;}
      repository.start(tenant,conversation,exchange,key,question);return null;
    });
    if(existing!=null){
      Long equipment=repository.equipment(tenant,auth.userId(),conversation);if(equipment!=null)equipmentQueries.get(equipment);
      if("FAILED".equals(existing.get("status")))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"上次提问已失败，请重新发送");
      if(!"SUCCEEDED".equals(existing.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"本次提问正在处理或已失败，请查看历史状态");
      return view(existing);
    }
    try{
      var prior=new ArrayList<String>();
      for(var row:repository.exchanges(tenant,conversation)){
        if(!"SUCCEEDED".equals(row.get("status")))continue;
        var visible=view(row);if(Boolean.TRUE.equals(visible.get("restricted")))continue;
        String q=row.get("question").toString();if(q.length()<=400)prior.add(q);
        if(prior.size()==3)break;
      }
      Collections.reverse(prior);
      Long equipment=repository.equipment(tenant,auth.userId(),conversation);if(equipment!=null)equipmentQueries.get(equipment);
      var answer=(Map<String,Object>)(context==null?answers.ask(question,List.copyOf(prior),equipment):answers.ask(question,List.copyOf(prior),equipment,context));
      var citations=(List<Map<String,Object>>)answer.get("citations");
      var ids=citations.stream().map(c->Long.parseLong(c.get("chunkId").toString())).toList();
      // Business figures must be queried with current permissions, not replayed from unscoped history text.
      boolean liveData=List.of("businessQueries","analytics","repairRecords","equipmentRecords","workInboxes").stream().anyMatch(answer::containsKey);
      String historyAnswer=liveData?"本轮已查询业务数据。请重新提问，按当前权限获取最新结果。":answer.get("answer").toString();
      tx.executeWithoutResult(s->{owner(conversation,true);repository.finish(tenant,exchange,historyAnswer,ids);});
      var result=new LinkedHashMap<>(answer);result.put("id",Long.toString(exchange));return result;
    }catch(Exception error){
      Throwable root=error;while(root.getCause()!=null&&root.getCause()!=root)root=root.getCause();
      log.warn("AI turn failed tenant={} exchange={} type={} frames={}",tenant,exchange,root.getClass().getName(),Arrays.stream(root.getStackTrace()).limit(8).map(StackTraceElement::toString).toList());
      repository.fail(tenant,exchange);throw new TurnFailed(error);
    }
  }
}
