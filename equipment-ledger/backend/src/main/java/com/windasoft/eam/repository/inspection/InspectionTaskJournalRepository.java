package com.windasoft.eam.repository.inspection;

import com.windasoft.eam.repository.system.NotificationRepository;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 任务事件与待办通知的持久化边界。 */
@Repository
public class InspectionTaskJournalRepository {
  private final JdbcTemplate db;
  private final NotificationRepository notifications;

  public InspectionTaskJournalRepository(
      JdbcTemplate db, NotificationRepository notifications) {
    this.db = db;
    this.notifications = notifications;
  }

  public List<Map<String, Object>> listTaskEvents(long tenantId, long taskId) {
    return db.queryForList(
        "SELECT action,actor_name actor,DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s')"
            + " time,details FROM eam_inspection_event WHERE tenant_id=? AND task_id=?"
            + " ORDER BY created_at,id",
        tenantId,
        taskId);
  }

  public boolean hasAction(long tenantId, long taskId, String action) {
    Long count =
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_inspection_event WHERE tenant_id=? AND task_id=? AND action=?",
            Long.class,
            tenantId,
            taskId,
            action);
    return count != null && count > 0;
  }

  public int insertTaskEvent(
      long recordId,
      long tenantId,
      long taskId,
      Long actorId,
      Object name,
      Object action,
      String payload) {
    return db.update(
        "INSERT INTO eam_inspection_event(id,tenant_id,task_id,actor_id,actor_name,action,details)"
            + " VALUES(?,?,?,?,?,?,?)",
        recordId,
        tenantId,
        taskId,
        actorId,
        name,
        action,
        payload);
  }

  public int completeTodos(long tenantId, long taskId) {
    return db.update(
        "UPDATE eam_inspection_inbox SET done=1 WHERE tenant_id=? AND task_id=? AND is_todo=1 AND"
            + " done=0",
        tenantId,
        taskId);
  }

  public int countNotice(long tenantId, long taskId, Object user, Object kind, boolean once) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_inspection_inbox WHERE tenant_id=? AND task_id=? AND"
            + " user_id=? AND kind=?"
            + (once ? "" : " AND done=0"),
        Integer.class,
        tenantId,
        taskId,
        user,
        kind);
  }

  public int insertInbox(
      long recordId,
      long tenantId,
      long taskId,
      Object user,
      Object kind,
      Object title,
      boolean todo) {
    int count =
        db.update(
            "INSERT INTO eam_inspection_inbox(id,tenant_id,task_id,user_id,kind,title,is_todo)"
                + " VALUES(?,?,?,?,?,?,?)",
            recordId,
            tenantId,
            taskId,
            user,
            kind,
            title,
            todo);
    db.update(
        "UPDATE eam_inspection_inbox SET message_key=?,message_args=? WHERE tenant_id=? AND id=?",
        com.windasoft.eam.common.NotificationMessages.key(kind),
        com.windasoft.eam.common.NotificationMessages.arguments(kind, title),
        tenantId,
        recordId);
    notifications.captureSource("eam_inspection_inbox", tenantId, recordId);
    return count;
  }
}
