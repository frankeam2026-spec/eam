package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.dto.system.NotificationContracts.Channel;
import com.windasoft.eam.entity.system.NotifyChannel;
import org.springframework.stereotype.Repository;

@Repository
public class NotifyChannelRepository {
  private final NotifyChannelMapper mapper;

  public NotifyChannelRepository(NotifyChannelMapper mapper) {
    this.mapper = mapper;
  }

  /** 原读取会初始化租户配置；锁读只用于外层渠道保存事务。 */
  public NotifyChannel find(long tenant, boolean lock) {
    mapper.ensure(tenant);
    var q = new LambdaQueryWrapper<NotifyChannel>().eq(NotifyChannel::getTenantId, tenant);
    if (lock) q.last("FOR UPDATE");
    return mapper.selectOne(q);
  }

  public void save(long tenant, Channel c, String secret) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<NotifyChannel>()
            .eq(NotifyChannel::getTenantId, tenant)
            .set(NotifyChannel::getSystemEnabled, c.systemEnabled() ? 1 : 0)
            .set(NotifyChannel::getEmailEnabled, c.emailEnabled() ? 1 : 0)
            .set(NotifyChannel::getSmtpHost, c.smtpHost())
            .set(NotifyChannel::getSmtpPort, c.smtpPort())
            .set(NotifyChannel::getSmtpSecurity, c.smtpSecurity())
            .set(NotifyChannel::getSmtpUsername, c.smtpUsername())
            .set(NotifyChannel::getSmtpSecret, secret)
            .set(NotifyChannel::getSenderAddress, c.senderAddress())
            .set(NotifyChannel::getPublicUrl, c.publicUrl())
            .setSql("row_version=row_version+1,updated_at=NOW(6)"));
  }
}
