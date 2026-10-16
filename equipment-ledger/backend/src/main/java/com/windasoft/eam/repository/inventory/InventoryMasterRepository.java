package com.windasoft.eam.repository.inventory;

import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryMasterRepository {
  private final JdbcTemplate db;
  private final NumberCounterMapper counter;

  public InventoryMasterRepository(JdbcTemplate db, NumberCounterMapper counter) {
    this.db = db;
    this.counter = counter;
  }

  public void nextMonthlyNumber(String rule, String period) {
    counter.nextMonthlyNumber(rule, period);
  }

  public long currentNumber() {
    return counter.currentValue();
  }

  public void validateKind(String kind) {
    table(kind);
  }

  private void validateFields(String kind, Map<String, Object> fields) {
    var allowed = new HashSet<>(Set.of("code", "name", "remark", "enabled"));
    switch (kind) {
      case "materials" ->
          allowed.addAll(
              Set.of(
                  "model",
                  "unit",
                  "category",
                  "usage_type",
                  "reference_price",
                  "batch_enabled",
                  "expiry_enabled",
                  "allocation_rule"));
      case "warehouses" -> allowed.addAll(Set.of("company_id", "manager_id", "allocation_rule"));
      case "bins" -> allowed.add("warehouse_id");
      default -> throw new IllegalArgumentException("未知库存档案");
    }
    if (!allowed.containsAll(fields.keySet())) throw new IllegalArgumentException("库存档案字段无效");
  }

  public static String table(String kind) {
    return switch (kind) {
      case "materials" -> "inv_material";
      case "warehouses" -> "inv_warehouse";
      case "bins" -> "inv_bin";
      default -> throw new IllegalArgumentException("未知库存档案");
    };
  }

  public List<Map<String, Object>> loadMaster(
      long tenantId, Object recordId, String kind, boolean lock) {
    return db.queryForList(
        "SELECT * FROM "
            + table(kind)
            + " WHERE tenant_id=? AND id=?"
            + (lock ? " FOR UPDATE" : ""),
        tenantId,
        recordId);
  }

  public int countCompany(long tenantId, Object companyId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_org_unit WHERE tenant_id=? AND id=? AND org_type='公司' AND"
            + " deleted=0 AND status=1",
        Integer.class,
        tenantId,
        companyId);
  }

  public List<Map<String, Object>> listMasters(long tenantId, String kind) {
    return db.queryForList(
        "SELECT * FROM " + table(kind) + " WHERE tenant_id=? ORDER BY code,id", tenantId);
  }

  public List<Map<String, Object>> lookupParent(long tenantId, Object parent, String source) {
    if (!Set.of("eam_org_unit", "inv_warehouse").contains(source))
      throw new IllegalArgumentException("未知父档案");
    return db.queryForList(
        "SELECT name FROM " + source + " WHERE tenant_id=? AND id=?", tenantId, parent);
  }

  public List<Map<String, Object>> findUserName(long tenantId, Object managerId) {
    return db.queryForList(
        "SELECT display_name FROM eam_sys_user WHERE tenant_id=? AND id=?", tenantId, managerId);
  }

  public List<Map<String, Object>> findFirstBin(long tenantId, Object warehouseId) {
    return db.queryForList(
        "SELECT id FROM inv_bin WHERE tenant_id=? AND warehouse_id=? ORDER BY id LIMIT 1",
        tenantId,
        warehouseId);
  }

  public int insertDefaultBin(Object binId, long tenantId, Object warehouseId) {
    return db.update(
        "INSERT INTO inv_bin(id,tenant_id,warehouse_id,code,name) VALUES(?,?,?,'DEFAULT','默认库位')",
        binId,
        tenantId,
        warehouseId);
  }

  public List<Map<String, Object>> listCompanies(long tenantId) {
    return db.queryForList(
        "SELECT id,name FROM eam_org_unit WHERE tenant_id=? AND org_type='公司' AND deleted=0 AND"
            + " status=1 ORDER BY name",
        tenantId);
  }

  public List<Map<String, Object>> listCompanyWarehouseIds(long tenantId, Object company) {
    return db.queryForList(
        "SELECT id FROM inv_warehouse WHERE tenant_id=? AND company_id=?", tenantId, company);
  }

  public List<Map<String, Object>> listUsers(long tenantId) {
    return db.queryForList(
        "SELECT id,display_name name,org_id FROM eam_sys_user WHERE tenant_id=? AND deleted=0"
            + " AND status=1 ORDER BY display_name",
        tenantId);
  }

  public List<Map<String, Object>> listWarehouses(long tenantId) {
    return db.queryForList(
        "SELECT * FROM inv_warehouse WHERE tenant_id=? AND enabled=1 ORDER BY name", tenantId);
  }

  public List<Map<String, Object>> listMaterials(long tenantId) {
    return db.queryForList(
        "SELECT * FROM inv_material WHERE tenant_id=? AND enabled=1 ORDER BY code", tenantId);
  }

  public List<Map<String, Object>> listBins(long tenantId) {
    return db.queryForList(
        "SELECT * FROM inv_bin WHERE tenant_id=? AND enabled=1 ORDER BY code", tenantId);
  }

  public int countCode(long tenantId, Object code, String kind) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM " + table(kind) + " WHERE tenant_id=? AND code=?",
        Integer.class,
        tenantId,
        code);
  }

  public int countMaterialStock(long tenantId, Object recordId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_balance WHERE tenant_id=? AND material_id=? AND"
            + " (quantity<>0 OR reserved_quantity<>0)",
        Integer.class,
        tenantId,
        recordId);
  }

  public int countLegacyStock(long tenantId, Object recordId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_legacy_stock_map m JOIN eam_spare_stock s ON"
            + " s.tenant_id=m.tenant_id AND s.id=m.stock_id WHERE m.tenant_id=? AND"
            + " m.material_id=? AND s.quantity<>0",
        Integer.class,
        tenantId,
        recordId);
  }

  public int countDraftReceipts(long tenantId, Object recordId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_receipt_line l JOIN inv_receipt r ON"
            + " r.tenant_id=l.tenant_id AND r.id=l.receipt_id WHERE l.tenant_id=? AND"
            + " l.material_id=? AND r.deleted=0 AND r.status='草稿'",
        Integer.class,
        tenantId,
        recordId);
  }

  public int countOpenRequisitions(long tenantId, Object recordId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_requisition_line l JOIN inv_requisition r ON"
            + " r.tenant_id=l.tenant_id AND r.id=l.requisition_id WHERE l.tenant_id=?"
            + " AND l.material_id=? AND r.deleted=0 AND r.status NOT IN"
            + " ('已取消','已完成','已关闭')",
        Integer.class,
        tenantId,
        recordId);
  }

  public int countOpenOperations(long tenantId, Object value, Object recordId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_operation d WHERE d.tenant_id=? AND d.deleted=0 AND"
            + " d.status IN ('草稿','在途') AND"
            + " (JSON_CONTAINS(d.payload,JSON_OBJECT('materialId',?), '$.lines') OR"
            + " EXISTS(SELECT 1 FROM inv_movement_allocation a JOIN inv_balance s ON"
            + " s.tenant_id=a.tenant_id AND s.id=a.balance_id WHERE"
            + " a.tenant_id=d.tenant_id AND a.document_id=d.id AND s.material_id=?))",
        Integer.class,
        tenantId,
        value,
        recordId);
  }

  public int countInstalledUsage(long tenantId, Object recordId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_usage WHERE tenant_id=? AND material_id=? AND" + " status='在用'",
        Integer.class,
        tenantId,
        recordId);
  }

  public int countUnit(long tenantId, Object value) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_base_item WHERE tenant_id=? AND item_type='UNIT' AND"
            + " name=? AND deleted=0 AND status=1",
        Integer.class,
        tenantId,
        value);
  }

  public int countMaterialCategory(long tenantId, Object value) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_base_item WHERE tenant_id=? AND"
            + " item_type='MATERIAL_CATEGORY' AND name=? AND deleted=0 AND status=1",
        Integer.class,
        tenantId,
        value);
  }

  public List<Map<String, Object>> findManager(long tenantId, Object manager) {
    return db.queryForList(
        "SELECT org_id FROM eam_sys_user WHERE tenant_id=? AND id=? AND deleted=0 AND"
            + " status=1",
        tenantId,
        manager);
  }

  public int insertMaster(String kind, Map<String, Object> fields, List<Object> params) {
    validateFields(kind, fields);
    return db.update(
        "INSERT INTO "
            + table(kind)
            + " ("
            + String.join(",", fields.keySet())
            + ",id,tenant_id) VALUES ("
            + String.join(",", Collections.nCopies(params.size(), "?"))
            + ")",
        params.toArray());
  }

  public int updateMaster(String kind, Map<String, Object> fields, List<Object> params) {
    validateFields(kind, fields);
    return db.update(
        "UPDATE "
            + table(kind)
            + " SET "
            + String.join(",", fields.keySet().stream().map(k -> k + "=?").toList())
            + ",row_version=row_version+1 WHERE tenant_id=? AND id=?",
        params.toArray());
  }

  public int insertMasterEvent(
      long recordId,
      long tenantId,
      Object kind,
      Object target,
      Object action,
      long userId,
      Object value,
      Object value8) {
    return db.update(
        "INSERT INTO"
            + " inv_master_event(id,tenant_id,resource_kind,resource_id,action,actor_id,actor_name,details_json)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        recordId,
        tenantId,
        kind,
        target,
        action,
        userId,
        value,
        value8);
  }
}
