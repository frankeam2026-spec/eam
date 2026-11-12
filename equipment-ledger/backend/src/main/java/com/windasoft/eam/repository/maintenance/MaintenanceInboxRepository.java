package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceInbox;
import org.springframework.stereotype.Repository;

/** 保养消息表的主仓储；不决定通知接收人，也不负责公共渠道入队。 */
@Repository
public class MaintenanceInboxRepository {
  private final MaintenanceInboxMapper mapper;

  public MaintenanceInboxRepository(MaintenanceInboxMapper mapper) {
    this.mapper = mapper;
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
}
