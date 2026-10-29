package com.windasoft.eam.repository.knowledge;

import com.windasoft.eam.dto.system.PermissionScope;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeDocumentRepository {
  private final JdbcTemplate db;
  public KnowledgeDocumentRepository(JdbcTemplate db) { this.db = db; }

  public Map<String,Object> list(long tenant, PermissionScope scope, String query, int page, int size) {
    var args = new ArrayList<Object>(); args.add(tenant);
    String where = " WHERE d.tenant_id=?";
    if (!scope.all()) {
      if (scope.companyIds().isEmpty()) where += " AND 1=0";
      else {
        where += " AND d.company_id IN (" + String.join(",", Collections.nCopies(scope.companyIds().size(), "?")) + ")";
        args.addAll(scope.companyIds());
      }
    }
    if (!query.isBlank()) { where += " AND LOCATE(?,d.title)>0"; args.add(query); }
    Long total = db.queryForObject("SELECT COUNT(*) FROM eam_knowledge_document d" + where, Long.class, args.toArray());
    args.add(size); args.add((page - 1L) * size);
    return Map.of("total", total == null ? 0 : total, "data", db.queryForList(
        "SELECT CAST(d.id AS CHAR) id,CAST(d.company_id AS CHAR) companyId,d.title,d.document_type documentType,"
        + "d.status,d.row_version rowVersion,d.updated_at updatedAt,"
        + "(SELECT v.parse_status FROM eam_knowledge_version v WHERE v.tenant_id=d.tenant_id AND v.document_id=d.id ORDER BY v.version_no DESC LIMIT 1) parseStatus,"
        + "(SELECT j.status FROM eam_knowledge_index_job j JOIN eam_knowledge_version v ON v.tenant_id=j.tenant_id AND v.id=j.version_id WHERE v.tenant_id=d.tenant_id AND v.document_id=d.id ORDER BY j.id DESC LIMIT 1) indexStatus"
        + " FROM eam_knowledge_document d" + where + " ORDER BY d.updated_at DESC,d.id DESC LIMIT ? OFFSET ?", args.toArray()));
  }

  public Object versions(long tenant,long document,int page,int size){
    Long total=db.queryForObject("SELECT COUNT(*) FROM eam_knowledge_version WHERE tenant_id=? AND document_id=?",Long.class,tenant,document);
    var rows=db.queryForList("SELECT CAST(v.id AS CHAR) id,v.version_no versionNo,v.parse_status parseStatus,v.parser_version parserVersion,v.error_message errorMessage,v.created_at createdAt,(d.published_version_id=v.id) published FROM eam_knowledge_version v JOIN eam_knowledge_document d ON d.tenant_id=v.tenant_id AND d.id=v.document_id WHERE v.tenant_id=? AND v.document_id=? ORDER BY v.version_no DESC LIMIT ? OFFSET ?",tenant,document,size,(page-1L)*size);
    return Map.of("data",rows,"total",total==null?0:total);
  }
  public void retryParse(long tenant,long document,long expected,long user){
    if(db.update("UPDATE eam_knowledge_document SET row_version=row_version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND id=? AND row_version=?",tenant,document,expected)!=1)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"资料已变更，请刷新");
    Long version=db.queryForObject("SELECT id FROM eam_knowledge_version WHERE tenant_id=? AND document_id=? ORDER BY version_no DESC LIMIT 1",Long.class,tenant,document);
    if(db.update("UPDATE eam_knowledge_version SET parse_status='PENDING',parse_attempts=0,parse_lease_until=NULL,error_message=NULL,created_by=? WHERE tenant_id=? AND id=? AND parse_status='FAILED'",user,tenant,version)!=1)throw new IllegalArgumentException("仅失败的解析任务可以重试");
  }
  public Map<String,Object> document(long tenant, long id) {
    var rows = db.queryForList("SELECT * FROM eam_knowledge_document WHERE tenant_id=? AND id=?", tenant,id);
    return rows.isEmpty() ? null : rows.getFirst();
  }
  public Map<String,Object> publishedFile(long tenant,long document,long version){
    var rows=db.queryForList("SELECT v.file_key FROM eam_knowledge_version v JOIN eam_knowledge_document d ON d.tenant_id=v.tenant_id AND d.id=v.document_id AND d.published_version_id=v.id WHERE v.tenant_id=? AND d.id=? AND v.id=? AND d.status='PUBLISHED' AND v.has_original=TRUE",tenant,document,version);
    return rows.isEmpty()?null:rows.getFirst();
  }

  public boolean companyExists(long tenant, long company) {
    return db.queryForObject("SELECT COUNT(*) FROM eam_org_unit WHERE tenant_id=? AND id=? AND org_type='公司' AND status=1 AND deleted=0", Integer.class, tenant,company) == 1;
  }

  public List<Map<String,Object>> companies(long tenant, PermissionScope scope) {
    var args=new ArrayList<Object>(); args.add(tenant);
    String where="";
    if(!scope.all()) {
      if(scope.companyIds().isEmpty()) return List.of();
      where=" AND id IN ("+String.join(",",Collections.nCopies(scope.companyIds().size(),"?"))+")";
      args.addAll(scope.companyIds());
    }
    return db.queryForList("SELECT CAST(id AS CHAR) id,name FROM eam_org_unit WHERE tenant_id=? AND org_type='公司' AND status=1 AND deleted=0"+where+" ORDER BY name,id",args.toArray());
  }

  public void create(long tenant,long user,long id,long version,long company,String title,String key,String digest,String parserVersion) {
    db.update("INSERT INTO eam_knowledge_document(id,tenant_id,company_id,title,document_type,created_by) VALUES(?,?,?,?,'MANUAL',?)",id,tenant,company,title,user);
    db.update("INSERT INTO eam_knowledge_version(id,tenant_id,document_id,version_no,file_key,sha256,parser_version,parse_status) VALUES(?,?,?,1,?,?,?,'RUNNING')",version,tenant,id,key,digest,parserVersion);
  }
  public void pending(long tenant,long version,long user){db.update("UPDATE eam_knowledge_version SET parse_status='PENDING',created_by=? WHERE tenant_id=? AND id=?",user,tenant,version);}
  public boolean disable(long tenant,long id,long expected){return db.update("UPDATE eam_knowledge_document SET status='DISABLED',row_version=row_version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND id=? AND row_version=?",tenant,id,expected)==1;}
  public void addVersion(long tenant,long document,long id,long user,long expected,String file,String digest,String parserVersion){
    var old=db.queryForMap("SELECT row_version FROM eam_knowledge_document WHERE tenant_id=? AND id=? FOR UPDATE",tenant,document);
    if(((Number)old.get("row_version")).longValue()!=expected)throw new IllegalStateException("文档已变更，请刷新");
    Integer next=db.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM eam_knowledge_version WHERE tenant_id=? AND document_id=?",Integer.class,tenant,document);
    db.update("INSERT INTO eam_knowledge_version(id,tenant_id,document_id,version_no,file_key,sha256,parser_version,parse_status,created_by) VALUES(?,?,?,?,?,?,?,'PENDING',?)",id,tenant,document,next,file,digest,parserVersion,user);
    db.update("UPDATE eam_knowledge_document SET row_version=row_version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND id=?",tenant,document);
  }
  public void type(long tenant,long id,String type){db.update("UPDATE eam_knowledge_document SET document_type=? WHERE tenant_id=? AND id=?",type,tenant,id);}
  public Object applicability(long tenant,long id){return db.queryForList("SELECT CAST(equipment_id AS CHAR) equipmentId FROM eam_knowledge_equipment WHERE tenant_id=? AND document_id=?",tenant,id);}
  public void bind(long tenant,long id,long expected,boolean all,List<Long> equipment){
    if(db.update("UPDATE eam_knowledge_document SET applies_to_all=?,status='DRAFT',published_version_id=NULL,row_version=row_version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND id=? AND row_version=?",all,tenant,id,expected)!=1)throw new IllegalStateException("文档已变更，请刷新");
    db.update("DELETE FROM eam_knowledge_equipment WHERE tenant_id=? AND document_id=?",tenant,id);
    for(long eq:equipment)db.update("INSERT INTO eam_knowledge_equipment(tenant_id,document_id,equipment_id) VALUES(?,?,?)",tenant,id,eq);
  }
  public int equipmentCount(long tenant,long id){return db.queryForObject("SELECT COUNT(*) FROM eam_knowledge_equipment WHERE tenant_id=? AND document_id=?",Integer.class,tenant,id);}
  public void caseSource(long tenant,long id,long version,long repair,long repairVersion){
    type(tenant,id,"CASE");db.update("UPDATE eam_knowledge_version SET has_original=FALSE WHERE tenant_id=? AND id=?",tenant,version);
    db.update("INSERT INTO eam_knowledge_case_source(tenant_id,document_id,repair_order_id,repair_row_version) VALUES(?,?,?,?)",tenant,id,repair,repairVersion);
  }

  public List<Map<String,Object>> pages(long tenant,long document) {
    return db.queryForList("SELECT p.page_no pageNo,p.needs_ocr needsOcr,p.text_content textContent,"
        + "(SELECT COUNT(*) FROM eam_knowledge_chunk c WHERE c.tenant_id=p.tenant_id AND c.page_id=p.id) chunkCount"
        + " FROM eam_knowledge_page p JOIN eam_knowledge_version v ON v.tenant_id=p.tenant_id AND v.id=p.version_id"
        + " WHERE v.tenant_id=? AND v.document_id=? AND v.version_no=(SELECT MAX(v2.version_no) FROM eam_knowledge_version v2 WHERE v2.tenant_id=v.tenant_id AND v2.document_id=v.document_id) ORDER BY p.page_no",tenant,document);
  }
}
