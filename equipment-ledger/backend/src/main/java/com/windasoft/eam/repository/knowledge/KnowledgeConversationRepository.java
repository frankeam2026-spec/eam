package com.windasoft.eam.repository.knowledge;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeConversationRepository {
  private final JdbcTemplate db;
  public KnowledgeConversationRepository(JdbcTemplate db){this.db=db;}
  public void create(long tenant,long user,long id){db.update("INSERT INTO eam_ai_conversation(id,tenant_id,user_id,title) VALUES(?,?,?,'新对话')",id,tenant,user);}
  public List<Map<String,Object>> list(long tenant,long user,int page,int size){
    return db.queryForList("SELECT CAST(id AS CHAR) id,title,updated_at updatedAt FROM eam_ai_conversation WHERE tenant_id=? AND user_id=? AND deleted=FALSE ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",tenant,user,size,(page-1L)*size);
  }
  public long count(long tenant,long user){return db.queryForObject("SELECT COUNT(*) FROM eam_ai_conversation WHERE tenant_id=? AND user_id=? AND deleted=FALSE",Long.class,tenant,user);}
  public boolean owner(long tenant,long user,long id,boolean lock){
    return !db.queryForList("SELECT id FROM eam_ai_conversation WHERE tenant_id=? AND user_id=? AND id=? AND deleted=FALSE"+(lock?" FOR UPDATE":""),tenant,user,id).isEmpty();
  }
  public void delete(long tenant,long user,long id){db.update("UPDATE eam_ai_conversation SET deleted=TRUE WHERE tenant_id=? AND user_id=? AND id=?",tenant,user,id);}
  public Long equipment(long tenant,long user,long id){return db.queryForObject("SELECT equipment_id FROM eam_ai_conversation WHERE tenant_id=? AND user_id=? AND id=? AND deleted=FALSE",Long.class,tenant,user,id);}
  public void equipment(long tenant,long user,long id,long equipment){
    if(db.queryForObject("SELECT COUNT(*) FROM eam_ai_exchange WHERE tenant_id=? AND conversation_id=?",Integer.class,tenant,id)>0)throw new IllegalArgumentException("切换设备请新建对话");
    db.update("UPDATE eam_ai_conversation SET equipment_id=? WHERE tenant_id=? AND user_id=? AND id=? AND deleted=FALSE",equipment,tenant,user,id);
  }
  public List<Map<String,Object>> exchanges(long tenant,long conversation){
    return exchanges(tenant,conversation,1,50);
  }
  public List<Map<String,Object>> exchanges(long tenant,long conversation,int page,int size){return db.queryForList("SELECT CAST(id AS CHAR) id,question,answer,status FROM eam_ai_exchange WHERE tenant_id=? AND conversation_id=? ORDER BY id DESC LIMIT ? OFFSET ?",tenant,conversation,size,(page-1L)*size);}
  public long exchangeCount(long tenant,long conversation){return db.queryForObject("SELECT COUNT(*) FROM eam_ai_exchange WHERE tenant_id=? AND conversation_id=?",Long.class,tenant,conversation);}
  public List<Long> citations(long tenant,long exchange){return db.queryForList("SELECT chunk_id FROM eam_ai_exchange_citation WHERE tenant_id=? AND exchange_id=? ORDER BY sort_no",Long.class,tenant,exchange);}
  public Map<String,Object> existing(long tenant,long conversation,String key){
    var rows=db.queryForList("SELECT CAST(id AS CHAR) id,question,answer,status FROM eam_ai_exchange WHERE tenant_id=? AND conversation_id=? AND request_key=?",tenant,conversation,key);
    return rows.isEmpty()?null:rows.getFirst();
  }
  public void start(long tenant,long conversation,long id,String key,String question){
    db.update("UPDATE eam_ai_exchange SET status='FAILED',completed_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND conversation_id=? AND status='RUNNING' AND lease_until<CURRENT_TIMESTAMP(6)",tenant,conversation);
    if(db.queryForObject("SELECT COUNT(*) FROM eam_ai_exchange WHERE tenant_id=? AND conversation_id=? AND status='RUNNING'",Integer.class,tenant,conversation)>0)
      throw new IllegalStateException("此对话正在回答，请稍后刷新");
    db.update("INSERT INTO eam_ai_exchange(id,tenant_id,conversation_id,request_key,question,status,lease_until) VALUES(?,?,?,?,?,'RUNNING',DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 3 MINUTE))",id,tenant,conversation,key,question);
    String title=question.substring(0,question.offsetByCodePoints(0,Math.min(100,question.codePointCount(0,question.length()))));
    db.update("UPDATE eam_ai_conversation SET title=CASE WHEN title='新对话' THEN ? ELSE title END,updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND id=?",title,tenant,conversation);
  }
  public void finish(long tenant,long exchange,String answer,List<Long> citations){
    if(db.update("UPDATE eam_ai_exchange SET answer=?,status='SUCCEEDED',completed_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND id=? AND status='RUNNING' AND lease_until>CURRENT_TIMESTAMP(6)",answer,tenant,exchange)!=1)throw new IllegalStateException("问答已超时，请刷新会话");
    for(int i=0;i<citations.size();i++)db.update("INSERT INTO eam_ai_exchange_citation(tenant_id,exchange_id,chunk_id,sort_no) VALUES(?,?,?,?)",tenant,exchange,citations.get(i),i);
  }
  public void fail(long tenant,long exchange){db.update("UPDATE eam_ai_exchange SET status='FAILED',completed_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND id=? AND status='RUNNING'",tenant,exchange);}
}
