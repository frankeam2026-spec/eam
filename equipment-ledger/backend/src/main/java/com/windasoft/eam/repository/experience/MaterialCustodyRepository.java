package com.windasoft.eam.repository.experience;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MaterialCustodyRepository {
  private final JdbcTemplate db;
  public static final String SELECT =
      "SELECT CAST(a.id AS CHAR)"
          + " id,a.quantity,a.used_quantity,a.returned_quantity,(a.quantity-a.used_quantity-a.returned_quantity)"
          + " remaining,m.code materialCode,m.name materialName,m.model specification,m.unit,w.name"
          + " warehouse,i.warehouse_id warehouseId,w.company_id companyId,i.code"
          + " issueCode,CAST(i.id AS CHAR)"
          + " issueId,COALESCE(c.holder_id,ol.recipient_id,r.applicant_id)"
          + " holderId,COALESCE(c.holder_name,ol.recipient_name,r.applicant_name,'')"
          + " holderName,COALESCE(ol.department_id,NULL)"
          + " departmentId,COALESCE(ol.task_kind,r.task_kind,'')"
          + " taskKind,COALESCE(ol.task_id,r.task_id) taskId,COALESCE(ol.task_code,r.task_code,'')"
          + " taskCode,COALESCE(c.remark,'') remark,COALESCE(c.row_version,-1) rowVersion FROM"
          + " inv_issue_allocation a JOIN inv_issue i ON i.tenant_id=a.tenant_id AND"
          + " i.id=a.issue_id JOIN inv_balance s ON s.tenant_id=a.tenant_id AND s.id=a.balance_id"
          + " JOIN inv_material m ON m.tenant_id=a.tenant_id AND m.id=s.material_id JOIN"
          + " inv_warehouse w ON w.tenant_id=i.tenant_id AND w.id=i.warehouse_id LEFT JOIN"
          + " inv_outbound_line ol ON ol.tenant_id=a.tenant_id AND ol.id=a.outbound_line_id LEFT"
          + " JOIN inv_requisition_line rl ON rl.tenant_id=a.tenant_id AND"
          + " rl.id=a.requisition_line_id LEFT JOIN inv_requisition r ON r.tenant_id=rl.tenant_id"
          + " AND r.id=rl.requisition_id LEFT JOIN inv_material_custody c ON"
          + " c.tenant_id=a.tenant_id AND c.allocation_id=a.id WHERE a.tenant_id=? AND i.reversed=0"
          + " AND (a.quantity-a.used_quantity-a.returned_quantity)>0";

  public MaterialCustodyRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> listRemaining(long tenantId) {
    return db.queryForList(SELECT + " ORDER BY i.created_at DESC,a.id", tenantId);
  }

  public List<Map<String, Object>> lockAllocation(long tenantId, Object allocation) {
    return db.queryForList(SELECT + " AND a.id=? FOR UPDATE", tenantId, allocation);
  }

  public List<Map<String, Object>> findHolder(long tenantId, Object holder) {
    return db.queryForList(
        "SELECT display_name FROM eam_sys_user WHERE tenant_id=? AND id=? AND status=1 AND"
            + " deleted=0",
        tenantId,
        holder);
  }

  public int saveCustody(
      long tenantId,
      Object allocation,
      Object holder,
      Object displayName,
      Object remark,
      long userId) {
    return db.update(
        "INSERT INTO"
            + " inv_material_custody(tenant_id,allocation_id,holder_id,holder_name,remark,actor_id)"
            + " VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE"
            + " remark=VALUES(remark),row_version=row_version+1,actor_id=VALUES(actor_id),updated_at=NOW(3)",
        tenantId,
        allocation,
        holder,
        displayName,
        remark,
        userId);
  }

  public int insertEvent(
      long recordId, long tenantId, Object allocation, long userId, Object value, String payload) {
    return db.update(
        "INSERT INTO"
            + " eam_experience_event(id,tenant_id,object_kind,object_id,action,actor_id,actor_name,details)"
            + " VALUES(?,?,'custody',?,'登记余料暂存',?,?,?)",
        recordId,
        tenantId,
        allocation,
        userId,
        value,
        payload);
  }

  public List<Map<String, Object>> lockTaskAllocations(long tenantId, Object kind, Object task) {
    return db.queryForList(
        SELECT
            + " AND COALESCE(ol.task_kind,r.task_kind)=? AND COALESCE(ol.task_id,r.task_id)=?"
            + " ORDER BY a.id FOR UPDATE",
        tenantId,
        kind,
        task);
  }
}
