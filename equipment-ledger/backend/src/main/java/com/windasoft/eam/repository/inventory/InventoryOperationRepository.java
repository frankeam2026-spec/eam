package com.windasoft.eam.repository.inventory;

import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryOperationRepository {
  private final JdbcTemplate db;
  private final InventoryDocumentStorage documents;
  private final NumberCounterMapper counter;

  public InventoryOperationRepository(
      JdbcTemplate db, NumberCounterMapper counter, InventoryDocumentStorage documents) {
    this.db = db;
    this.counter = counter;
    this.documents = documents;
  }

  public void nextMonthlyNumber(String rule, String period) {
    counter.nextMonthlyNumber(rule, period);
  }

  public long currentNumber() {
    return counter.currentValue();
  }

  public List<Map<String, Object>> loadOperation(
      long tenantId, Object kind, Object doc, boolean locked) {
    if (locked) documents.lock(tenantId, doc, kind);
    return db.queryForList(
        "SELECT * FROM v_inv_operation WHERE tenant_id=? AND kind=? AND id=? AND deleted=0",
        tenantId,
        kind,
        doc);
  }

  public List<Map<String, Object>> loadSource(long tenantId, Object sourceId, String table) {
    if (!Set.of("inv_receipt", "inv_issue", "v_inv_operation").contains(table))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.inventory.message0013");
    return db.queryForList(
        "SELECT * FROM " + table + " WHERE tenant_id=? AND id=?", tenantId, sourceId);
  }

  public List<Map<String, Object>> loadLegacyStock(long tenantId, Object stock) {
    return db.queryForList(
        "SELECT * FROM eam_spare_stock WHERE tenant_id=? AND id=?", tenantId, stock);
  }

  public List<Map<String, Object>> loadBalance(long tenantId, Object balance, boolean locked) {
    return db.queryForList(
        "SELECT * FROM inv_balance WHERE tenant_id=? AND id=?" + (locked ? " FOR UPDATE" : ""),
        tenantId,
        balance);
  }

  public List<Map<String, Object>> loadIssueAllocation(
      long tenantId, Object allocation, boolean locked) {
    return db.queryForList(
        "SELECT a.*,(SELECT COALESCE(SUM(u.cost_amount),0) FROM inv_usage u WHERE"
            + " u.tenant_id=a.tenant_id AND u.allocation_id=a.id) used_cost,(SELECT COUNT(*) FROM"
            + " inv_usage u WHERE u.tenant_id=a.tenant_id AND u.allocation_id=a.id AND"
            + " u.cost_amount IS NULL) missing_used_cost,i.code"
            + " issue_code,i.company_id,i.warehouse_id,i.requisition_id,s.material_id,s.batch_id,m.name"
            + " material_name FROM inv_issue_allocation a JOIN inv_issue i ON"
            + " i.tenant_id=a.tenant_id AND i.id=a.issue_id JOIN inv_balance s ON"
            + " s.tenant_id=a.tenant_id AND s.id=a.balance_id JOIN inv_material m ON"
            + " m.tenant_id=s.tenant_id AND m.id=s.material_id WHERE a.tenant_id=? AND a.id=? AND"
            + " i.reversed=0"
            + (locked ? " FOR UPDATE" : ""),
        tenantId,
        allocation);
  }

  public List<Map<String, Object>> lockTargetBalance(
      long tenantId, Object warehouse, Object bin, Object material, Object batch, Object state) {
    return db.queryForList(
        "SELECT id FROM inv_balance WHERE tenant_id=? AND warehouse_id=? AND bin_id=? AND"
            + " material_id=? AND batch_id=? AND stock_status=? FOR UPDATE",
        tenantId,
        warehouse,
        bin,
        material,
        batch,
        state);
  }

  public List<Map<String, Object>> loadBatch(long tenantId, Object batchId) {
    return db.queryForList("SELECT * FROM inv_batch WHERE tenant_id=? AND id=?", tenantId, batchId);
  }

  public List<Map<String, Object>> loadLayerSource(long tenantId, Object costLayerId) {
    return db.queryForList(
        "SELECT source_line_id FROM inv_cost_layer WHERE tenant_id=? AND id=?",
        tenantId,
        costLayerId);
  }

  public List<Map<String, Object>> loadOriginLayer(long tenantId, Object costLayerId) {
    return db.queryForList(
        "SELECT * FROM inv_cost_layer WHERE tenant_id=? AND id=?", tenantId, costLayerId);
  }

  public List<Map<String, Object>> lockLegacyStock(long tenantId, Object stock) {
    return db.queryForList(
        "SELECT * FROM eam_spare_stock WHERE tenant_id=? AND id=? FOR UPDATE", tenantId, stock);
  }

  public List<Map<String, Object>> loadReceiptLineSource(long tenantId, Object layerId) {
    return db.queryForList(
        "SELECT source_line_id FROM inv_cost_layer WHERE tenant_id=? AND id=?", tenantId, layerId);
  }

  public List<Map<String, Object>> lockLayer(long tenantId, Object layerId) {
    return db.queryForList(
        "SELECT * FROM inv_cost_layer WHERE tenant_id=? AND id=? FOR UPDATE", tenantId, layerId);
  }

  public List<Map<String, Object>> loadMovementLayer(long tenantId, Object costLayerId) {
    return db.queryForList(
        "SELECT * FROM inv_cost_layer WHERE tenant_id=? AND id=?", tenantId, costLayerId);
  }

  public List<Map<String, Object>> lockReceipt(long tenantId, Object receipt) {
    return db.queryForList(
        "SELECT * FROM inv_receipt WHERE tenant_id=? AND id=? FOR UPDATE", tenantId, receipt);
  }

  public List<Map<String, Object>> lockIssue(long tenantId, Object source) {
    return db.queryForList(
        "SELECT * FROM inv_issue WHERE tenant_id=? AND id=? FOR UPDATE", tenantId, source);
  }

  public List<Map<String, Object>> lockSourceOperation(long tenantId, Object source) {
    documents.lock(tenantId, source, null);
    return db.queryForList(
        "SELECT * FROM v_inv_operation WHERE tenant_id=? AND id=? AND deleted=0", tenantId, source);
  }

  public int insertTenantLock(long tenantId) {
    return db.update(
        "INSERT INTO inv_tenant_lock(tenant_id) VALUES(?) ON DUPLICATE KEY UPDATE"
            + " tenant_id=tenant_id",
        tenantId);
  }

  public Map<String, Object> lockTenant(long tenantId) {
    return db.queryForMap(
        "SELECT tenant_id FROM inv_tenant_lock WHERE tenant_id=? FOR UPDATE", tenantId);
  }

  public int insertEvent(
      long recordId,
      long tenantId,
      Object value,
      Object document,
      Object action,
      long userId,
      Object value7) {
    return db.update(
        "INSERT INTO"
            + " inv_document_event(id,tenant_id,document_kind,document_id,action,actor_id,actor_name)"
            + " VALUES(?,?,?,?,?,?,?)",
        recordId,
        tenantId,
        value,
        document,
        action,
        userId,
        value7);
  }

  public List<Map<String, Object>> listAllocations(long tenantId, Object doc) {
    return db.queryForList(
        "SELECT a.*,m.name material_name,b.name bin_name,pc.code batch_code FROM"
            + " inv_movement_allocation a JOIN inv_balance s ON s.tenant_id=a.tenant_id AND"
            + " s.id=a.balance_id JOIN inv_material m ON m.tenant_id=s.tenant_id AND"
            + " m.id=s.material_id JOIN inv_bin b ON b.tenant_id=s.tenant_id AND"
            + " b.id=s.bin_id LEFT JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND"
            + " pc.id=s.batch_id WHERE a.tenant_id=? AND a.document_id=? ORDER BY a.id",
        tenantId,
        doc);
  }

  public List<Map<String, Object>> listEvents(long tenantId, Object doc) {
    return db.queryForList(
        "SELECT action,actor_name,created_at FROM inv_document_event WHERE tenant_id=? AND"
            + " document_id=? ORDER BY created_at,id",
        tenantId,
        doc);
  }

  public List<Map<String, Object>> listStock(long tenantId) {
    return db.queryForList(
        "SELECT s.*,m.code material_code,m.name material_name,m.unit,b.name bin_name,pc.code"
            + " batch_code FROM inv_balance s JOIN inv_material m ON m.tenant_id=s.tenant_id"
            + " AND m.id=s.material_id JOIN inv_bin b ON b.tenant_id=s.tenant_id AND"
            + " b.id=s.bin_id LEFT JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND"
            + " pc.id=s.batch_id WHERE s.tenant_id=? ORDER BY"
            + " s.warehouse_id,m.code,b.code,s.id",
        tenantId);
  }

  public List<Map<String, Object>> listReturnableAllocations(long tenantId) {
    return db.queryForList(
        "SELECT a.*,a.quantity-a.used_quantity-a.returned_quantity available_quantity,i.code"
            + " issue_code,i.warehouse_id,i.company_id,COALESCE(ol.task_kind,r.task_kind)"
            + " task_kind,COALESCE(ol.task_code,r.task_code) task_code,m.name"
            + " material_name,m.unit,s.material_id,s.batch_id,pc.code batch_code FROM"
            + " inv_issue_allocation a JOIN inv_issue i ON i.tenant_id=a.tenant_id AND"
            + " i.id=a.issue_id LEFT JOIN inv_outbound_line ol ON ol.tenant_id=a.tenant_id AND"
            + " ol.id=a.outbound_line_id LEFT JOIN inv_requisition_line rl ON"
            + " rl.tenant_id=a.tenant_id AND rl.id=a.requisition_line_id LEFT JOIN"
            + " inv_requisition r ON r.tenant_id=rl.tenant_id AND r.id=rl.requisition_id JOIN"
            + " inv_balance s ON s.tenant_id=a.tenant_id AND s.id=a.balance_id JOIN"
            + " inv_material m ON m.tenant_id=s.tenant_id AND m.id=s.material_id LEFT JOIN"
            + " inv_batch pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id WHERE"
            + " a.tenant_id=? AND i.reversed=0 AND"
            + " a.quantity>a.used_quantity+a.returned_quantity ORDER BY i.created_at DESC,a.id",
        tenantId);
  }

  public List<Map<String, Object>> listReceipts(long tenantId) {
    return db.queryForList(
        "SELECT id,code,warehouse_id FROM inv_receipt WHERE tenant_id=? AND status='已入库'"
            + " AND deleted=0",
        tenantId);
  }

  public List<Map<String, Object>> listReversalSources(
      long tenantId, long tenantId2, long tenantId3) {
    return db.queryForList(
        "SELECT id,code,warehouse_id,'receipt' source_kind FROM inv_receipt WHERE tenant_id=?"
            + " AND status='已入库' AND deleted=0 UNION ALL SELECT id,code,warehouse_id,'issue'"
            + " source_kind FROM inv_issue WHERE tenant_id=? AND reversed=0 AND status='已出库'"
            + " AND deleted=0 UNION ALL SELECT id,code,warehouse_id,kind source_kind FROM"
            + " v_inv_operation WHERE tenant_id=? AND status IN ('已完成','在途') AND"
            + " kind<>'reversals' AND deleted=0",
        tenantId,
        tenantId2,
        tenantId3);
  }

  public List<Map<String, Object>> listLegacyStock(long tenantId) {
    return db.queryForList(
        "SELECT s.*,COALESCE(x.migration_status,'待初始化') migration_status FROM"
            + " eam_spare_stock s LEFT JOIN inv_legacy_stock_map x ON"
            + " x.tenant_id=s.tenant_id AND x.stock_id=s.id WHERE s.tenant_id=? AND"
            + " (x.stock_id IS NULL OR x.migration_status='待初始化')",
        tenantId);
  }

  public List<Map<String, Object>> listCompanyWarehouses(long tenantId, Object companyId) {
    return db.queryForList(
        "SELECT id FROM inv_warehouse WHERE tenant_id=? AND company_id=?", tenantId, companyId);
  }

  public int countMaterialStock(long tenantId, Object material) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_balance WHERE tenant_id=? AND material_id=? AND" + " quantity>0",
        Integer.class,
        tenantId,
        material);
  }

  public List<Map<String, Object>> listCountStock(long tenantId, Object warehouseId) {
    return db.queryForList(
        "SELECT s.*,m.name material_name,b.name bin_name,pc.code batch_code FROM"
            + " inv_balance s JOIN inv_material m ON m.tenant_id=s.tenant_id AND"
            + " m.id=s.material_id JOIN inv_bin b ON b.tenant_id=s.tenant_id AND"
            + " b.id=s.bin_id LEFT JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND"
            + " pc.id=s.batch_id WHERE s.tenant_id=? AND s.warehouse_id=? ORDER BY s.id FOR"
            + " UPDATE",
        tenantId,
        warehouseId);
  }

  public int insertOperation(
      Object record,
      long tenantId,
      Object kind,
      Object value,
      Object companyId,
      Object warehouseId,
      Object target,
      String payload,
      long userId) {
    return documents.insert(
        record, tenantId, kind, value, companyId, warehouseId, target, payload, userId);
  }

  public int updateOperation(
      Object warehouseId,
      Object companyId,
      Object target,
      String payload,
      long tenantId,
      Object record) {
    return documents.update(tenantId, record, companyId, warehouseId, target, payload);
  }

  public List<Map<String, Object>> lockPersonalReturnAllocation(long tenantId, Object allocation) {
    return db.queryForList(
        com.windasoft.eam.repository.experience.MaterialCustodyRepository.SELECT
            + " AND a.id=? FOR UPDATE",
        tenantId,
        allocation);
  }

  public List<Map<String, Object>> findPendingReturn(long tenantId, Object value) {
    return db.queryForList(
        "SELECT d.id,d.code FROM inv_return d WHERE d.tenant_id=? AND d.status='草稿' AND d.deleted=0"
            + " AND EXISTS(SELECT 1 FROM inv_return_line l WHERE l.tenant_id=d.tenant_id AND"
            + " l.document_id=d.id AND l.allocation_id=?)",
        tenantId,
        value);
  }

  public List<Map<String, Object>> listEnabledBins(long tenantId, Object warehouse) {
    return db.queryForList(
        "SELECT id FROM inv_bin WHERE tenant_id=? AND warehouse_id=? AND enabled=1 ORDER BY id"
            + " LIMIT 1",
        tenantId,
        warehouse);
  }

  public int insertPersonalReturn(
      Object doc,
      long tenantId,
      Object code,
      Object companyId,
      Object warehouse,
      String payload,
      long userId) {
    return documents.insert(
        doc, tenantId, "returns", code, companyId, warehouse, null, payload, userId);
  }

  public List<Map<String, Object>> listActiveUsers(long tenantId) {
    return db.queryForList(
        "SELECT id FROM eam_sys_user WHERE tenant_id=? AND status=1 AND deleted=0", tenantId);
  }

  public int insertReturnTodo(
      long recordId, long tenantId, Object recipient, Object doc, Object value) {
    int count =
        db.update(
            "INSERT INTO inv_inbox(id,tenant_id,user_id,document_id,route_kind,kind,title,is_todo)"
                + " VALUES(?,?,?,?,'returns','待退料入库',?,1)",
            recordId,
            tenantId,
            recipient,
            doc,
            value);
    db.update(
        "UPDATE inv_inbox SET message_key=?,message_args=? WHERE tenant_id=? AND id=?",
        com.windasoft.eam.common.NotificationMessages.key("待退料入库"),
        com.windasoft.eam.common.NotificationMessages.arguments("待退料入库", value),
        tenantId,
        recordId);
    return count;
  }

  public int ensureBalance(
      long recordId,
      long tenantId,
      Object companyId,
      Object warehouse,
      Object bin,
      Object material,
      Object batch,
      Object state) {
    return db.update(
        "INSERT INTO"
            + " inv_balance(id,tenant_id,company_id,warehouse_id,bin_id,material_id,batch_id,stock_status)"
            + " VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
        recordId,
        tenantId,
        companyId,
        warehouse,
        bin,
        material,
        batch,
        state);
  }

  public int insertTransaction(
      long recordId,
      long tenantId,
      Object companyId,
      Object warehouseId,
      Object id,
      Object doc,
      Object allocation,
      Object kind,
      Object qty,
      Object amount,
      long userId) {
    return db.update(
        "INSERT INTO"
            + " inv_transaction(id,tenant_id,company_id,warehouse_id,balance_id,document_id,source_line_id,kind,quantity,amount,actor_id)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        recordId,
        tenantId,
        companyId,
        warehouseId,
        id,
        doc,
        allocation,
        kind,
        qty,
        amount,
        userId);
  }

  public int addBalance(Object qty, long tenantId, Object balance) {
    return db.update(
        "UPDATE inv_balance SET quantity=quantity+?,row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        qty,
        tenantId,
        balance);
  }

  public int insertCostLayer(
      Object layer,
      long tenantId,
      Object balance,
      Object source,
      Object value,
      Object received,
      Object qty,
      Object qty8,
      Object unit,
      Object amount) {
    return db.update(
        "INSERT INTO"
            + " inv_cost_layer(id,tenant_id,balance_id,source_line_id,origin_layer_id,origin_received_at,quantity,remaining_quantity,unit_cost,remaining_cost)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
        layer,
        tenantId,
        balance,
        source,
        value,
        received,
        qty,
        qty8,
        unit,
        amount);
  }

  public int insertMovement(
      Object allocation,
      long tenantId,
      Object doc,
      Object balance,
      Object value,
      Object layer,
      Object qty,
      Object amount,
      Object received,
      Object unit) {
    return db.update(
        "INSERT INTO"
            + " inv_movement_allocation(id,tenant_id,document_id,balance_id,cost_layer_id,target_layer_id,quantity,amount,origin_received_at,unit_cost)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
        allocation,
        tenantId,
        doc,
        balance,
        value,
        layer,
        qty,
        amount,
        received,
        unit);
  }

  public List<Map<String, Object>> lockLayers(long tenantId, Object id) {
    return db.queryForList(
        "SELECT * FROM inv_cost_layer WHERE tenant_id=? AND balance_id=? AND"
            + " remaining_quantity>0 ORDER BY origin_received_at,id FOR UPDATE",
        tenantId,
        id);
  }

  public int consumeLayer(Object taken, Object amount, long tenantId, Object id) {
    return db.update(
        "UPDATE inv_cost_layer SET"
            + " remaining_quantity=remaining_quantity-?,remaining_cost=remaining_cost-? WHERE"
            + " tenant_id=? AND id=?",
        taken,
        amount,
        tenantId,
        id);
  }

  public int insertOutgoingMovement(
      Object allocation,
      long tenantId,
      Object doc,
      Object id,
      Object id5,
      Object targetBin,
      Object value,
      Object value8,
      Object originReceivedAt,
      Object unit) {
    return db.update(
        "INSERT INTO"
            + " inv_movement_allocation(id,tenant_id,document_id,balance_id,cost_layer_id,target_bin_id,quantity,amount,origin_received_at,unit_cost)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
        allocation,
        tenantId,
        doc,
        id,
        id5,
        targetBin,
        value,
        value8,
        originReceivedAt,
        unit);
  }

  public int takeBalance(Object qty, long tenantId, Object id) {
    return db.update(
        "UPDATE inv_balance SET quantity=quantity-?,row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        qty,
        tenantId,
        id);
  }

  public List<Map<String, Object>> lockOutgoingMovements(long tenantId, Object doc) {
    return db.queryForList(
        "SELECT * FROM inv_movement_allocation WHERE tenant_id=? AND document_id=? AND"
            + " quantity<0 ORDER BY id FOR UPDATE",
        tenantId,
        doc);
  }

  public int setMovementTarget(Object targetLayer, long tenantId, Object id) {
    return db.update(
        "UPDATE inv_movement_allocation SET target_layer_id=? WHERE tenant_id=? AND id=?",
        targetLayer,
        tenantId,
        id);
  }

  public int addReturnedQuantity(Object qty, Object amount, long tenantId, Object id) {
    return db.update(
        "UPDATE inv_issue_allocation SET"
            + " returned_quantity=returned_quantity+?,returned_amount=returned_amount+?"
            + " WHERE tenant_id=? AND id=?",
        qty,
        amount,
        tenantId,
        id);
  }

  public List<Map<String, Object>> lockLegacyMapping(long tenantId, Object stock) {
    return db.queryForList(
        "SELECT * FROM inv_legacy_stock_map WHERE tenant_id=? AND stock_id=? FOR" + " UPDATE",
        tenantId,
        stock);
  }

  public int countLegacyWaiting(long tenantId, Object stock) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_repair_material WHERE tenant_id=? AND"
            + " stock_id=? AND status='待出库'",
        Integer.class,
        tenantId,
        stock);
  }

  public int insertLegacyMapping(
      long tenantId, Object stock, Object materialId, Object warehouse, Object id) {
    return db.update(
        "INSERT INTO"
            + " inv_legacy_stock_map(tenant_id,stock_id,material_id,warehouse_id,opening_line_id,migration_status)"
            + " VALUES(?,?,?,?,?,'已初始化') ON DUPLICATE KEY UPDATE"
            + " material_id=VALUES(material_id),warehouse_id=VALUES(warehouse_id),opening_line_id=VALUES(opening_line_id),migration_status='已初始化'",
        tenantId,
        stock,
        materialId,
        warehouse,
        id);
  }

  public int clearLegacyQuantity(long tenantId, Object stock) {
    return db.update(
        "UPDATE eam_spare_stock SET quantity=0 WHERE tenant_id=? AND id=?", tenantId, stock);
  }

  public int closeTransferTodos(long tenantId, Object doc) {
    return db.update(
        "UPDATE inv_inbox SET done=1 WHERE tenant_id=? AND document_id=? AND"
            + " route_kind='transfers' AND is_todo=1",
        tenantId,
        doc);
  }

  public List<Map<String, Object>> listNotifyUsers(long tenantId) {
    return db.queryForList(
        "SELECT id FROM eam_sys_user WHERE tenant_id=? AND status=1 AND deleted=0", tenantId);
  }

  public int insertTransferTodo(
      long recordId, long tenantId, Object user, Object doc, Object value) {
    int count =
        db.update(
            "INSERT INTO"
                + " inv_inbox(id,tenant_id,user_id,document_id,route_kind,kind,title,is_todo)"
                + " VALUES(?,?,?,?,'transfers','待收货',?,1)",
            recordId,
            tenantId,
            user,
            doc,
            value);
    db.update(
        "UPDATE inv_inbox SET message_key=?,message_args=? WHERE tenant_id=? AND id=?",
        com.windasoft.eam.common.NotificationMessages.key("待收货"),
        com.windasoft.eam.common.NotificationMessages.arguments("待收货", value),
        tenantId,
        recordId);
    return count;
  }

  public int confirmOperation(Object status, String payload, long tenantId, Object doc) {
    return documents.confirm(tenantId, doc, status, payload);
  }

  public int completeReturnTodos(long tenantId, Object doc) {
    return db.update(
        "UPDATE inv_inbox SET done=1 WHERE tenant_id=? AND document_id=? AND"
            + " route_kind='returns'",
        tenantId,
        doc);
  }

  public int insertReturnNotice(
      long recordId, long tenantId, Object surplusRequestedBy, Object doc, Object value) {
    int count =
        db.update(
            "INSERT INTO inv_inbox(id,tenant_id,user_id,document_id,route_kind,kind,title,is_todo)"
                + " VALUES(?,?,?,?,'returns','退料已入库',?,0)",
            recordId,
            tenantId,
            surplusRequestedBy,
            doc,
            value);
    db.update(
        "UPDATE inv_inbox SET message_key=?,message_args=? WHERE tenant_id=? AND id=?",
        com.windasoft.eam.common.NotificationMessages.key("退料已入库"),
        com.windasoft.eam.common.NotificationMessages.arguments("退料已入库", value),
        tenantId,
        recordId);
    return count;
  }

  public int countReservedMaterial(long tenantId, Object material) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_balance WHERE tenant_id=? AND material_id=? AND"
            + " reserved_quantity>0",
        Integer.class,
        tenantId,
        material);
  }

  public int countDraftReceipts(long tenantId, Object material) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_receipt_line l JOIN inv_receipt r ON"
            + " r.tenant_id=l.tenant_id AND r.id=l.receipt_id WHERE l.tenant_id=? AND"
            + " l.material_id=? AND r.status='草稿' AND r.deleted=0",
        Integer.class,
        tenantId,
        material);
  }

  public int countOpenRequisitions(long tenantId, Object material) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_requisition_line l JOIN inv_requisition r ON"
            + " r.tenant_id=l.tenant_id AND r.id=l.requisition_id WHERE l.tenant_id=? AND"
            + " l.material_id=? AND r.status NOT IN ('已完成','已关闭','已取消') AND r.deleted=0",
        Integer.class,
        tenantId,
        material);
  }

  public int countOpenOperations(long tenantId, Object except, Object value, Object material) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_inv_operation d WHERE d.tenant_id=? AND d.deleted=0 AND d.id<>? AND"
            + " d.status IN ('草稿','在途') AND (EXISTS(SELECT 1 FROM v_inv_operation_material l WHERE"
            + " l.tenant_id=d.tenant_id AND l.document_id=d.id AND l.material_id=?) OR"
            + " EXISTS(SELECT 1 FROM inv_movement_allocation a JOIN inv_balance b ON"
            + " b.tenant_id=a.tenant_id AND b.id=a.balance_id WHERE a.tenant_id=d.tenant_id AND"
            + " a.document_id=d.id AND b.material_id=?))",
        Integer.class,
        tenantId,
        except,
        value,
        material);
  }

  public int countStockForConversion(long tenantId, Object material) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_balance WHERE tenant_id=? AND material_id=? AND" + " quantity>0",
        Integer.class,
        tenantId,
        material);
  }

  public int countBatchCode(long tenantId, Object code) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_batch WHERE tenant_id=? AND code=?",
        Integer.class,
        tenantId,
        code);
  }

  public List<Map<String, Object>> lockBatch(long tenantId, Object code) {
    return db.queryForList(
        "SELECT * FROM inv_batch WHERE tenant_id=? AND code=? FOR UPDATE", tenantId, code);
  }

  public int insertBatch(
      Object batch, long tenantId, Object material, Object code, Object production, Object expiry) {
    return db.update(
        "INSERT INTO"
            + " inv_batch(id,tenant_id,material_id,code,supplier_batch,production_date,expiry_date)"
            + " VALUES(?,?,?,?,'',?,?)",
        batch,
        tenantId,
        material,
        code,
        production,
        expiry);
  }

  public int enableMaterialBatch(long tenantId, Object material) {
    return db.update(
        "UPDATE inv_material SET batch_enabled=1,row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        tenantId,
        material);
  }

  public int closeCostLayer(long tenantId, Object layerId) {
    return db.update(
        "UPDATE inv_cost_layer SET remaining_quantity=0,remaining_cost=0 WHERE tenant_id=? AND"
            + " id=?",
        tenantId,
        layerId);
  }

  public int takeBalanceForReversal(Object qty, long tenantId, Object id) {
    return db.update(
        "UPDATE inv_balance SET quantity=quantity-?,row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        qty,
        tenantId,
        id);
  }

  public int insertReversalMovement(
      Object allocation,
      long tenantId,
      Object doc,
      Object id,
      Object layerId,
      Object value,
      Object value7,
      Object originReceivedAt,
      Object unitCost) {
    return db.update(
        "INSERT INTO"
            + " inv_movement_allocation(id,tenant_id,document_id,balance_id,cost_layer_id,quantity,amount,origin_received_at,unit_cost)"
            + " VALUES(?,?,?,?,?,?,?,?,?)",
        allocation,
        tenantId,
        doc,
        id,
        layerId,
        value,
        value7,
        originReceivedAt,
        unitCost);
  }

  public List<Map<String, Object>> listReceiptLayers(long tenantId, Object receipt) {
    return db.queryForList(
        "SELECT c.* FROM inv_cost_layer c JOIN inv_receipt_line l ON l.tenant_id=c.tenant_id"
            + " AND l.id=c.source_line_id WHERE c.tenant_id=? AND l.receipt_id=? AND"
            + " c.origin_layer_id IS NULL ORDER BY c.balance_id,c.id FOR UPDATE",
        tenantId,
        receipt);
  }

  public int reverseReceipt(long tenantId, Object receipt) {
    return db.update(
        "UPDATE inv_receipt SET status='已冲销',row_version=row_version+1 WHERE tenant_id=? AND id=?",
        tenantId,
        receipt);
  }

  public int reverseOpening(long tenantId, Object value) {
    return documents.reverseOpening(tenantId, value);
  }

  public List<Map<String, Object>> lockIssueAllocations(long tenantId, Object source) {
    return db.queryForList(
        "SELECT * FROM inv_issue_allocation WHERE tenant_id=? AND issue_id=? ORDER BY id FOR"
            + " UPDATE",
        tenantId,
        source);
  }

  public int restoreRequestedQuantity(Object quantity, long tenantId, Object requisitionLineId) {
    return db.update(
        "UPDATE inv_requisition_line SET issued_quantity=issued_quantity-? WHERE tenant_id=?"
            + " AND id=?",
        quantity,
        tenantId,
        requisitionLineId);
  }

  public int markIssueReversed(long tenantId, Object source) {
    return db.update(
        "UPDATE inv_issue SET reversed=1 WHERE tenant_id=? AND id=?", tenantId, source);
  }

  public List<Map<String, Object>> listAffectedRequests(long tenantId, Object source) {
    return db.queryForList(
        "SELECT DISTINCT r.* FROM inv_requisition r JOIN inv_requisition_line l ON"
            + " l.tenant_id=r.tenant_id AND l.requisition_id=r.id JOIN inv_issue_allocation a"
            + " ON a.tenant_id=l.tenant_id AND a.requisition_line_id=l.id WHERE a.tenant_id=?"
            + " AND a.issue_id=? ORDER BY r.id FOR UPDATE",
        tenantId,
        source);
  }

  public int restoreRequestStatus(long tenantId, Object id) {
    return db.update(
        "UPDATE inv_requisition SET status=IF(EXISTS(SELECT 1 FROM inv_requisition_line l"
            + " WHERE l.tenant_id=inv_requisition.tenant_id AND"
            + " l.requisition_id=inv_requisition.id AND"
            + " l.issued_quantity>0),'部分发料','待预留'),row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=?",
        tenantId,
        id);
  }

  public int reopenRequestTodos(long tenantId, Object id) {
    return db.update(
        "UPDATE inv_inbox SET done=0 WHERE tenant_id=? AND document_id=? AND"
            + " route_kind='requisitions' AND is_todo=1",
        tenantId,
        id);
  }

  public int reverseIssue(long tenantId, Object source) {
    return db.update(
        "UPDATE inv_issue SET status='已冲销',row_version=row_version+1 WHERE tenant_id=? AND id=?",
        tenantId,
        source);
  }

  public List<Map<String, Object>> lockMovements(long tenantId, Object source) {
    return db.queryForList(
        "SELECT * FROM inv_movement_allocation WHERE tenant_id=? AND document_id=? ORDER BY"
            + " id FOR UPDATE",
        tenantId,
        source);
  }

  public int restoreReturnedQuantity(Object q, Object amount, long tenantId, Object id) {
    return db.update(
        "UPDATE inv_issue_allocation SET"
            + " returned_quantity=returned_quantity-?,returned_amount=returned_amount-? WHERE"
            + " tenant_id=? AND id=?",
        q,
        amount,
        tenantId,
        id);
  }

  public int reverseSource(long tenantId, Object source) {
    return documents.reverse(tenantId, source);
  }

  public int completeSourceTodos(long tenantId, Object source) {
    return db.update(
        "UPDATE inv_inbox SET done=1 WHERE tenant_id=? AND document_id=? AND is_todo=1",
        tenantId,
        source);
  }

  public int deleteOperation(long tenantId, Object doc) {
    return documents.delete(tenantId, doc);
  }

  public int moveCostLayers(Object destination, long tenantId, Object balanceId) {
    return db.update(
        "UPDATE inv_cost_layer SET balance_id=? WHERE tenant_id=? AND balance_id=? AND"
            + " remaining_quantity>0",
        destination,
        tenantId,
        balanceId);
  }

  public int addMergedBalance(Object qty, long tenantId, Object destination) {
    return db.update(
        "UPDATE inv_balance SET quantity=quantity+?,row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        qty,
        tenantId,
        destination);
  }

  public int clearMergedBalance(long tenantId, Object balanceId) {
    return db.update(
        "UPDATE inv_balance SET quantity=0,row_version=row_version+1 WHERE tenant_id=? AND id=?",
        tenantId,
        balanceId);
  }

  public int insertMasterEvent(
      long recordId,
      long tenantId,
      Object balanceId,
      Object target,
      long userId,
      Object value,
      String payload) {
    return db.update(
        "INSERT INTO"
            + " inv_master_event(id,tenant_id,resource_kind,resource_id,action,actor_id,actor_name,details_json)"
            + " VALUES(?,?,'balances',?,?,?,?,?)",
        recordId,
        tenantId,
        balanceId,
        target,
        userId,
        value,
        payload);
  }
}
