package com.windasoft.eam.repository.printing;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentPrintRepository {
  private final JdbcTemplate db;

  public DocumentPrintRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> templates(long tenant) {
    return db.queryForList(
        "SELECT t.*,o.name company_name FROM eam_print_template t LEFT JOIN eam_org_unit o ON"
            + " o.tenant_id=t.tenant_id AND o.id=t.company_id WHERE t.tenant_id=? AND t.deleted=0"
            + " ORDER BY t.is_default DESC,t.updated_at DESC,t.id DESC",
        tenant);
  }

  public Map<String, Object> template(long tenant, long id) {
    var rows =
        db.queryForList(
            "SELECT * FROM eam_print_template WHERE tenant_id=? AND id=? AND deleted=0",
            tenant,
            id);
    if (rows.isEmpty()) throw new IllegalArgumentException("打印模板不存在");
    return rows.getFirst();
  }

  public List<Map<String, Object>> companies(long tenant) {
    return db.queryForList(
        "SELECT id,name FROM eam_org_unit WHERE tenant_id=? AND org_type IN ('公司','集团') AND"
            + " status=1 AND deleted=0 ORDER BY id",
        tenant);
  }

  public Map<String, Object> source(long tenant, String reference) {
    boolean legacy = reference.startsWith("g");
    String value = legacy ? reference.substring(1) : reference;
    if (!value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("出库单编号无效");
    var rows =
        db.queryForList(
            "SELECT i.id,i.warehouse_id,w.company_id,o.name company_name FROM "
                + (legacy ? "inv_issue_group" : "inv_issue")
                + " i JOIN inv_warehouse w ON w.tenant_id=i.tenant_id AND w.id=i.warehouse_id LEFT"
                + " JOIN eam_org_unit o ON o.tenant_id=w.tenant_id AND o.id=w.company_id WHERE"
                + " i.tenant_id=? AND i.id=?"
                + (legacy ? "" : " AND i.deleted=0"),
            tenant,
            Long.parseLong(value));
    if (rows.isEmpty()) throw new IllegalArgumentException("出库单不存在");
    return rows.getFirst();
  }

  public List<Map<String, Object>> allocations(long tenant, String reference) {
    boolean group = reference.startsWith("g");
    return db.queryForList(
        """
SELECT a.id,a.quantity,a.unit_cost,a.amount,a.outbound_line_id,
 m.code,m.name,m.model,m.unit,COALESCE(pc.code,'') batch,COALESCE(b.name,'') bin,
 COALESCE(l.recipient_name,r.applicant_name,'') recipient,COALESCE(d.name,'') department,
 COALESCE(l.task_code,r.task_code,'') task,COALESCE(r.code,'') requisition,
 COALESCE(c.currency,'未标注币种') currency
FROM inv_issue_allocation a JOIN inv_issue i ON i.tenant_id=a.tenant_id AND i.id=a.issue_id
 JOIN inv_balance s ON s.tenant_id=a.tenant_id AND s.id=a.balance_id
 JOIN inv_material m ON m.tenant_id=s.tenant_id AND m.id=s.material_id
 LEFT JOIN inv_bin b ON b.tenant_id=s.tenant_id AND b.id=s.bin_id
 LEFT JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id
 LEFT JOIN inv_cost_layer c ON c.tenant_id=a.tenant_id AND c.id=a.cost_layer_id
 LEFT JOIN inv_outbound_line l ON l.tenant_id=a.tenant_id AND l.id=a.outbound_line_id
 LEFT JOIN inv_requisition_line ql ON ql.tenant_id=a.tenant_id AND ql.id=a.requisition_line_id
 LEFT JOIN inv_requisition r ON r.tenant_id=a.tenant_id AND r.id=COALESCE(l.requisition_id,ql.requisition_id)
 LEFT JOIN eam_org_unit d ON d.tenant_id=l.tenant_id AND d.id=l.department_id
WHERE a.tenant_id=? AND
"""
            + (group ? " i.group_id=?" : " i.id=?")
            + " ORDER BY i.id,COALESCE(l.line_no,0),a.id",
        tenant,
        Long.parseLong(group ? reference.substring(1) : reference));
  }

  public void clearDefaults(long tenant, Long company, Long except) {
    db.update(
        "UPDATE eam_print_template SET is_default=0,row_version=row_version+1 WHERE tenant_id=? AND"
            + " company_id <=> ? AND deleted=0 AND is_default=1 AND id<>?",
        tenant,
        company,
        except);
  }

  public void insertTemplate(
      long id,
      long tenant,
      String name,
      Long company,
      boolean enabled,
      boolean def,
      String config) {
    db.update(
        "INSERT INTO"
            + " eam_print_template(id,tenant_id,name,company_id,enabled,is_default,config_json)"
            + " VALUES(?,?,?,?,?,?,CAST(? AS JSON))",
        id,
        tenant,
        name,
        company,
        enabled,
        def,
        config);
  }

  public int updateTemplate(
      long tenant,
      long id,
      String name,
      Long company,
      boolean enabled,
      boolean def,
      String config,
      int version) {
    return db.update(
        "UPDATE eam_print_template SET"
            + " name=?,company_id=?,enabled=?,is_default=?,config_json=CAST(? AS"
            + " JSON),row_version=row_version+1 WHERE tenant_id=? AND id=? AND row_version=? AND"
            + " deleted=0",
        name,
        company,
        enabled,
        def,
        config,
        tenant,
        id,
        version);
  }

  public int deleteTemplate(long tenant, long id, int version) {
    return db.update(
        "UPDATE eam_print_template SET deleted=1,is_default=0,row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=? AND row_version=? AND deleted=0",
        tenant,
        id,
        version);
  }

  public Map<String, Object> job(long tenant, long id, boolean file) {
    var rows =
        db.queryForList(
            "SELECT "
                + jobColumns()
                + (file ? ",pdf_content" : "")
                + " FROM eam_print_job WHERE tenant_id=? AND id=?",
            tenant,
            id);
    if (rows.isEmpty()) throw new IllegalArgumentException("打印记录不存在");
    return rows.getFirst();
  }

  public Map<String, Object> lockedJob(long tenant, long id) {
    return db.queryForMap(
        "SELECT " + jobColumns() + " FROM eam_print_job WHERE tenant_id=? AND id=? FOR UPDATE",
        tenant,
        id);
  }

  private String jobColumns() {
    return "id,company_id,warehouse_id,document_id,document_code,document_status,template_id,template_name,template_version,contains_money,actor_name,created_at,page_count,pdf_sha256";
  }

  public List<Map<String, Object>> jobs(long tenant, String document) {
    return db.queryForList(
        "SELECT "
            + jobColumns()
            + " FROM eam_print_job WHERE tenant_id=? AND document_id=? ORDER BY created_at DESC,id"
            + " DESC LIMIT 100",
        tenant,
        document);
  }

  public Map<String, Object> retry(long tenant, long actor, String key, boolean lock) {
    var rows =
        db.queryForList(
            "SELECT id,request_hash FROM eam_print_job WHERE tenant_id=? AND actor_id=? AND"
                + " request_key=?"
                + (lock ? " FOR UPDATE" : ""),
            tenant,
            actor,
            key);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public void insertJob(
      long id,
      long tenant,
      long company,
      long warehouse,
      String document,
      String code,
      String status,
      Long template,
      String name,
      int version,
      String config,
      String data,
      boolean money,
      long actor,
      String actorName,
      String key,
      String hash,
      String pdfHash,
      int pages,
      byte[] pdf) {
    db.update(
        "INSERT INTO"
            + " eam_print_job(id,tenant_id,company_id,warehouse_id,document_id,document_code,document_status,template_id,template_name,template_version,template_snapshot,document_snapshot,contains_money,actor_id,actor_name,request_key,request_hash,pdf_sha256,page_count,pdf_content)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON),?,?,?,?,?,?,?,?) ON"
            + " DUPLICATE KEY UPDATE id=id",
        id,
        tenant,
        company,
        warehouse,
        document,
        code,
        status,
        template,
        name,
        version,
        config,
        data,
        money,
        actor,
        actorName,
        key,
        hash,
        pdfHash,
        pages,
        pdf);
  }
}
