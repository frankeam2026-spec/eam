package com.windasoft.eam.repository.inventory;

import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboundRepository {
  private final JdbcTemplate db;
  private final NumberCounterMapper counter;

  public void nextMonthlyNumber(String rule, String period) {
    counter.nextMonthlyNumber(rule, period);
  }

  public long currentNumber() {
    return counter.currentValue();
  }

  private static final String MATERIAL_WHERE =
      " WHERE tenant_id=? AND enabled=1 AND (code LIKE ? OR name LIKE ? OR model LIKE ? OR category"
          + " LIKE ?)";

  public OutboundRepository(JdbcTemplate db, NumberCounterMapper counter) {
    this.db = db;
    this.counter = counter;
  }

  public List<Map<String, Object>> loadIssue(long tenantId, Object id, boolean lock) {
    return db.queryForList(
        "SELECT * FROM inv_issue WHERE tenant_id=? AND id=? AND deleted=0"
            + (lock ? " FOR UPDATE" : ""),
        tenantId,
        id);
  }

  public int countOtherRecipients(long tenantId, Object id, long userId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_outbound_line WHERE tenant_id=? AND issue_id=? AND"
            + " recipient_id<>?",
        Integer.class,
        tenantId,
        id,
        userId);
  }

  public int countOwnRecipient(long tenantId, Object id, long userId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_outbound_line WHERE tenant_id=? AND issue_id=? AND"
            + " recipient_id=?",
        Integer.class,
        tenantId,
        id,
        userId);
  }

  public List<Map<String, Object>> listLines(long tenantId, Object id) {
    return db.queryForList(
        "SELECT l.*,m.code material_code,m.name material_name,m.model,m.unit,rl.quantity"
            + " requested_quantity,rl.issued_quantity,(rl.quantity-rl.issued_quantity)"
            + " remaining_quantity,COALESCE((SELECT SUM(remaining_quantity) FROM inv_reservation h"
            + " WHERE h.tenant_id=l.tenant_id AND h.requisition_line_id=l.requisition_line_id),0)"
            + " reserved_quantity,r.code requisition_code,d.name department_name FROM"
            + " inv_outbound_line l JOIN inv_material m ON m.tenant_id=l.tenant_id AND"
            + " m.id=l.material_id LEFT JOIN inv_requisition_line rl ON rl.tenant_id=l.tenant_id"
            + " AND rl.id=l.requisition_line_id LEFT JOIN inv_requisition r ON"
            + " r.tenant_id=l.tenant_id AND r.id=l.requisition_id LEFT JOIN eam_org_unit d ON"
            + " d.tenant_id=l.tenant_id AND d.id=l.department_id WHERE l.tenant_id=? AND"
            + " l.issue_id=? ORDER BY l.line_no",
        tenantId,
        id);
  }

  public int insertEvent(
      long recordId, long tenantId, Object id, Object action, long userId, Object value) {
    return db.update(
        "INSERT INTO"
            + " inv_document_event(id,tenant_id,document_kind,document_id,action,actor_id,actor_name)"
            + " VALUES(?,?,'出库',?,?,?,?)",
        recordId,
        tenantId,
        id,
        action,
        userId,
        value);
  }

  public int countCode(long tenantId, Object code, long tenantId3, Object code4) {
    return db.queryForObject(
        "SELECT (SELECT COUNT(*) FROM inv_issue WHERE tenant_id=? AND code=?)+(SELECT COUNT(*)"
            + " FROM inv_issue_group WHERE tenant_id=? AND code=?)",
        Integer.class,
        tenantId,
        code,
        tenantId3,
        code4);
  }

  public Long countMaterials(long tenant, String match) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_material" + MATERIAL_WHERE,
        Long.class,
        tenant,
        match,
        match,
        match,
        match);
  }

  public List<Map<String, Object>> listMaterials(long tenant, String match, int page, int size) {
    return db.queryForList(
        "SELECT id,code,name,model,unit,category,enabled,batch_enabled,expiry_enabled FROM"
            + " inv_material"
            + MATERIAL_WHERE
            + " ORDER BY code,id LIMIT ? OFFSET ?",
        tenant,
        match,
        match,
        match,
        match,
        size,
        ((long) page - 1) * size);
  }

  public List<Map<String, Object>> aggregateMaterialStock(
      long tenant, long warehouse, java.time.LocalDate today, List<Long> materialIds) {
    if (materialIds.isEmpty()) return List.of();
    var args = new ArrayList<Object>();
    args.add(today);
    args.add(tenant);
    args.add(warehouse);
    args.addAll(materialIds);
    return db.queryForList(
        "SELECT s.material_id,COALESCE(SUM(s.quantity),0) stock_quantity,"
            + " COALESCE(SUM(s.reserved_quantity),0) reserved_quantity, COALESCE(SUM(CASE"
            + " WHEN s.stock_status='可用' AND b.enabled=1 AND (m.batch_enabled=0 OR"
            + " s.batch_id<>0) AND (m.expiry_enabled=0 OR pc.expiry_date IS NOT NULL) AND"
            + " (pc.expiry_date IS NULL OR pc.expiry_date>=?) THEN"
            + " GREATEST(s.quantity-s.reserved_quantity,0) ELSE 0 END),0) available_quantity"
            + " FROM inv_balance s JOIN inv_material m ON m.tenant_id=s.tenant_id AND"
            + " m.id=s.material_id JOIN inv_bin b ON b.tenant_id=s.tenant_id AND"
            + " b.id=s.bin_id LEFT JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND"
            + " pc.id=s.batch_id WHERE s.tenant_id=? AND s.warehouse_id=? AND s.material_id"
            + " IN ("
            + String.join(",", Collections.nCopies(materialIds.size(), "?"))
            + ") GROUP BY s.material_id",
        args.toArray());
  }

  public List<Map<String, Object>> listWarehouses(long tenantId) {
    return db.queryForList(
        "SELECT w.*,o.name company_name FROM inv_warehouse w JOIN eam_org_unit o ON"
            + " o.tenant_id=w.tenant_id AND o.id=w.company_id WHERE w.tenant_id=? AND"
            + " w.enabled=1 ORDER BY o.name,w.name",
        tenantId);
  }

  public List<Map<String, Object>> listOrganizations(long tenantId) {
    return db.queryForList(
        "SELECT id,name,parent_id,org_type FROM eam_org_unit WHERE tenant_id=? AND"
            + " deleted=0 AND status=1 ORDER BY id",
        tenantId);
  }

  public List<Map<String, Object>> listUsers(long tenantId) {
    return db.queryForList(
        "SELECT id,display_name name,org_id FROM eam_sys_user WHERE tenant_id=? AND"
            + " deleted=0 AND status=1 ORDER BY display_name",
        tenantId);
  }

  public List<Map<String, Object>> findDepartment(long tenantId, Object department) {
    return db.queryForList(
        "SELECT id,org_type FROM eam_org_unit WHERE tenant_id=? AND id=? AND deleted=0 AND"
            + " status=1",
        tenantId,
        department);
  }

  public List<Map<String, Object>> findRecipient(long tenantId, Object recipient) {
    return db.queryForList(
        "SELECT display_name,org_id FROM eam_sys_user WHERE tenant_id=? AND id=? AND"
            + " deleted=0 AND status=1",
        tenantId,
        recipient);
  }

  public List<Map<String, Object>> findRequestLine(long tenantId, Object reqLine) {
    return db.queryForList(
        "SELECT"
            + " l.*,r.company_id,r.warehouse_id,r.task_kind,r.task_id,r.task_code,r.applicant_id,r.applicant_name,r.id"
            + " request_id FROM inv_requisition_line l JOIN inv_requisition r ON"
            + " r.tenant_id=l.tenant_id AND r.id=l.requisition_id WHERE l.tenant_id=? AND l.id=?"
            + " AND r.deleted=0 FOR UPDATE",
        tenantId,
        reqLine);
  }

  public int insertIssue(
      Object target,
      long tenantId,
      Object value,
      Object company,
      Object warehouse,
      Object value6,
      long userId,
      Object value8,
      Object value9,
      String payload) {
    return db.update(
        "INSERT INTO"
            + " inv_issue(id,tenant_id,code,requisition_id,company_id,warehouse_id,confirmation_key,preview_fingerprint,total_amount,actor_id,actor_name,status,source_kind,payload)"
            + " VALUES(?,?,?,NULL,?,?,?,'',0,?,?,'草稿',?,CAST(? AS JSON))",
        target,
        tenantId,
        value,
        company,
        warehouse,
        value6,
        userId,
        value8,
        value9,
        payload);
  }

  public int updateIssue(
      Object company, Object warehouse, String payload, long tenantId, Object target) {
    return db.update(
        "UPDATE inv_issue SET company_id=?,warehouse_id=?,payload=CAST(? AS"
            + " JSON),row_version=row_version+1 WHERE tenant_id=? AND id=?",
        company,
        warehouse,
        payload,
        tenantId,
        target);
  }

  public int deleteLines(long tenantId, Object target) {
    return db.update(
        "DELETE FROM inv_outbound_line WHERE tenant_id=? AND issue_id=?", tenantId, target);
  }

  public int insertLine(
      long recordId,
      long tenantId,
      Object target,
      Object value,
      Object materialId,
      Object quantity,
      Object requisitionId,
      Object requisitionLineId,
      Object taskKind,
      Object taskId,
      Object taskCode,
      Object departmentId,
      Object recipientId,
      Object recipientName) {
    return db.update(
        "INSERT INTO"
            + " inv_outbound_line(id,tenant_id,issue_id,line_no,material_id,quantity,requisition_id,requisition_line_id,task_kind,task_id,task_code,department_id,recipient_id,recipient_name)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        recordId,
        tenantId,
        target,
        value,
        materialId,
        quantity,
        requisitionId,
        requisitionLineId,
        taskKind,
        taskId,
        taskCode,
        departmentId,
        recipientId,
        recipientName);
  }

  public List<Map<String, Object>> listRequestLines(long tenantId, Object reqId) {
    return db.queryForList(
        "SELECT l.*,m.code material_code,m.name material_name,m.unit,COALESCE((SELECT"
            + " SUM(remaining_quantity) FROM inv_reservation s WHERE s.tenant_id=l.tenant_id"
            + " AND s.requisition_line_id=l.id),0) reserved_quantity FROM"
            + " inv_requisition_line l JOIN inv_material m ON m.tenant_id=l.tenant_id AND"
            + " m.id=l.material_id WHERE l.tenant_id=? AND l.requisition_id=? AND"
            + " l.quantity>l.issued_quantity ORDER BY l.line_no",
        tenantId,
        reqId);
  }

  public List<Map<String, Object>> findLineRequest(long tenantId, Object reqLine) {
    return db.queryForList(
        "SELECT requisition_id FROM inv_requisition_line WHERE tenant_id=? AND id=?",
        tenantId,
        reqLine);
  }

  public List<Map<String, Object>> listStock(
      long tenantId, Object warehouse, Object material, long reqLine) {
    return db.queryForList(
        "SELECT s.id,s.material_id,s.bin_id,b.name bin_name,pc.code"
            + " batch_code,pc.expiry_date,s.stock_status,b.enabled"
            + " bin_enabled,s.quantity,s.reserved_quantity,(SELECT"
            + " COALESCE(SUM(remaining_quantity),0) FROM inv_reservation h WHERE"
            + " h.tenant_id=s.tenant_id AND h.balance_id=s.id AND h.requisition_line_id="
            + reqLine
            + ") own_reserved_quantity,s.quantity-s.reserved_quantity available_quantity FROM"
            + " inv_balance s JOIN inv_bin b ON b.tenant_id=s.tenant_id AND b.id=s.bin_id LEFT"
            + " JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id WHERE"
            + " s.tenant_id=? AND s.warehouse_id=? AND s.material_id=? AND s.stock_status='可用'"
            + " AND b.enabled=1 AND (pc.expiry_date IS NULL OR pc.expiry_date>=CURRENT_DATE())"
            + " ORDER BY b.code,pc.code,s.id",
        tenantId,
        warehouse,
        material);
  }

  public Map<String, Object> lockRequestQuantity(long tenantId, Object reqLine, boolean lock) {
    return db.queryForMap(
        "SELECT quantity,issued_quantity FROM inv_requisition_line WHERE tenant_id=? AND"
            + " id=?"
            + (lock ? " FOR UPDATE" : ""),
        tenantId,
        reqLine);
  }

  public List<Map<String, Object>> listMaterialStock(
      long tenantId, Object warehouse, Object material, boolean lock) {
    return db.queryForList(
        "SELECT s.*,b.name bin_name,b.enabled bin_enabled,pc.code"
            + " batch_code,pc.expiry_date,COALESCE((SELECT MIN(c.origin_received_at) FROM"
            + " inv_cost_layer c WHERE c.tenant_id=s.tenant_id AND c.balance_id=s.id AND"
            + " c.remaining_quantity>0),'9999-12-31') first_received_at FROM inv_balance s"
            + " JOIN inv_bin b ON b.tenant_id=s.tenant_id AND b.id=s.bin_id LEFT JOIN"
            + " inv_batch pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id WHERE"
            + " s.tenant_id=? AND s.warehouse_id=? AND s.material_id=? ORDER BY s.id"
            + (lock ? " FOR UPDATE" : ""),
        tenantId,
        warehouse,
        material);
  }

  public List<Map<String, Object>> listOwnReservations(long tenantId, Object key, boolean lock) {
    return db.queryForList(
        "SELECT * FROM inv_reservation WHERE tenant_id=? AND"
            + " requisition_line_id=? AND remaining_quantity>0 ORDER BY id"
            + (lock ? " FOR UPDATE" : ""),
        tenantId,
        key);
  }

  public List<Map<String, Object>> listCostLayers(long tenantId, Object key, boolean lock) {
    return db.queryForList(
        "SELECT * FROM inv_cost_layer WHERE tenant_id=? AND balance_id=? AND"
            + " remaining_quantity>0 ORDER BY origin_received_at,id"
            + (lock ? " FOR UPDATE" : ""),
        tenantId,
        key);
  }

  public int countConfirmationKey(long tenantId, Object key, Object issueId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_issue WHERE tenant_id=? AND confirmation_key=? AND id<>?",
        Integer.class,
        tenantId,
        key,
        issueId);
  }

  public int takeBalance(
      Object qty, Object value, long tenantId, Object balanceId, Object qty5, Object value6) {
    return db.update(
        "UPDATE inv_balance SET"
            + " quantity=quantity-?,reserved_quantity=reserved_quantity-?,row_version=row_version+1"
            + " WHERE tenant_id=? AND id=? AND quantity>=? AND reserved_quantity>=?",
        qty,
        value,
        tenantId,
        balanceId,
        qty5,
        value6);
  }

  public int consumeReservation(Object qty, long tenantId, Object reservationId, Object qty4) {
    return db.update(
        "UPDATE inv_reservation SET remaining_quantity=remaining_quantity-? WHERE"
            + " tenant_id=? AND id=? AND remaining_quantity>=?",
        qty,
        tenantId,
        reservationId,
        qty4);
  }

  public int consumeCostLayer(
      Object qty, Object amount, long tenantId, Object costLayerId, Object qty5, Object amount6) {
    return db.update(
        "UPDATE inv_cost_layer SET"
            + " remaining_quantity=remaining_quantity-?,remaining_cost=remaining_cost-?"
            + " WHERE tenant_id=? AND id=? AND remaining_quantity>=? AND"
            + " remaining_cost>=?",
        qty,
        amount,
        tenantId,
        costLayerId,
        qty5,
        amount6);
  }

  public int addIssuedQuantity(Object qty, long tenantId, Object requisitionLineId, Object qty4) {
    return db.update(
        "UPDATE inv_requisition_line SET issued_quantity=issued_quantity+? WHERE"
            + " tenant_id=? AND id=? AND quantity-issued_quantity>=?",
        qty,
        tenantId,
        requisitionLineId,
        qty4);
  }

  public int insertAllocation(
      Object allocation,
      long tenantId,
      Object issueId,
      Object outboundLineId,
      Object requisitionLineId,
      Object reservationId,
      Object balanceId,
      Object costLayerId,
      Object qty,
      Object amount,
      Object unitCost) {
    return db.update(
        "INSERT INTO"
            + " inv_issue_allocation(id,tenant_id,issue_id,outbound_line_id,requisition_line_id,reservation_id,balance_id,cost_layer_id,quantity,amount,unit_cost)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        allocation,
        tenantId,
        issueId,
        outboundLineId,
        requisitionLineId,
        reservationId,
        balanceId,
        costLayerId,
        qty,
        amount,
        unitCost);
  }

  public int insertTransaction(
      long recordId,
      long tenantId,
      Object companyId,
      Object warehouseId,
      Object balanceId,
      Object issueId,
      Object allocation,
      Object value,
      Object value9,
      long userId) {
    return db.update(
        "INSERT INTO"
            + " inv_transaction(id,tenant_id,company_id,warehouse_id,balance_id,document_id,source_line_id,kind,quantity,amount,actor_id)"
            + " VALUES(?,?,?,?,?,?,?,'出库',?,?,?)",
        recordId,
        tenantId,
        companyId,
        warehouseId,
        balanceId,
        issueId,
        allocation,
        value,
        value9,
        userId);
  }

  public int confirmIssue(
      Object key,
      Object fingerprint,
      Object totalAmount,
      long userId,
      Object value,
      long tenantId,
      Object issueId) {
    return db.update(
        "UPDATE inv_issue SET"
            + " status='已出库',confirmation_key=?,preview_fingerprint=?,total_amount=?,actor_id=?,actor_name=?,confirmed_at=NOW(3),row_version=row_version+1"
            + " WHERE tenant_id=? AND id=?",
        key,
        fingerprint,
        totalAmount,
        userId,
        value,
        tenantId,
        issueId);
  }

  public int insertRecipientNotice(
      long recordId, long tenantId, Object recipientId, Object issueId, Object value) {
    return db.update(
        "INSERT INTO"
            + " inv_inbox(id,tenant_id,user_id,document_id,route_kind,kind,title,is_todo)"
            + " VALUES(?,?,?,?,'outbounds','出库完成',?,0)",
        recordId,
        tenantId,
        recipientId,
        issueId,
        value);
  }

  public int deleteIssue(long tenantId, Object id) {
    return db.update(
        "UPDATE inv_issue SET deleted=1,row_version=row_version+1 WHERE tenant_id=? AND id=?",
        tenantId,
        id);
  }

  public List<Map<String, Object>> listAllocations(long tenantId, Object issueId) {
    return db.queryForList(
        "SELECT a.*,m.code material_code,m.name material_name,b.name bin_name,pc.code"
            + " batch_code,pc.expiry_date,(SELECT receipt_id FROM inv_receipt_line rl WHERE"
            + " rl.tenant_id=c.tenant_id AND rl.id=c.source_line_id)"
            + " receipt_id,c.origin_received_at,r.code"
            + " requisition_code,COALESCE(l.task_kind,r.task_kind)"
            + " task_kind,COALESCE(l.task_id,r.task_id)"
            + " task_id,COALESCE(l.task_code,r.task_code)"
            + " task_code,COALESCE(l.recipient_name,r.applicant_name) recipient_name FROM"
            + " inv_issue_allocation a JOIN inv_balance s ON s.tenant_id=a.tenant_id AND"
            + " s.id=a.balance_id JOIN inv_material m ON m.tenant_id=s.tenant_id AND"
            + " m.id=s.material_id JOIN inv_bin b ON b.tenant_id=s.tenant_id AND b.id=s.bin_id"
            + " LEFT JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id JOIN"
            + " inv_cost_layer c ON c.tenant_id=a.tenant_id AND c.id=a.cost_layer_id LEFT JOIN"
            + " inv_outbound_line l ON l.tenant_id=a.tenant_id AND l.id=a.outbound_line_id LEFT"
            + " JOIN inv_requisition_line rl ON rl.tenant_id=a.tenant_id AND"
            + " rl.id=a.requisition_line_id LEFT JOIN inv_requisition r ON"
            + " r.tenant_id=rl.tenant_id AND r.id=rl.requisition_id WHERE a.tenant_id=? AND"
            + " a.issue_id=? ORDER BY a.id",
        tenantId,
        issueId);
  }

  public List<Map<String, Object>> findLegacyGroup(long tenantId, Object value) {
    return db.queryForList(
        "SELECT * FROM inv_issue_group WHERE tenant_id=? AND id=?", tenantId, value);
  }

  public String findLegacyCompany(long tenantId, Object companyId) {
    return db.queryForObject(
        "SELECT name FROM eam_org_unit WHERE tenant_id=? AND id=?",
        String.class,
        tenantId,
        companyId);
  }

  public List<Map<String, Object>> listLegacyGroupIssues(long tenantId, Object id) {
    return db.queryForList(
        "SELECT id,reversed FROM inv_issue WHERE tenant_id=? AND group_id=? ORDER BY id",
        tenantId,
        id);
  }

  public BigDecimal findLegacyAmount(long tenantId, Object id) {
    return db.queryForObject(
        "SELECT total_amount FROM inv_issue WHERE tenant_id=? AND id=?",
        BigDecimal.class,
        tenantId,
        id);
  }

  public String findCompany(long tenantId, Object companyId) {
    return db.queryForObject(
        "SELECT name FROM eam_org_unit WHERE tenant_id=? AND id=?",
        String.class,
        tenantId,
        companyId);
  }

  public List<Map<String, Object>> listEvents(long tenantId, Object issueId) {
    return db.queryForList(
        "SELECT action,actor_name,created_at FROM inv_document_event WHERE tenant_id=? AND"
            + " document_kind IN ('出库','issue') AND document_id=? ORDER BY created_at,id",
        tenantId,
        issueId);
  }

  public List<Map<String, Object>> searchIssues(long tenantId) {
    return db.queryForList(
        "SELECT i.*,DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s')"
            + " fmt_created_at,DATE_FORMAT(i.confirmed_at,'%Y-%m-%d %H:%i:%s')"
            + " fmt_confirmed_at,w.name warehouse_name,COALESCE((SELECT GROUP_CONCAT(DISTINCT"
            + " recipient_name SEPARATOR '、') FROM inv_outbound_line l WHERE"
            + " l.tenant_id=i.tenant_id AND l.issue_id=i.id),(SELECT applicant_name FROM"
            + " inv_requisition r WHERE r.tenant_id=i.tenant_id AND r.id=i.requisition_id))"
            + " recipient_name,COALESCE((SELECT GROUP_CONCAT(DISTINCT task_code SEPARATOR '、')"
            + " FROM inv_outbound_line l WHERE l.tenant_id=i.tenant_id AND l.issue_id=i.id),'')"
            + " task_code,COALESCE((SELECT SUM(a.quantity) FROM inv_issue_allocation a WHERE"
            + " a.tenant_id=i.tenant_id AND a.issue_id=i.id),(SELECT SUM(l.quantity) FROM"
            + " inv_outbound_line l WHERE l.tenant_id=i.tenant_id AND l.issue_id=i.id))"
            + " total_quantity FROM inv_issue i JOIN inv_warehouse w ON w.tenant_id=i.tenant_id"
            + " AND w.id=i.warehouse_id WHERE i.tenant_id=? AND i.deleted=0 AND i.group_id IS"
            + " NULL ORDER BY i.created_at DESC,i.id DESC",
        tenantId);
  }

  public List<Map<String, Object>> searchLegacyGroups(long tenantId) {
    return db.queryForList(
        "SELECT g.*,DATE_FORMAT(g.created_at,'%Y-%m-%d %H:%i:%s') fmt_created_at,w.name"
            + " warehouse_name,(SELECT SUM(total_amount) FROM inv_issue i WHERE"
            + " i.tenant_id=g.tenant_id AND i.group_id=g.id) total_amount,(SELECT"
            + " SUM(a.quantity) FROM inv_issue_allocation a JOIN inv_issue i ON"
            + " i.tenant_id=a.tenant_id AND i.id=a.issue_id WHERE i.tenant_id=g.tenant_id AND"
            + " i.group_id=g.id) total_quantity FROM inv_issue_group g JOIN inv_warehouse w ON"
            + " w.tenant_id=g.tenant_id AND w.id=g.warehouse_id WHERE g.tenant_id=? ORDER BY"
            + " g.created_at DESC",
        tenantId);
  }

  public int countUnreversedLegacyIssues(long tenantId, Object id) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_issue WHERE tenant_id=? AND group_id=? AND" + " reversed=0",
        Integer.class,
        tenantId,
        id);
  }

  public List<Map<String, Object>> findReservation(long tenantId, Object res, Object requestId) {
    return db.queryForList(
        "SELECT * FROM inv_reservation WHERE tenant_id=? AND id=? AND requisition_id=?",
        tenantId,
        res,
        requestId);
  }

  public List<Map<String, Object>> findConfirmedIssue(long tenantId, Object key) {
    return db.queryForList(
        "SELECT * FROM inv_issue WHERE tenant_id=? AND confirmation_key=? AND deleted=0",
        tenantId,
        key);
  }

  public int countIssueRequest(long tenantId, Object id, Object requestId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_outbound_line WHERE tenant_id=? AND issue_id=? AND"
            + " requisition_id=?",
        Integer.class,
        tenantId,
        id,
        requestId);
  }

  public int updatePayload(String payload, long tenantId, Object issue) {
    return db.update(
        "UPDATE inv_issue SET payload=CAST(? AS JSON) WHERE tenant_id=? AND id=?",
        payload,
        tenantId,
        issue);
  }

  public List<Map<String, Object>> findIssueByKey(long tenantId, Object key) {
    return db.queryForList(
        "SELECT * FROM inv_issue WHERE tenant_id=? AND confirmation_key=? AND deleted=0",
        tenantId,
        key);
  }

  public int storePreviewPayload(String payload, long tenantId, Object issue) {
    return db.update(
        "UPDATE inv_issue SET payload=CAST(? AS JSON) WHERE tenant_id=? AND id=?",
        payload,
        tenantId,
        issue);
  }
}
