package com.windasoft.eam.repository.inventory;

import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import com.windasoft.eam.repository.repair.RepairOrderMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RequisitionRepository {
  public void workflowStatus(long tenant, long id, String state, String gate) {
    db.update(
        "UPDATE inv_requisition SET status=?,approval_gate=?,row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=? AND deleted=0",
        state,
        gate,
        tenant,
        id);
  }

  public Map<String, Object> workflowValues(long tenant, long id) {
    var rows =
        db.queryForList(
            "SELECT r.applicant_id AS applicantId,CASE WHEN o.org_type='部门' THEN u.org_id ELSE NULL"
                + " END AS applicantDepartmentId,r.company_id AS companyId,r.warehouse_id AS"
                + " warehouseId,r.task_kind AS taskKind FROM inv_requisition r JOIN eam_sys_user u"
                + " ON u.tenant_id=r.tenant_id AND u.id=r.applicant_id LEFT JOIN eam_org_unit o ON"
                + " o.tenant_id=u.tenant_id AND o.id=u.org_id WHERE r.tenant_id=? AND r.id=? AND"
                + " r.deleted=0",
            tenant,
            id);
    if (rows.isEmpty()) throw new IllegalArgumentException("领料申请不存在");
    var values = rows.getFirst();
    values.put(
        "lines",
        db.queryForList(
            "SELECT l.material_id AS materialId,m.code,l.quantity FROM inv_requisition_line l JOIN"
                + " inv_material m ON m.tenant_id=l.tenant_id AND m.id=l.material_id WHERE"
                + " l.tenant_id=? AND l.requisition_id=? ORDER BY l.line_no,l.id",
            tenant,
            id));
    return values;
  }

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.repository.system.NotificationRepository notifications;

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
  private final RepairOrderMapper repairs;

  public RepairOrder findRepair(long id) {
    return repairs.selectById(id);
  }

  public List<RepairOrder> listRepairs(long tenant) {
    return repairs.selectList(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RepairOrder>()
            .eq(RepairOrder::getTenantId, tenant)
            .eq(RepairOrder::getDeleted, 0));
  }

  public RequisitionRepository(
      JdbcTemplate db, NumberCounterMapper counter, RepairOrderMapper repairs) {
    this.db = db;
    this.counter = counter;
    this.repairs = repairs;
  }

  public List<Map<String, Object>> loadRequest(long tenantId, Object requestId, boolean lock) {
    return db.queryForList(
        "SELECT * FROM inv_requisition WHERE tenant_id=? AND id=? AND deleted=0"
            + (lock ? " FOR UPDATE" : ""),
        tenantId,
        requestId);
  }

  public List<Map<String, Object>> listActiveUsers(long tenantId) {
    return db.queryForList(
        "SELECT id FROM eam_sys_user WHERE tenant_id=? AND status=1 AND deleted=0", tenantId);
  }

  public int insertWarehouseTodo(
      long recordId, long tenantId, Object recipient, Object requestId, Object value) {
    int count =
        db.update(
            "INSERT INTO inv_inbox(id,tenant_id,user_id,document_id,route_kind,kind,title,is_todo)"
                + " VALUES(?,?,?,?,'requisitions','待出库',?,1)",
            recordId,
            tenantId,
            recipient,
            requestId,
            value);
    db.update(
        "UPDATE inv_inbox SET message_key=?,message_args=? WHERE tenant_id=? AND id=?",
        com.windasoft.eam.common.NotificationMessages.key("待出库"),
        com.windasoft.eam.common.NotificationMessages.arguments("待出库", value),
        tenantId,
        recordId);
    if (notifications != null) notifications.captureSource("inv_inbox", tenantId, recordId);
    return count;
  }

  public int completeWarehouseTodos(long tenantId, Object requestId) {
    return db.update(
        "UPDATE inv_inbox SET done=1 WHERE tenant_id=? AND document_id=? AND"
            + " route_kind='requisitions' AND kind='待出库' AND is_todo=1",
        tenantId,
        requestId);
  }

  public int insertRepairEvent(
      long recordId, long tenantId, Object taskId, Object action, long userId, Object value) {
    return db.update(
        "INSERT INTO"
            + " eam_repair_event(id,tenant_id,order_id,action,actor_id,actor_name,details_json,created_at)"
            + " VALUES(?,?,?,?,?,?,JSON_OBJECT(),NOW())",
        recordId,
        tenantId,
        taskId,
        action,
        userId,
        value);
  }

  public int insertTaskEvent(
      long recordId,
      long tenantId,
      Object taskId,
      long userId,
      Object value,
      Object action,
      String table) {
    if (!(Set.of("eam_maintenance_event", "eam_inspection_event").contains(table)))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.inventory.message0008");
    return db.update(
        "INSERT INTO "
            + table
            + "(id,tenant_id,task_id,actor_id,actor_name,action,details)"
            + " VALUES(?,?,?,?,?,?,JSON_OBJECT())",
        recordId,
        tenantId,
        taskId,
        userId,
        value,
        action);
  }

  public int insertEvent(
      long recordId, long tenantId, long requestId, String action, long userId, String actorName) {
    return db.update(
        "INSERT INTO"
            + " inv_document_event(id,tenant_id,document_kind,document_id,action,actor_id,actor_name)"
            + " VALUES(?,?,'领料',?,?,?,?)",
        recordId,
        tenantId,
        requestId,
        action,
        userId,
        actorName);
  }

  public int countCode(long tenantId, Object code) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_requisition WHERE tenant_id=? AND code=?",
        Integer.class,
        tenantId,
        code);
  }

  public BigDecimal sumIssued(long tenantId, Object id) {
    return db.queryForObject(
        "SELECT COALESCE(SUM(issued_quantity),0) FROM inv_requisition_line WHERE"
            + " tenant_id=? AND requisition_id=?",
        BigDecimal.class,
        tenantId,
        id);
  }

  public BigDecimal sumRemaining(long tenantId, Object id) {
    return db.queryForObject(
        "SELECT COALESCE(SUM(quantity-issued_quantity),0) FROM inv_requisition_line WHERE"
            + " tenant_id=? AND requisition_id=?",
        BigDecimal.class,
        tenantId,
        id);
  }

  public List<Map<String, Object>> listWarehouseChoices(long tenantId) {
    return db.queryForList(
        "SELECT id,name,company_id FROM inv_warehouse WHERE tenant_id=? AND enabled=1 ORDER"
            + " BY name",
        tenantId);
  }

  public List<Map<String, Object>> listApplicantWarehouseChoices(long tenantId) {
    return db.queryForList(
        "SELECT id,name,company_id FROM inv_warehouse WHERE tenant_id=? AND enabled=1 ORDER"
            + " BY name",
        tenantId);
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

  public int deleteLines(long tenantId, Object target) {
    return db.update(
        "DELETE FROM inv_requisition_line WHERE tenant_id=? AND requisition_id=?",
        tenantId,
        target);
  }

  public int submitRequest(long tenantId, Object requestId) {
    return db.update(
        "UPDATE inv_requisition SET status=IF(EXISTS(SELECT 1 FROM inv_requisition_line l WHERE"
            + " l.tenant_id=inv_requisition.tenant_id AND l.requisition_id=inv_requisition.id AND"
            + " l.issued_quantity>0),'部分发料','待预留'),row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        tenantId,
        requestId);
  }

  public List<Map<String, Object>> listReservableStock(
      long tenantId, Object requestId, Object warehouseId, Object value) {
    return db.queryForList(
        "SELECT s.id balance_id,l.id line_id,m.name material_name,b.name bin_name,pc.code"
            + " batch_code,pc.expiry_date,s.quantity-s.reserved_quantity available_quantity"
            + " FROM inv_requisition_line l JOIN inv_material m ON m.tenant_id=l.tenant_id AND"
            + " m.id=l.material_id AND m.enabled=1 JOIN inv_balance s ON"
            + " s.tenant_id=l.tenant_id AND s.material_id=l.material_id JOIN inv_bin b ON"
            + " b.tenant_id=s.tenant_id AND b.id=s.bin_id AND b.enabled=1 LEFT JOIN inv_batch"
            + " pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id WHERE l.tenant_id=? AND"
            + " l.requisition_id=? AND s.warehouse_id=? AND s.stock_status='可用' AND"
            + " s.quantity>s.reserved_quantity AND (pc.expiry_date IS NULL OR"
            + " pc.expiry_date>=?) AND (m.expiry_enabled=0 OR pc.expiry_date IS NOT NULL) ORDER"
            + " BY m.code,b.code,pc.code",
        tenantId,
        requestId,
        warehouseId,
        value);
  }

  public int countLine(long tenantId, Object requestId, Object lineId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_requisition_line WHERE tenant_id=? AND"
            + " requisition_id=? AND id=?",
        Integer.class,
        tenantId,
        requestId,
        lineId);
  }

  public List<Map<String, Object>> lockLines(long tenantId, Object requestId) {
    return db.queryForList(
        "SELECT * FROM inv_requisition_line WHERE tenant_id=? AND requisition_id=? ORDER BY"
            + " material_id FOR UPDATE",
        tenantId,
        requestId);
  }

  public BigDecimal sumLineReservations(long tenantId, Object id) {
    return db.queryForObject(
        "SELECT COALESCE(SUM(remaining_quantity),0) FROM inv_reservation WHERE tenant_id=?"
            + " AND requisition_line_id=?",
        BigDecimal.class,
        tenantId,
        id);
  }

  public List<Map<String, Object>> lockAvailableStock(
      long tenantId, Object id, Object id3, Object today, Object expiryEnabled) {
    return db.queryForList(
        "SELECT s.*,pc.expiry_date,COALESCE((SELECT MIN(c.origin_received_at) FROM"
            + " inv_cost_layer c WHERE c.tenant_id=s.tenant_id AND c.balance_id=s.id AND"
            + " c.remaining_quantity>0),'9999-12-31') first_received_at FROM inv_balance s"
            + " JOIN inv_bin b ON b.tenant_id=s.tenant_id AND b.id=s.bin_id LEFT JOIN"
            + " inv_batch pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id WHERE"
            + " s.tenant_id=? AND s.warehouse_id=? AND s.material_id=? AND"
            + " s.stock_status='可用' AND s.quantity>s.reserved_quantity AND b.enabled=1 AND"
            + " (pc.expiry_date IS NULL OR pc.expiry_date>=?) AND (?=0 OR pc.expiry_date IS"
            + " NOT NULL) ORDER BY s.id FOR UPDATE",
        tenantId,
        id,
        id3,
        today,
        expiryEnabled);
  }

  public int addReservedQuantity(Object qty, long tenantId, Object id) {
    return db.update(
        "UPDATE inv_balance SET reserved_quantity=reserved_quantity+?,row_version=row_version+1"
            + " WHERE tenant_id=? AND id=?",
        qty,
        tenantId,
        id);
  }

  public int insertReservation(
      long recordId,
      long tenantId,
      Object requestId,
      Object id,
      Object id5,
      Object qty,
      Object qty7) {
    return db.update(
        "INSERT INTO"
            + " inv_reservation(id,tenant_id,requisition_id,requisition_line_id,balance_id,quantity,remaining_quantity)"
            + " VALUES(?,?,?,?,?,?,?)",
        recordId,
        tenantId,
        requestId,
        id,
        id5,
        qty,
        qty7);
  }

  public int updateReservationStatus(Object value, long tenantId, Object requestId) {
    return db.update(
        "UPDATE inv_requisition SET status=?,row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        value,
        tenantId,
        requestId);
  }

  public List<Map<String, Object>> lockReservations(long tenantId, Object requestId) {
    return db.queryForList(
        "SELECT * FROM inv_reservation WHERE tenant_id=? AND requisition_id=? AND"
            + " remaining_quantity>0 ORDER BY balance_id,id FOR UPDATE",
        tenantId,
        requestId);
  }

  public int resetStatus(long tenantId, Object requestId) {
    return db.update(
        "UPDATE inv_requisition SET status=IF(EXISTS(SELECT 1 FROM inv_requisition_line l WHERE"
            + " l.tenant_id=inv_requisition.tenant_id AND l.requisition_id=inv_requisition.id AND"
            + " l.issued_quantity>0),'部分发料','待预留'),row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        tenantId,
        requestId);
  }

  public Map<String, Object> lockBalance(long tenantId, Object balanceId) {
    return db.queryForMap(
        "SELECT id FROM inv_balance WHERE tenant_id=? AND id=? FOR UPDATE", tenantId, balanceId);
  }

  public int releaseBalance(Object remainingQuantity, long tenantId, Object balanceId) {
    return db.update(
        "UPDATE inv_balance SET reserved_quantity=reserved_quantity-?,row_version=row_version+1"
            + " WHERE tenant_id=? AND id=?",
        remainingQuantity,
        tenantId,
        balanceId);
  }

  public int releaseReservation(long tenantId, Object id) {
    return db.update(
        "UPDATE inv_reservation SET remaining_quantity=0 WHERE tenant_id=? AND id=?", tenantId, id);
  }

  public BigDecimal sumIssuedForCancel(long tenantId, Object requestId) {
    return db.queryForObject(
        "SELECT COALESCE(SUM(issued_quantity),0) FROM inv_requisition_line WHERE"
            + " tenant_id=? AND requisition_id=?",
        BigDecimal.class,
        tenantId,
        requestId);
  }

  public List<Map<String, Object>> lockReservationsForCancel(long tenantId, Object requestId) {
    return db.queryForList(
        "SELECT * FROM inv_reservation WHERE tenant_id=? AND requisition_id=? AND"
            + " remaining_quantity>0 ORDER BY balance_id,id FOR UPDATE",
        tenantId,
        requestId);
  }

  public int cancelRequest(long tenantId, Object requestId) {
    return db.update(
        "UPDATE inv_requisition SET status='已取消',row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        tenantId,
        requestId);
  }

  public int deleteRequest(long tenantId, Object recordId) {
    return db.update(
        "UPDATE inv_requisition SET deleted=1,row_version=row_version+1 WHERE tenant_id=? AND id=?",
        tenantId,
        recordId);
  }

  public Map<String, Object> remainingSummary(long tenantId, Object requestId) {
    return db.queryForMap(
        "SELECT COALESCE(SUM(quantity-issued_quantity),0) remaining FROM inv_requisition_line"
            + " WHERE tenant_id=? AND requisition_id=?",
        tenantId,
        requestId);
  }

  public BigDecimal sumReservations(long tenantId, Object requestId) {
    return db.queryForObject(
        "SELECT COALESCE(SUM(remaining_quantity),0) FROM inv_reservation WHERE tenant_id=? AND"
            + " requisition_id=?",
        BigDecimal.class,
        tenantId,
        requestId);
  }

  public Long findTaskDepartment(long tenantId, Object taskId, String field, String table) {
    if (!(Map.of(
            "eam_repair_order",
            "department_org_id",
            "v_maintenance_task",
            "department_id",
            "v_inspection_task",
            "department_id")
        .getOrDefault(table, "")
        .equals(field)))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.inventory.message0008");
    return db.queryForObject(
        "SELECT " + field + " FROM " + table + " WHERE tenant_id=? AND id=?",
        Long.class,
        tenantId,
        taskId);
  }

  public int countUnissuedLines(long tenantId, Object requestId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_requisition_line WHERE tenant_id=? AND requisition_id=?"
            + " AND issued_quantity<quantity",
        Integer.class,
        tenantId,
        requestId);
  }

  public int updateIssueProgress(Object value, long tenantId, Object requestId) {
    return db.update(
        "UPDATE inv_requisition SET status=?,row_version=row_version+1 WHERE tenant_id=? AND id=?",
        value,
        tenantId,
        requestId);
  }

  public int insertApplicantNotice(
      long recordId, long tenantId, Object applicantId, Object requestId, Object value) {
    int count =
        db.update(
            "INSERT INTO inv_inbox(id,tenant_id,user_id,document_id,route_kind,kind,title,is_todo)"
                + " VALUES(?,?,?,?,'requisitions','出库完成',?,0)",
            recordId,
            tenantId,
            applicantId,
            requestId,
            value);
    db.update(
        "UPDATE inv_inbox SET message_key=?,message_args=? WHERE tenant_id=? AND id=?",
        com.windasoft.eam.common.NotificationMessages.key("出库完成"),
        com.windasoft.eam.common.NotificationMessages.arguments("出库完成", value),
        tenantId,
        recordId);
    if (notifications != null) notifications.captureSource("inv_inbox", tenantId, recordId);
    return count;
  }

  public List<Map<String, Object>> lockReservationsForClose(long tenantId, Object requestId) {
    return db.queryForList(
        "SELECT * FROM inv_reservation WHERE tenant_id=? AND requisition_id=? AND"
            + " remaining_quantity>0 ORDER BY balance_id,id FOR UPDATE",
        tenantId,
        requestId);
  }

  public int closeRequest(long tenantId, Object requestId) {
    return db.update(
        "UPDATE inv_requisition SET status='已关闭',row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        tenantId,
        requestId);
  }

  public int insertCloseEvent(
      long recordId, long tenantId, Object requestId, long userId, Object value, Object value6) {
    return db.update(
        "INSERT INTO"
            + " inv_document_event(id,tenant_id,document_kind,document_id,action,actor_id,actor_name,details_json)"
            + " VALUES(?,?,'领料',?,'关闭剩余需求',?,?,JSON_OBJECT('reason',?))",
        recordId,
        tenantId,
        requestId,
        userId,
        value,
        value6);
  }

  public int insertCloseNotice(
      long recordId, long tenantId, Object applicantId, Object requestId, Object value) {
    int count =
        db.update(
            "INSERT INTO inv_inbox(id,tenant_id,user_id,document_id,route_kind,kind,title,is_todo)"
                + " VALUES(?,?,?,?,'requisitions','需求已关闭',?,0)",
            recordId,
            tenantId,
            applicantId,
            requestId,
            value);
    db.update(
        "UPDATE inv_inbox SET message_key=?,message_args=? WHERE tenant_id=? AND id=?",
        com.windasoft.eam.common.NotificationMessages.key("需求已关闭"),
        com.windasoft.eam.common.NotificationMessages.arguments("需求已关闭", value),
        tenantId,
        recordId);
    if (notifications != null) notifications.captureSource("inv_inbox", tenantId, recordId);
    return count;
  }
}
