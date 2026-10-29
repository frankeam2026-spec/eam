package com.windasoft.eam.repository.knowledge;

import com.windasoft.eam.dto.system.PermissionScope;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeSearchRepository {
  private final JdbcTemplate db;
  public KnowledgeSearchRepository(JdbcTemplate db){this.db=db;}
  private String scope(PermissionScope scope,List<Object> args){
    if(scope.all())return "";
    if(scope.companyIds().isEmpty())return " AND 1=0";
    args.addAll(scope.companyIds());
    return " AND d.company_id IN ("+String.join(",",Collections.nCopies(scope.companyIds().size(),"?"))+")";
  }
  public List<Long> versions(long tenant,PermissionScope scope,String model){
    return versions(tenant,scope,model,null);
  }
  public List<Long> versions(long tenant,PermissionScope scope,String model,Long equipment){
    var args=new ArrayList<Object>();args.add(tenant);args.add(model);
    var rows=db.queryForList("SELECT d.published_version_id FROM eam_knowledge_document d"
        +" JOIN eam_knowledge_version v ON v.tenant_id=d.tenant_id AND v.id=d.published_version_id AND v.document_id=d.id"
        +" JOIN eam_knowledge_index_job j ON j.tenant_id=v.tenant_id AND j.version_id=v.id"
        +" WHERE d.tenant_id=? AND j.embedding_model=? AND j.status='READY' AND d.status='PUBLISHED' AND v.parse_status='READY'"
        +scope(scope,args)+applicability(equipment,args)+" ORDER BY d.id LIMIT 1001",Long.class,args.toArray());
    if(rows.size()>1000)throw new IllegalStateException("当前检索范围过大，请先缩小知识库范围");
    return rows;
  }
  private String applicability(Long equipment,List<Object> args){if(equipment==null)return " AND d.applies_to_all=TRUE";args.add(equipment);args.add(equipment);return " AND d.company_id=(SELECT e.company_org_id FROM eam_equipment e WHERE e.tenant_id=d.tenant_id AND e.id=?) AND (d.applies_to_all=TRUE OR EXISTS(SELECT 1 FROM eam_knowledge_equipment a WHERE a.tenant_id=d.tenant_id AND a.document_id=d.id AND a.equipment_id=?))";}
  public List<Map<String,Object>> chunks(long tenant,PermissionScope scope,List<Long> ids){
    if(ids.isEmpty())return List.of();
    var args=new ArrayList<Object>();args.add(tenant);args.addAll(ids);
    return db.queryForList("SELECT CAST(c.id AS CHAR) chunkId,CAST(d.id AS CHAR) documentId,CAST(v.id AS CHAR) versionId,d.title,p.page_no pageNo,c.text_content excerpt,v.has_original hasOriginal,"
        +"(SELECT CAST(cs.repair_order_id AS CHAR) FROM eam_knowledge_case_source cs WHERE cs.tenant_id=d.tenant_id AND cs.document_id=d.id) repairOrderId"
        +" FROM eam_knowledge_chunk c JOIN eam_knowledge_page p ON p.tenant_id=c.tenant_id AND p.id=c.page_id"
        +" JOIN eam_knowledge_version v ON v.tenant_id=p.tenant_id AND v.id=p.version_id"
        +" JOIN eam_knowledge_document d ON d.tenant_id=v.tenant_id AND d.id=v.document_id AND d.published_version_id=v.id"
        +" WHERE c.tenant_id=? AND c.id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") AND d.status='PUBLISHED' AND v.parse_status='READY'"
        +scope(scope,args),args.toArray());
  }
  public List<Long> keyword(long tenant,List<Long> versions,List<String> terms){
    if(versions.isEmpty()||terms.isEmpty())return List.of();
    var args=new ArrayList<Object>();args.add(tenant);args.addAll(versions);
    String expressions=String.join(" OR ",Collections.nCopies(terms.size(),"LOCATE(?,c.text_content)>0"));args.addAll(terms);
    return db.queryForList("SELECT c.id FROM eam_knowledge_chunk c JOIN eam_knowledge_page p ON p.tenant_id=c.tenant_id AND p.id=c.page_id WHERE c.tenant_id=? AND p.version_id IN ("+String.join(",",Collections.nCopies(versions.size(),"?"))+") AND ("+expressions+") ORDER BY c.id LIMIT 12",Long.class,args.toArray());
  }
}
