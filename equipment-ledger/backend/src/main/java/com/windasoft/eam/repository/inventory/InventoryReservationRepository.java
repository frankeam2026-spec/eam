package com.windasoft.eam.repository.inventory;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryReservationRepository {
  private final JdbcTemplate db;

  public InventoryReservationRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> listTenantIds() {
    return db.queryForList(
        "SELECT DISTINCT tenant_id FROM inv_requisition WHERE deleted=0 AND status IN"
            + " ('待预留','部分预留','已预留','部分发料')");
  }

  public int insertLock(Object t) {
    return db.update(
        "INSERT INTO inv_tenant_lock(tenant_id) VALUES(?) ON DUPLICATE KEY UPDATE"
            + " tenant_id=tenant_id",
        t);
  }

  public Map<String, Object> lockTenant(Object t) {
    return db.queryForMap("SELECT tenant_id FROM inv_tenant_lock WHERE tenant_id=? FOR UPDATE", t);
  }

  public List<Map<String, Object>> lockRequests(Object t) {
    return db.queryForList(
        "SELECT * FROM inv_requisition WHERE tenant_id=? AND deleted=0 AND status IN"
            + " ('待预留','部分预留','已预留','部分发料') ORDER BY id FOR UPDATE",
        t);
  }

  public List<Map<String, Object>> loadTask(Object t, Object taskId, String kind, String table) {
    if (!Set.of("eam_repair_order", "v_maintenance_task", "v_inspection_task").contains(table))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.inventory.message0003");
    return db.queryForList(
        "SELECT status"
            + (kind.equals("保养") ? "" : ",deleted")
            + " FROM "
            + table
            + " WHERE tenant_id=? AND id=?",
        t,
        taskId);
  }

  public List<Map<String, Object>> lockReservations(Object t, Object id) {
    return db.queryForList(
        "SELECT * FROM inv_reservation WHERE tenant_id=? AND requisition_id=? AND"
            + " remaining_quantity>0 ORDER BY balance_id,id FOR UPDATE",
        t,
        id);
  }

  public int releaseBalance(Object remainingQuantity, Object t, Object balanceId) {
    return db.update(
        "UPDATE inv_balance SET"
            + " reserved_quantity=reserved_quantity-?,row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=?",
        remainingQuantity,
        t,
        balanceId);
  }

  public int releaseReservation(Object t, Object id) {
    return db.update(
        "UPDATE inv_reservation SET remaining_quantity=0 WHERE tenant_id=? AND id=?", t, id);
  }

  public int closeRequest(Object t, Object id) {
    return db.update(
        "UPDATE inv_requisition SET status='已关闭',row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=?",
        t,
        id);
  }

  public int closeTodos(Object t, Object id) {
    return db.update(
        "UPDATE inv_inbox SET done=1 WHERE tenant_id=? AND document_id=? AND"
            + " route_kind='requisitions' AND is_todo=1",
        t,
        id);
  }

  public int insertSystemEvent(long recordId, Object t, Object id) {
    return db.update(
        "INSERT INTO"
            + " inv_document_event(id,tenant_id,document_kind,document_id,action,actor_id,actor_name)"
            + " VALUES(?,?,'领料',?,'任务结束，释放预留并关闭申请',0,'系统')",
        recordId,
        t,
        id);
  }
}
