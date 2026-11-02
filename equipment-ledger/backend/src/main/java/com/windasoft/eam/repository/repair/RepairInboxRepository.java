package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.common.NotificationMessages;
import com.windasoft.eam.domain.repair.RepairNoticeKind;
import com.windasoft.eam.dto.repair.RepairInboxView;
import com.windasoft.eam.entity.repair.RepairInbox;
import java.util.List;
import org.springframework.stereotype.Repository;

/** RepairInbox 主仓储：集中维护租户条件及持久化操作，不决定业务授权和事务。 */
@Repository
public class RepairInboxRepository {
  /** 工作台只返回最近消息，沿用原有上限，不与列表分页上限合并。 */
  private static final int MAX_RECENT_MESSAGES = 300;

  private final RepairInboxMapper mapper;

  public RepairInboxRepository(RepairInboxMapper mapper) {
    this.mapper = mapper;
  }

  public void inbox(
      long id, long tenant, long user, long order, String kind, String title, boolean todo) {
    var row = new RepairInbox();
    row.setId(id);
    row.setTenantId(tenant);
    row.setUserId(user);
    row.setOrderId(order);
    row.setKind(kind);
    row.setTitle(title);
    row.setIsTodo(todo ? 1 : 0);
    row.setDone(0);
    row.setMessageKey(NotificationMessages.key(kind));
    row.setMessageArgs(NotificationMessages.arguments(kind, title));
    mapper.insertAtDatabaseTime(row);
  }

  private LambdaUpdateWrapper<RepairInbox> pending(long tenant, long order) {
    return new LambdaUpdateWrapper<RepairInbox>()
        .eq(RepairInbox::getTenantId, tenant)
        .eq(RepairInbox::getOrderId, order)
        .eq(RepairInbox::getIsTodo, 1)
        .eq(RepairInbox::getDone, 0)
        .set(RepairInbox::getDone, 1);
  }

  /** 出库待办独立关闭，防止接单或审核结束误关仓库任务。 */
  public void closeTodos(long tenant, long order) {
    mapper.update(
        null,
        pending(tenant, order).ne(RepairInbox::getKind, RepairNoticeKind.PENDING_ISSUE.code()));
  }

  public void closeIssueTodos(long tenant, long order) {
    mapper.update(
        null,
        pending(tenant, order).eq(RepairInbox::getKind, RepairNoticeKind.PENDING_ISSUE.code()));
  }

  public void closeApprovalReturnTodos(long tenant, long order) {
    mapper.update(
        null,
        pending(tenant, order).eq(RepairInbox::getKind, RepairNoticeKind.REPORT_REVISION.code()));
  }

  public int markRead(long tenant, long user, long id) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<RepairInbox>()
            .eq(RepairInbox::getTenantId, tenant)
            .eq(RepairInbox::getUserId, user)
            .eq(RepairInbox::getId, id)
            .setSql("read_at=NOW()"));
  }

  public int hasInbox(long tenant, long user, long order) {
    return Math.toIntExact(
        mapper.selectCount(
            new LambdaQueryWrapper<RepairInbox>()
                .eq(RepairInbox::getTenantId, tenant)
                .eq(RepairInbox::getUserId, user)
                .eq(RepairInbox::getOrderId, order)));
  }

  public List<RepairInboxView> inboxRows(long tenant, long user) {
    return mapper.inboxRows(tenant, user, MAX_RECENT_MESSAGES);
  }
}
