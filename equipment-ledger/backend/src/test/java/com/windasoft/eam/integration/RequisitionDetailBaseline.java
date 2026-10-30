package com.windasoft.eam.integration;

import com.windasoft.eam.common.inventory.InventoryMoney;
import com.windasoft.eam.service.inventory.InventoryViewAssembler;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** Frozen pre-refactor detail queries: a wire-compatibility oracle, never a production adapter. */
final class RequisitionDetailBaseline {
  private final JdbcTemplate db;

  RequisitionDetailBaseline(JdbcTemplate db) {
    this.db = db;
  }

  Map<String, Object> read(long tenant, long id, boolean money) {
    var views = new InventoryViewAssembler();
    var result =
        views.view(
            db.queryForList(
                    "SELECT * FROM inv_requisition WHERE tenant_id=? AND id=? AND deleted=0",
                    tenant,
                    id)
                .getFirst());
    result.put(
        "warehouseName",
        db.queryForObject(
            "SELECT name FROM inv_warehouse WHERE tenant_id=? AND id=?",
            String.class,
            tenant,
            Long.parseLong(result.get("warehouseId").toString())));
    result.put("workflow", List.of());
    result.put("lines", listLines(tenant, id).stream().map(views::view).toList());
    result.put("reservations", listReservations(tenant, id).stream().map(views::view).toList());
    result.put(
        "issues",
        listIssues(tenant, id).stream()
            .map(
                issue -> {
                  var value = views.view(issue);
                  var allocations =
                      listAllocations(tenant, issue.get("id"), id).stream()
                          .map(views::view)
                          .toList();
                  value.put("allocations", allocations);
                  value.put(
                      "totalAmount",
                      allocations.stream()
                          .map(a -> new BigDecimal(a.get("amount").toString()))
                          .reduce(BigDecimal.ZERO, BigDecimal::add)
                          .toPlainString());
                  return value;
                })
            .toList());
    result.put("events", listEvents(tenant, id).stream().map(views::view).toList());
    if (!money) {
      InventoryMoney.hide(result);
    }
    return result;
  }

  public List<Map<String, Object>> listLines(long tenantId, Object requestId) {
    return db.queryForList(
        "SELECT l.*,m.code material_code,m.name material_name,m.model"
            + " material_model,m.unit,COALESCE((SELECT SUM(r.remaining_quantity) FROM"
            + " inv_reservation r WHERE r.tenant_id=l.tenant_id AND"
            + " r.requisition_line_id=l.id),0) reserved_quantity FROM inv_requisition_line"
            + " l JOIN inv_material m ON m.tenant_id=l.tenant_id AND m.id=l.material_id"
            + " WHERE l.tenant_id=? AND l.requisition_id=? ORDER BY l.line_no",
        tenantId,
        requestId);
  }

  public List<Map<String, Object>> listReservations(long tenantId, Object requestId) {
    return db.queryForList(
        "SELECT r.*,m.name material_name,b.name bin_name,pc.code batch_code FROM"
            + " inv_reservation r JOIN inv_balance s ON s.tenant_id=r.tenant_id AND"
            + " s.id=r.balance_id JOIN inv_material m ON m.tenant_id=s.tenant_id AND"
            + " m.id=s.material_id JOIN inv_bin b ON b.tenant_id=s.tenant_id AND"
            + " b.id=s.bin_id LEFT JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND"
            + " pc.id=s.batch_id WHERE r.tenant_id=? AND r.requisition_id=? AND"
            + " r.remaining_quantity>0 ORDER BY r.created_at,r.id",
        tenantId,
        requestId);
  }

  public List<Map<String, Object>> listIssues(long tenantId, Object requestId) {
    return db.queryForList(
        "SELECT i.*,g.code group_code FROM inv_issue i LEFT JOIN inv_issue_group g ON"
            + " g.tenant_id=i.tenant_id AND g.id=i.group_id WHERE i.tenant_id=? AND"
            + " (i.requisition_id=? OR EXISTS(SELECT 1 FROM inv_issue_allocation a JOIN"
            + " inv_requisition_line l ON l.tenant_id=a.tenant_id AND"
            + " l.id=a.requisition_line_id WHERE a.tenant_id=i.tenant_id AND"
            + " a.issue_id=i.id AND l.requisition_id="
            + requestId
            + ")) ORDER BY i.created_at,i.id",
        tenantId,
        requestId);
  }

  public List<Map<String, Object>> listAllocations(long tenantId, Object id, long requestId) {
    return db.queryForList(
        "SELECT a.*,m.name material_name,b.name bin_name,pc.code batch_code"
            + " FROM inv_issue_allocation a JOIN inv_balance s ON"
            + " s.tenant_id=a.tenant_id AND s.id=a.balance_id JOIN"
            + " inv_material m ON m.tenant_id=s.tenant_id AND"
            + " m.id=s.material_id JOIN inv_bin b ON b.tenant_id=s.tenant_id"
            + " AND b.id=s.bin_id LEFT JOIN inv_batch pc ON"
            + " pc.tenant_id=s.tenant_id AND pc.id=s.batch_id WHERE"
            + " a.tenant_id=? AND a.issue_id=? AND a.requisition_line_id IN"
            + " (SELECT id FROM inv_requisition_line WHERE tenant_id="
            + tenantId
            + " AND requisition_id="
            + requestId
            + ") ORDER BY a.id",
        tenantId,
        id);
  }

  public List<Map<String, Object>> listEvents(long tenantId, Object requestId) {
    return db.queryForList(
        "SELECT"
            + " action,actor_name,created_at,JSON_UNQUOTE(JSON_EXTRACT(details_json,'$.reason'))"
            + " reason FROM inv_document_event WHERE tenant_id=? AND document_kind='领料' AND"
            + " document_id=? ORDER BY created_at,id",
        tenantId,
        requestId);
  }
}
