package com.windasoft.eam.repository.knowledge;
import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;
@Repository
public class KnowledgeJobRepository {
  private final JdbcTemplate db;public KnowledgeJobRepository(JdbcTemplate db){this.db=db;}
  public List<Map<String,Object>> parsing(){return db.queryForList("SELECT tenant_id,id,document_id,created_by,file_key FROM eam_knowledge_version WHERE created_by IS NOT NULL AND parse_attempts<3 AND (parse_status='PENDING' OR (parse_status='RUNNING' AND parse_lease_until<CURRENT_TIMESTAMP(6))) ORDER BY id LIMIT 3");}
  public int claim(long tenant,long version){
    if(db.update("UPDATE eam_knowledge_version SET parse_status='RUNNING',parse_attempts=parse_attempts+1,parse_lease_until=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 5 MINUTE),error_message=NULL WHERE tenant_id=? AND id=? AND parse_attempts<3 AND (parse_status='PENDING' OR (parse_status='RUNNING' AND parse_lease_until<CURRENT_TIMESTAMP(6)))",tenant,version)!=1)return -1;
    return db.queryForObject("SELECT parse_attempts FROM eam_knowledge_version WHERE tenant_id=? AND id=?",Integer.class,tenant,version);
  }
  public void heartbeat(long tenant,long version,int attempt){if(db.update("UPDATE eam_knowledge_version SET parse_lease_until=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 5 MINUTE) WHERE tenant_id=? AND id=? AND parse_status='RUNNING' AND parse_attempts=? AND parse_lease_until>CURRENT_TIMESTAMP(6)",tenant,version,attempt)!=1)throw new IllegalStateException("解析任务租约已失效");}
  public void failed(long tenant,long version,int attempt){db.update("UPDATE eam_knowledge_version SET parse_status='FAILED',error_message='解析失败，请检查PDF、OCR配置和权限后重试',parse_lease_until=NULL WHERE tenant_id=? AND id=? AND parse_attempts=? AND parse_status='RUNNING'",tenant,version,attempt);}
  public void failExhausted(){db.update("UPDATE eam_knowledge_version SET parse_status='FAILED',error_message='解析重试达到上限，请检查后手工重试' WHERE parse_status='RUNNING' AND parse_attempts>=3 AND parse_lease_until<CURRENT_TIMESTAMP(6)");db.update("UPDATE eam_knowledge_index_job SET status='FAILED',error_message='索引重试达到上限，请检查后手工重试' WHERE status='RUNNING' AND attempts>=3 AND lease_until<CURRENT_TIMESTAMP(6)");}
  public List<Map<String,Object>> indexing(){return db.queryForList("SELECT j.*,v.document_id FROM eam_knowledge_index_job j JOIN eam_knowledge_version v ON v.tenant_id=j.tenant_id AND v.id=j.version_id WHERE j.created_by IS NOT NULL AND j.attempts<3 AND (j.status='PENDING' OR (j.status='RUNNING' AND j.lease_until<CURRENT_TIMESTAMP(6))) ORDER BY j.id LIMIT 3");}
  public void queue(long id,long tenant,long user,long version,String model,long rowVersion){
    db.update("INSERT IGNORE INTO eam_knowledge_index_job(id,tenant_id,version_id,embedding_model,next_attempt_at,created_by,document_row_version) VALUES(?,?,?,?,CURRENT_TIMESTAMP(6),?,?)",id,tenant,version,model,user,rowVersion);
    db.update("UPDATE eam_knowledge_index_job SET status='PENDING',attempts=0,created_by=?,document_row_version=?,next_attempt_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND version_id=? AND embedding_model=? AND status='FAILED'",user,rowVersion,tenant,version,model);
  }
  public void failIndex(long tenant,long id){db.update("UPDATE eam_knowledge_index_job SET status='FAILED',error_message='索引失败，请检查配置、权限和文档版本' WHERE tenant_id=? AND id=? AND (status='PENDING' OR (status='RUNNING' AND lease_until<CURRENT_TIMESTAMP(6)))",tenant,id);}
}
