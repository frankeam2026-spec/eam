package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.dto.maintenance.MaintenanceInboxView;
import com.windasoft.eam.entity.maintenance.MaintenanceInbox;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 保养消息表的主仓储；不决定通知接收人，也不负责公共渠道入队。 */
@Repository
public class MaintenanceInboxRepository {
  private final MaintenanceInboxMapper mapper;
  private final JdbcTemplate db;

  public MaintenanceInboxRepository(MaintenanceInboxMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
  }

  /** 消息与未删除任务的只读JOIN，沿用原300条上限、租户和收件人范围及稳定时间顺序。 */
  public List<MaintenanceInboxView> list(long tenant, long user) {
    return db.query(
        """
SELECT CAST(i.id AS CHAR) id,CAST(t.id AS CHAR) orderId,'maintenance' module,t.code orderNo,
  JSON_UNQUOTE(JSON_EXTRACT(t.snapshot,'$.equipmentName')) equipmentName,t.status,i.kind,
  i.title,i.message_key messageKey,i.message_args messageArgs,i.is_todo todo,i.done,
  i.read_at IS NOT NULL isRead,DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time
FROM eam_maintenance_inbox i
JOIN v_maintenance_task t ON t.id=i.task_id AND t.tenant_id=i.tenant_id
WHERE i.tenant_id=? AND i.user_id=? ORDER BY i.created_at DESC,i.id DESC LIMIT 300
""",
        (row, index) ->
            new MaintenanceInboxView(
                row.getString("id"),
                row.getString("orderId"),
                row.getString("module"),
                row.getString("orderNo"),
                row.getString("equipmentName"),
                row.getString("status"),
                row.getString("kind"),
                row.getString("title"),
                row.getString("messageKey"),
                row.getString("messageArgs"),
                row.getInt("todo"),
                row.getInt("done"),
                row.getInt("isRead"),
                row.getString("time")),
        tenant,
        user);
  }

  public void insert(MaintenanceInbox message) {
    mapper.insert(message);
  }

  /** 业务流转只关闭待办，不改变普通通知和用户已读时间。 */
  public void completeTodos(long tenant, long task) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<MaintenanceInbox>()
            .eq(MaintenanceInbox::getTenantId, tenant)
            .eq(MaintenanceInbox::getTaskId, task)
            .eq(MaintenanceInbox::getTodo, true)
            .set(MaintenanceInbox::getDone, true));
  }

  /** 任务删除时全部消息关闭并标为已读；COALESCE 保留已有已读时间，NOW 沿用数据库时钟。 */
  public void completeForDeletion(long tenant, long task) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<MaintenanceInbox>()
            .eq(MaintenanceInbox::getTenantId, tenant)
            .eq(MaintenanceInbox::getTaskId, task)
            .set(MaintenanceInbox::getDone, true)
            .setSql("read_at=COALESCE(read_at,NOW())"));
  }

  /** 用户只能标记自己在当前租户的消息，重复已读沿用原更新数据库时间的行为。 */
  public void markRead(long tenant, long user, long id) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<MaintenanceInbox>()
            .eq(MaintenanceInbox::getTenantId, tenant)
            .eq(MaintenanceInbox::getUserId, user)
            .eq(MaintenanceInbox::getId, id)
            .setSql("read_at=NOW()"));
  }

  /** 已关闭或已读的历史提醒也算已发送，不能在下一轮扫描中重新通知。 */
  public boolean hasNotice(long tenant, long task, String kind) {
    return mapper.exists(
        new LambdaQueryWrapper<MaintenanceInbox>()
            .eq(MaintenanceInbox::getTenantId, tenant)
            .eq(MaintenanceInbox::getTaskId, task)
            .eq(MaintenanceInbox::getKind, kind));
  }

  /** 工作中心只标记当前租户、当前用户未读的消息，空ID表示本人的全部消息。 */
  public void markWorkRead(long tenant, long user, String id) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<MaintenanceInbox>()
            .eq(MaintenanceInbox::getTenantId, tenant)
            .eq(MaintenanceInbox::getUserId, user)
            .eq(!id.isBlank(), MaintenanceInbox::getId, id)
            .isNull(MaintenanceInbox::getReadAt)
            .setSql("read_at=NOW()"));
  }
}
