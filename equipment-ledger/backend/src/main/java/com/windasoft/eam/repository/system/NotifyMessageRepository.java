package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.system.NotifyMessage;
import org.springframework.stereotype.Repository;

@Repository
public class NotifyMessageRepository {
  private final NotifyMessageMapper mapper;

  public NotifyMessageRepository(NotifyMessageMapper mapper) {
    this.mapper = mapper;
  }

  /** n:标识只更新独立公共消息；旧ID仍按legacy_id映射，沿用原模块范围及首次已读时间。 */
  public void markRead(long tenant, long user, String module, String id) {
    var q =
        new LambdaUpdateWrapper<NotifyMessage>()
            .eq(NotifyMessage::getTenantId, tenant)
            .eq(NotifyMessage::getRecipientId, user);
    if (id.startsWith("n:"))
      q.eq(NotifyMessage::getId, Long.parseLong(id.substring(2)))
          .isNull(NotifyMessage::getLegacyId);
    else {
      q.eq(!module.isBlank(), NotifyMessage::getModule, module);
      if (!id.isBlank()) q.eq(NotifyMessage::getLegacyId, id);
    }
    mapper.update(null, q.setSql("read_at=COALESCE(read_at,NOW(6))"));
  }
}
