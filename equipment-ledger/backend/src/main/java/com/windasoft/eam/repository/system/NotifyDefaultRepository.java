package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.system.NotifyDefault;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class NotifyDefaultRepository {
  private final NotifyDefaultMapper mapper;

  public NotifyDefaultRepository(NotifyDefaultMapper mapper) {
    this.mapper = mapper;
  }

  public List<NotifyDefault> list(long tenant) {
    return mapper.selectList(
        new LambdaQueryWrapper<NotifyDefault>().eq(NotifyDefault::getTenantId, tenant));
  }

  public NotifyDefault find(long tenant, String kind, boolean lock) {
    if (lock) mapper.ensure(tenant, kind);
    var q =
        new LambdaQueryWrapper<NotifyDefault>()
            .eq(NotifyDefault::getTenantId, tenant)
            .eq(NotifyDefault::getBusinessKind, kind);
    if (lock) q.last("FOR UPDATE");
    return mapper.selectOne(q);
  }

  public void save(long tenant, String kind, Long policy) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<NotifyDefault>()
            .eq(NotifyDefault::getTenantId, tenant)
            .eq(NotifyDefault::getBusinessKind, kind)
            .set(NotifyDefault::getPolicyId, policy)
            .setSql("row_version=row_version+1,effective_at=NOW(6)"));
  }
}
