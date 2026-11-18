package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.dto.system.NotificationContracts.Retention;
import com.windasoft.eam.entity.system.NotifyRetention;
import org.springframework.stereotype.Repository;

@Repository
public class NotifyRetentionRepository {
  private final NotifyRetentionMapper mapper;

  public NotifyRetentionRepository(NotifyRetentionMapper mapper) {
    this.mapper = mapper;
  }

  public NotifyRetention find(long tenant, boolean lock) {
    mapper.ensure(tenant);
    var q = new LambdaQueryWrapper<NotifyRetention>().eq(NotifyRetention::getTenantId, tenant);
    if (lock) q.last("FOR UPDATE");
    return mapper.selectOne(q);
  }

  public void save(long tenant, Retention input) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<NotifyRetention>()
            .eq(NotifyRetention::getTenantId, tenant)
            .set(NotifyRetention::getSuccessDays, input.successDays())
            .set(NotifyRetention::getExceptionDays, input.exceptionDays())
            .setSql("row_version=row_version+1,updated_at=NOW(6)"));
  }
}
