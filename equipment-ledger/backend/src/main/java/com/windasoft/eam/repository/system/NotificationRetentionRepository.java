package com.windasoft.eam.repository.system;

import com.windasoft.eam.dto.system.NotificationContracts.Retention;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRetentionRepository {
  private final JdbcTemplate db;
  public NotificationRetentionRepository(JdbcTemplate db) { this.db = db; }
  public Map<String,Object> settings(long tenant, boolean lock) {
    db.update("INSERT IGNORE INTO eam_notify_retention(tenant_id) VALUES(?)", tenant);
    return db.queryForMap("SELECT row_version rowVersion,success_days successDays,exception_days exceptionDays FROM eam_notify_retention WHERE tenant_id=?"+(lock?" FOR UPDATE":""), tenant);
  }
  public void save(long tenant, Retention input) {
    db.update("UPDATE eam_notify_retention SET success_days=?,exception_days=?,row_version=row_version+1,updated_at=NOW(6) WHERE tenant_id=?", input.successDays(),input.exceptionDays(),tenant);
  }
  // Bound the work of each transaction. No state actively awaiting a retry is eligible.
  public List<Map<String,Object>> candidates(int limit) {
    return db.queryForList("""
      SELECT d.id,d.message_id,m.tenant_id FROM eam_notify_delivery d
      JOIN eam_notify_message m ON m.id=d.message_id
      LEFT JOIN eam_notify_retention r ON r.tenant_id=m.tenant_id
      WHERE (d.status='SENT' AND d.sent_at<DATE_SUB(NOW(6),INTERVAL COALESCE(r.success_days,90) DAY))
      OR ((d.status IN ('UNKNOWN','MISSING_ADDRESS','DISABLED','NOT_INTEGRATED','CANCELLED') OR (d.status='FAILED' AND d.attempts>=5))
      AND d.updated_at<DATE_SUB(NOW(6),INTERVAL COALESCE(r.exception_days,180) DAY))
      ORDER BY d.message_id,d.id LIMIT ?
      """,limit);
  }
  public boolean compact(long tenant,long message,long delivery) {
    var owner=db.queryForList("SELECT id FROM eam_notify_message WHERE tenant_id=? AND id=? FOR UPDATE",Long.class,tenant,message);
    if(owner.isEmpty())return false;
    var rows=db.queryForList("""
      SELECT d.* FROM eam_notify_delivery d LEFT JOIN eam_notify_retention r ON r.tenant_id=?
      WHERE d.message_id=? AND d.id=? AND (
        (d.status='SENT' AND d.sent_at<DATE_SUB(NOW(6),INTERVAL COALESCE(r.success_days,90) DAY))
        OR ((d.status IN ('UNKNOWN','MISSING_ADDRESS','DISABLED','NOT_INTEGRATED','CANCELLED') OR (d.status='FAILED' AND d.attempts>=5))
          AND d.updated_at<DATE_SUB(NOW(6),INTERVAL COALESCE(r.exception_days,180) DAY))) FOR UPDATE
      """,tenant,message,delivery);
    if(rows.isEmpty())return false;
    var row=rows.getFirst();String status=row.get("status").toString();
    db.update("INSERT IGNORE INTO eam_notify_receipt(message_id,channel,terminal_status,sent_at) VALUES(?,?,?,?)",message,row.get("channel"),status,row.get("sent_at"));
    return db.update("DELETE FROM eam_notify_delivery WHERE id=? AND message_id=?",delivery,message)==1;
  }
}
