package com.windasoft.eam.inventory;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InventoryReservationCleanup {
  private final JdbcTemplate db;
  private final TransactionTemplate transaction;

  public InventoryReservationCleanup(JdbcTemplate db, PlatformTransactionManager manager) {
    this.db = db;
    this.transaction = new TransactionTemplate(manager);
  }

  // The worker uses tenant IDs from stored documents, not a fabricated request identity.
  @Scheduled(fixedDelayString = "${eam.inventory.cleanup-delay-ms:30000}")
  public void cleanup() {
    for (var tenant :
        db.queryForList(
            "SELECT DISTINCT tenant_id FROM inv_requisition WHERE deleted=0 AND status IN"
                + " ('待预留','部分预留','已预留','部分发料')"))
      transaction.executeWithoutResult(
          status -> {
            long t = ((Number) tenant.get("tenant_id")).longValue();
            db.update(
                "INSERT INTO inv_tenant_lock(tenant_id) VALUES(?) ON DUPLICATE KEY UPDATE"
                    + " tenant_id=tenant_id",
                t);
            db.queryForMap("SELECT tenant_id FROM inv_tenant_lock WHERE tenant_id=? FOR UPDATE", t);
            for (var r :
                db.queryForList(
                    "SELECT * FROM inv_requisition WHERE tenant_id=? AND deleted=0 AND status IN"
                        + " ('待预留','部分预留','已预留','部分发料') ORDER BY id FOR UPDATE",
                    t)) {
              String kind = Objects.toString(r.get("task_kind"));
              String table =
                  kind.equals("维修")
                      ? "eam_repair_order"
                      : kind.equals("保养") ? "eam_maintenance_task" : "eam_inspection_task";
              var tasks =
                  db.queryForList(
                      "SELECT status"
                          + (kind.equals("保养") ? "" : ",deleted")
                          + " FROM "
                          + table
                          + " WHERE tenant_id=? AND id=?",
                      t,
                      r.get("task_id"));
              boolean ended =
                  tasks.isEmpty()
                      || !kind.equals("保养")
                          && ((Number) tasks.getFirst().get("deleted")).intValue() != 0
                      || !tasks.isEmpty()
                          && Set.of("待验收", "待审核", "已完成", "已验收", "已审核", "已作废", "过期作废", "已取消", "已关闭")
                              .contains(Objects.toString(tasks.getFirst().get("status")));
              if (!ended) continue;
              for (var reservation :
                  db.queryForList(
                      "SELECT * FROM inv_reservation WHERE tenant_id=? AND requisition_id=? AND"
                          + " remaining_quantity>0 ORDER BY balance_id,id FOR UPDATE",
                      t,
                      r.get("id"))) {
                db.update(
                    "UPDATE inv_balance SET"
                        + " reserved_quantity=reserved_quantity-?,row_version=row_version+1 WHERE"
                        + " tenant_id=? AND id=?",
                    reservation.get("remaining_quantity"),
                    t,
                    reservation.get("balance_id"));
                db.update(
                    "UPDATE inv_reservation SET remaining_quantity=0 WHERE tenant_id=? AND id=?",
                    t,
                    reservation.get("id"));
              }
              db.update(
                  "UPDATE inv_requisition SET status='已关闭',row_version=row_version+1 WHERE"
                      + " tenant_id=? AND id=?",
                  t,
                  r.get("id"));
              db.update(
                  "UPDATE inv_inbox SET done=1 WHERE tenant_id=? AND document_id=? AND"
                      + " route_kind='requisitions' AND is_todo=1",
                  t,
                  r.get("id"));
              db.update(
                  "INSERT INTO"
                      + " inv_document_event(id,tenant_id,document_kind,document_id,action,actor_id,actor_name)"
                      + " VALUES(?,?,'领料',?,'任务结束，释放预留并关闭申请',0,'系统')",
                  IdWorker.getId(),
                  t,
                  r.get("id"));
            }
          });
  }
}
