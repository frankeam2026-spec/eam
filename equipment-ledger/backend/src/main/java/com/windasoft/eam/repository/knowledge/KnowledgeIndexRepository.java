package com.windasoft.eam.repository.knowledge;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeIndexRepository {
  private final JdbcTemplate db;
  public KnowledgeIndexRepository(JdbcTemplate db){this.db=db;}
  public Map<String,Object> latest(long tenant,long document){
    var rows=db.queryForList("SELECT * FROM eam_knowledge_version WHERE tenant_id=? AND document_id=? ORDER BY version_no DESC LIMIT 1",tenant,document);
    return rows.isEmpty()?null:rows.getFirst();
  }
  public int claim(long id,long tenant,long version,String model){
    db.update("INSERT IGNORE INTO eam_knowledge_index_job(id,tenant_id,version_id,embedding_model,next_attempt_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP(6))",id,tenant,version,model);
    int changed=db.update("UPDATE eam_knowledge_index_job SET status='RUNNING',attempts=attempts+1,lease_until=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 5 MINUTE),error_message=NULL WHERE tenant_id=? AND version_id=? AND embedding_model=? AND (status IN ('PENDING','FAILED') OR (status='RUNNING' AND lease_until<CURRENT_TIMESTAMP(6)))",tenant,version,model);
    if(changed!=1)throw new IllegalStateException("索引已完成或正在建立，请刷新状态");
    return db.queryForObject("SELECT attempts FROM eam_knowledge_index_job WHERE tenant_id=? AND version_id=? AND embedding_model=?",Integer.class,tenant,version,model);
  }
  public List<Map<String,Object>> chunks(long tenant,long version,long after){
    return db.queryForList("SELECT c.id,c.text_content FROM eam_knowledge_chunk c JOIN eam_knowledge_page p ON p.tenant_id=c.tenant_id AND p.id=c.page_id WHERE c.tenant_id=? AND p.version_id=? AND c.id>? ORDER BY c.id LIMIT 10",tenant,version,after);
  }
  public void heartbeat(long tenant,long version,String model,int attempt){
    if(db.update("UPDATE eam_knowledge_index_job SET lease_until=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 5 MINUTE) WHERE tenant_id=? AND version_id=? AND embedding_model=? AND attempts=? AND status='RUNNING' AND lease_until>CURRENT_TIMESTAMP(6)",tenant,version,model,attempt)!=1)
      throw new IllegalStateException("索引任务租约已失效");
  }
  public void finish(long tenant,long version,String model,int attempt,boolean success){
    if(db.update("UPDATE eam_knowledge_index_job SET status=?,lease_until=NULL,error_message=? WHERE tenant_id=? AND version_id=? AND embedding_model=? AND attempts=? AND status='RUNNING'",success?"READY":"FAILED",success?null:"索引建立失败，请检查AI与Qdrant配置后重试",tenant,version,model,attempt)!=1)
      throw new IllegalStateException("索引任务状态已改变");
  }
  public boolean ready(long tenant,long version,String model){
    return db.queryForObject("SELECT COUNT(*) FROM eam_knowledge_index_job WHERE tenant_id=? AND version_id=? AND embedding_model=? AND status='READY'",Integer.class,tenant,version,model)==1;
  }
  public boolean publish(long tenant,long document,long version,long expected){
    return db.update("UPDATE eam_knowledge_document SET status='PUBLISHED',published_version_id=?,row_version=row_version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND id=? AND row_version=? AND EXISTS(SELECT 1 FROM eam_knowledge_version v WHERE v.tenant_id=? AND v.id=? AND v.document_id=? AND v.parse_status='READY')",version,tenant,document,expected,tenant,version,document)==1;
  }
}
