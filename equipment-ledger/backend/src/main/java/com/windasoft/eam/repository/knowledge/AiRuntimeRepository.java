package com.windasoft.eam.repository.knowledge;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AiRuntimeRepository {
  private final JdbcTemplate db;
  public AiRuntimeRepository(JdbcTemplate db){this.db=db;}
  public Map<String,Object> settings(long tenant,boolean lock){
    db.update("INSERT IGNORE INTO eam_ai_setting(tenant_id) VALUES(?)",tenant);
    return db.queryForMap("SELECT * FROM eam_ai_setting WHERE tenant_id=?"+(lock?" FOR UPDATE":""),tenant);
  }
  public void save(long tenant,Map<String,Object> c,String secret){
    if(db.update("UPDATE eam_ai_setting SET enabled=?,base_url=?,api_secret=?,chat_model=?,embedding_model=?,monthly_limit=?,warning_percent=?,input_price=?,output_price=?,embedding_price=?,vision_model=?,audio_model=?,vision_input_price=?,vision_output_price=?,audio_minute_price=?,row_version=row_version+1 WHERE tenant_id=? AND row_version=?",
        c.get("enabled"),c.get("baseUrl"),secret,c.get("chatModel"),c.get("embeddingModel"),c.get("monthlyLimit"),c.get("warningPercent"),c.get("inputPrice"),c.get("outputPrice"),c.get("embeddingPrice"),c.get("visionModel"),c.get("audioModel"),c.get("visionInputPrice"),c.get("visionOutputPrice"),c.get("audioMinutePrice"),tenant,c.get("rowVersion"))!=1)throw new IllegalStateException("AI设置已变更，请刷新");
  }
  public Map<String,Object> usage(long tenant,String month,boolean lock){
    db.update("INSERT IGNORE INTO eam_ai_monthly_usage(tenant_id,billing_month) VALUES(?,?)",tenant,month);
    return db.queryForMap("SELECT * FROM eam_ai_monthly_usage WHERE tenant_id=? AND billing_month=?"+(lock?" FOR UPDATE":""),tenant,month);
  }
  public void reserve(long id,long tenant,long user,String month,String purpose,String model,BigDecimal amount){
    db.update("UPDATE eam_ai_monthly_usage SET reserved_amount=reserved_amount+? WHERE tenant_id=? AND billing_month=?",amount,tenant,month);
    db.update("INSERT INTO eam_ai_call(id,tenant_id,user_id,billing_month,purpose,model_name,status,reserved_amount) VALUES(?,?,?,?,?,?,'RUNNING',?)",id,tenant,user,month,purpose,model,amount);
  }
  public void finish(long id,long tenant,String month,BigDecimal reserved,BigDecimal cost,Long input,Long output,long elapsed,boolean success){
    if(db.update("UPDATE eam_ai_call SET status=?,estimated_cost=?,input_tokens=?,output_tokens=?,elapsed_ms=?,completed_at=CURRENT_TIMESTAMP(6) WHERE id=? AND tenant_id=? AND status='RUNNING'",success?"SUCCEEDED":"UNKNOWN",cost,input,output,elapsed,id,tenant)==1)
      db.update("UPDATE eam_ai_monthly_usage SET reserved_amount=reserved_amount-?,estimated_spent=estimated_spent+? WHERE tenant_id=? AND billing_month=?",reserved,cost,tenant,month);
  }
  public Object calls(long tenant,int page,int size){
    return Map.of("total",db.queryForObject("SELECT COUNT(*) FROM eam_ai_call WHERE tenant_id=?",Long.class,tenant),"data",db.queryForList("SELECT CAST(id AS CHAR) id,purpose,model_name modelName,status,input_tokens inputTokens,output_tokens outputTokens,estimated_cost estimatedCost,elapsed_ms elapsedMs,created_at createdAt FROM eam_ai_call WHERE tenant_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",tenant,size,(page-1L)*size));
  }
  public void recoverExpired(long tenant){
    var rows=db.queryForList("SELECT id,billing_month,reserved_amount FROM eam_ai_call WHERE tenant_id=? AND status='RUNNING' AND created_at<DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 10 MINUTE) ORDER BY id LIMIT 100",tenant);
    for(var row:rows){var amount=new BigDecimal(row.get("reserved_amount").toString());finish(((Number)row.get("id")).longValue(),tenant,row.get("billing_month").toString(),amount,amount,null,null,600000,false);}
  }
}
