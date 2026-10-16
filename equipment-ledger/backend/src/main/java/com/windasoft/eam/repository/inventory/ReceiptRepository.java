package com.windasoft.eam.repository.inventory;

import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReceiptRepository {
  private final JdbcTemplate db;
  private final NumberCounterMapper counter;
  private static final String MATERIAL_WHERE =
      " WHERE tenant_id=? AND enabled=1 AND (code LIKE ? OR name LIKE ? OR model LIKE ? OR category"
          + " LIKE ?)";

  public ReceiptRepository(JdbcTemplate db, NumberCounterMapper counter) {
    this.db = db;
    this.counter = counter;
  }

  public void nextMonthlyNumber(String rule, String period) {
    counter.nextMonthlyNumber(rule, period);
  }

  public long currentNumber() {
    return counter.currentValue();
  }

  public List<Map<String, Object>> loadReceipt(long tenantId, Object recordId, boolean lock) {
    return db.queryForList(
        "SELECT * FROM inv_receipt WHERE tenant_id=? AND id=? AND deleted=0"
            + (lock ? " FOR UPDATE" : ""),
        tenantId,
        recordId);
  }

  public int countCode(long tenantId, Object code, String table) {
    if (!Set.of("inv_receipt", "inv_batch").contains(table))
      throw new IllegalArgumentException("未知入库编号类型");
    return db.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND code=?",
        Integer.class,
        tenantId,
        code);
  }

  public int insertEvent(
      long recordId, long tenantId, Object document, Object action, long userId, Object value) {
    return db.update(
        "INSERT INTO"
            + " inv_document_event(id,tenant_id,document_kind,document_id,action,actor_id,actor_name)"
            + " VALUES(?,?,'入库',?,?,?,?)",
        recordId,
        tenantId,
        document,
        action,
        userId,
        value);
  }

  public List<Map<String, Object>> listLines(long tenantId, Object recordId) {
    return db.queryForList(
        "SELECT l.*,m.code material_code,m.name"
            + " material_name,m.unit,m.batch_enabled,m.expiry_enabled,b.name bin_name FROM"
            + " inv_receipt_line l JOIN inv_material m ON m.id=l.material_id AND"
            + " m.tenant_id=l.tenant_id JOIN inv_bin b ON b.id=l.bin_id AND"
            + " b.tenant_id=l.tenant_id WHERE l.tenant_id=? AND l.receipt_id=? ORDER BY"
            + " l.line_no",
        tenantId,
        recordId);
  }

  public List<Map<String, Object>> listEvents(long tenantId, Object recordId) {
    return db.queryForList(
        "SELECT action,actor_name,created_at FROM inv_document_event WHERE tenant_id=? AND"
            + " document_kind='入库' AND document_id=? ORDER BY created_at,id",
        tenantId,
        recordId);
  }

  public List<Map<String, Object>> searchReceipts(
      long tenantId, Object value, Object value3, Object value4) {
    return db.queryForList(
        "SELECT r.*,DATE_FORMAT(r.created_at,'%Y-%m-%d %H:%i:%s')"
            + " fmt_created_at,DATE_FORMAT(r.confirmed_at,'%Y-%m-%d %H:%i:%s')"
            + " fmt_confirmed_at,w.name warehouse_name,COALESCE((SELECT e.actor_name FROM"
            + " inv_document_event e WHERE e.tenant_id=r.tenant_id AND e.document_kind='入库'"
            + " AND e.document_id=r.id ORDER BY e.created_at DESC,e.id DESC LIMIT 1),"
            + " (SELECT u.display_name FROM eam_sys_user u WHERE u.tenant_id=r.tenant_id"
            + " AND u.id=r.created_by),'') actor_name FROM inv_receipt r JOIN inv_warehouse w"
            + " ON w.tenant_id=r.tenant_id AND w.id=r.warehouse_id WHERE r.tenant_id=? AND"
            + " r.deleted=0 AND (r.code LIKE ? OR r.supplier LIKE ? OR w.name LIKE ?) ORDER BY"
            + " r.created_at DESC,r.id DESC",
        tenantId, value, value3, value4);
  }

  public long countMaterials(long tenant, String match) {
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

  public List<Map<String, Object>> listRawLines(long tenantId, Object recordId) {
    return db.queryForList(
        "SELECT * FROM inv_receipt_line WHERE tenant_id=? AND receipt_id=?", tenantId, recordId);
  }

  public int insertReceipt(
      Object target,
      long tenantId,
      Object value,
      Object companyId,
      Object warehouseId,
      Object value6,
      Object value7,
      long userId) {
    return db.update(
        "INSERT INTO"
            + " inv_receipt(id,tenant_id,code,company_id,warehouse_id,supplier,remark,created_by)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        target,
        tenantId,
        value,
        companyId,
        warehouseId,
        value6,
        value7,
        userId);
  }

  public int updateReceipt(
      Object companyId,
      Object warehouseId,
      Object value,
      Object value4,
      long tenantId,
      Object target) {
    return db.update(
        "UPDATE inv_receipt SET"
            + " company_id=?,warehouse_id=?,supplier=?,remark=?,row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=?",
        companyId,
        warehouseId,
        value,
        value4,
        tenantId,
        target);
  }

  public int deleteLines(long tenantId, Object target) {
    return db.update(
        "DELETE FROM inv_receipt_line WHERE tenant_id=? AND receipt_id=?", tenantId, target);
  }

  public List<Map<String, Object>> listEnabledBins(long tenantId, Object warehouseId) {
    return db.queryForList(
        "SELECT id FROM inv_bin WHERE tenant_id=? AND warehouse_id=? AND enabled=1",
        tenantId,
        warehouseId);
  }

  public int insertLine(
      long recordId,
      long tenantId,
      Object target,
      Object value,
      Object id,
      Object id6,
      Object qty,
      Object price,
      Object fee,
      Object value10,
      Object value11,
      Object production,
      Object expiry,
      Object taxRate,
      Object value15) {
    return db.update(
        "INSERT INTO"
            + " inv_receipt_line(id,tenant_id,receipt_id,line_no,material_id,bin_id,quantity,unit_price,allocated_fee,batch_code,supplier_batch,production_date,expiry_date,tax_rate,currency)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        recordId,
        tenantId,
        target,
        value,
        id,
        id6,
        qty,
        price,
        fee,
        value10,
        value11,
        production,
        expiry,
        taxRate,
        value15);
  }

  public List<Map<String, Object>> lockLines(long tenantId, Object recordId) {
    return db.queryForList(
        "SELECT * FROM inv_receipt_line WHERE tenant_id=? AND receipt_id=? ORDER BY"
            + " material_id,bin_id,id FOR UPDATE",
        tenantId,
        recordId);
  }

  public List<Map<String, Object>> lockBatch(long tenantId, Object batchCode) {
    return db.queryForList(
        "SELECT * FROM inv_batch WHERE tenant_id=? AND code=? FOR UPDATE", tenantId, batchCode);
  }

  public int insertBatch(
      Object batchId,
      long tenantId,
      Object id,
      Object batchCode,
      Object supplierBatch,
      Object productionDate,
      Object expiryDate) {
    return db.update(
        "INSERT INTO"
            + " inv_batch(id,tenant_id,material_id,code,supplier_batch,production_date,expiry_date)"
            + " VALUES(?,?,?,?,?,?,?)",
        batchId,
        tenantId,
        id,
        batchCode,
        supplierBatch,
        productionDate,
        expiryDate);
  }

  public int ensureBalance(
      Object candidate,
      long tenantId,
      Object companyId,
      Object warehouseId,
      Object id,
      Object id6,
      Object batchId) {
    return db.update(
        "INSERT INTO"
            + " inv_balance(id,tenant_id,company_id,warehouse_id,bin_id,material_id,batch_id)"
            + " VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
        candidate,
        tenantId,
        companyId,
        warehouseId,
        id,
        id6,
        batchId);
  }

  public Map<String, Object> lockBalance(
      long tenantId, Object warehouseId, Object id, Object id4, Object batchId) {
    return db.queryForMap(
        "SELECT * FROM inv_balance WHERE tenant_id=? AND warehouse_id=? AND bin_id=? AND"
            + " material_id=? AND batch_id=? AND stock_status='可用' FOR UPDATE",
        tenantId,
        warehouseId,
        id,
        id4,
        batchId);
  }

  public int addBalance(Object qty, long tenantId, Object id) {
    return db.update(
        "UPDATE inv_balance SET quantity=quantity+?,row_version=row_version+1 WHERE tenant_id=?"
            + " AND id=?",
        qty,
        tenantId,
        id);
  }

  public int insertCostLayer(
      long recordId,
      long tenantId,
      Object id,
      Object id4,
      Object qty,
      Object qty6,
      Object unitCost,
      Object amount) {
    return db.update(
        "INSERT INTO"
            + " inv_cost_layer(id,tenant_id,balance_id,source_line_id,origin_received_at,quantity,remaining_quantity,unit_cost,remaining_cost)"
            + " VALUES(?,?,?,?,CURRENT_TIMESTAMP(3),?,?,?,?)",
        recordId,
        tenantId,
        id,
        id4,
        qty,
        qty6,
        unitCost,
        amount);
  }

  public int insertTransaction(
      long recordId,
      long tenantId,
      Object companyId,
      Object warehouseId,
      Object id,
      Object recordId6,
      Object id7,
      Object qty,
      Object amount,
      long userId) {
    return db.update(
        "INSERT INTO"
            + " inv_transaction(id,tenant_id,company_id,warehouse_id,balance_id,document_id,source_line_id,kind,quantity,amount,actor_id)"
            + " VALUES(?,?,?,?,?,?,?,'入库',?,?,?)",
        recordId,
        tenantId,
        companyId,
        warehouseId,
        id,
        recordId6,
        id7,
        qty,
        amount,
        userId);
  }

  public int updateLineCost(
      Object batchId,
      Object batchCode,
      Object amount,
      Object taxRate,
      Object taxRate5,
      long tenantId,
      Object id) {
    return db.update(
        "UPDATE inv_receipt_line SET"
            + " batch_id=?,batch_code=?,total_cost=?,tax_amount=?,gross_amount=? WHERE"
            + " tenant_id=? AND id=?",
        batchId,
        batchCode,
        amount,
        taxRate,
        taxRate5,
        tenantId,
        id);
  }

  public int confirmReceipt(long tenantId, Object recordId) {
    return db.update(
        "UPDATE inv_receipt SET"
            + " status='已入库',confirmed_at=CURRENT_TIMESTAMP(3),row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=?",
        tenantId,
        recordId);
  }

  public int deleteReceipt(long tenantId, Object recordId) {
    return db.update(
        "UPDATE inv_receipt SET deleted=1,row_version=row_version+1 WHERE tenant_id=? AND id=?",
        tenantId,
        recordId);
  }

  public List<Map<String, Object>> lockLinesForFees(long tenantId, Object recordId) {
    return db.queryForList(
        "SELECT * FROM inv_receipt_line WHERE tenant_id=? AND receipt_id=? ORDER BY line_no FOR"
            + " UPDATE",
        tenantId,
        recordId);
  }

  public int updateLineFee(Object allocated, long tenantId, Object id) {
    return db.update(
        "UPDATE inv_receipt_line SET allocated_fee=? WHERE tenant_id=? AND id=?",
        allocated,
        tenantId,
        id);
  }

  public int updateVersion(long tenantId, Object recordId) {
    return db.update(
        "UPDATE inv_receipt SET row_version=row_version+1 WHERE tenant_id=? AND id=?",
        tenantId,
        recordId);
  }
}
