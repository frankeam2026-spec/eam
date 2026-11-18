package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.windasoft.eam.entity.system.NotifyPolicy;
import org.springframework.stereotype.Repository;

@Repository
public class NotifyPolicyRepository {
  private final NotifyPolicyMapper mapper;

  public NotifyPolicyRepository(NotifyPolicyMapper mapper) {
    this.mapper = mapper;
  }

  private LambdaQueryWrapper<NotifyPolicy> filters(long tenant, String kind, String q) {
    return new LambdaQueryWrapper<NotifyPolicy>()
        .eq(NotifyPolicy::getTenantId, tenant)
        .eq(!kind.isBlank(), NotifyPolicy::getBusinessKind, kind)
        .apply("LOCATE({0},name)>0", q);
  }

  public Page<NotifyPolicy> list(long tenant, String kind, String q, int page, int size) {
    return mapper.selectPage(
        new Page<>(page, size),
        filters(tenant, kind, q).orderByDesc(NotifyPolicy::getUpdatedAt, NotifyPolicy::getId));
  }

  public Page<NotifyPolicy> choices(long tenant, String kind, String q, int page, int size) {
    return mapper.selectPage(
        new Page<>(page, size),
        filters(tenant, kind, q)
            .eq(NotifyPolicy::getEnabled, 1)
            .orderByAsc(NotifyPolicy::getName, NotifyPolicy::getId));
  }

  public NotifyPolicy find(long tenant, long id, boolean lock) {
    var q =
        new LambdaQueryWrapper<NotifyPolicy>()
            .eq(NotifyPolicy::getTenantId, tenant)
            .eq(NotifyPolicy::getId, id);
    if (lock) q.last("FOR UPDATE");
    return mapper.selectOne(q);
  }

  public void insert(NotifyPolicy row) {
    mapper.insert(row);
  }

  /** 生效下界仅在启停变化时推进，名称编辑不补发历史提醒；保持数据库时钟与原版本递增。 */
  public void update(NotifyPolicy row) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<NotifyPolicy>()
            .eq(NotifyPolicy::getTenantId, row.getTenantId())
            .eq(NotifyPolicy::getId, row.getId())
            .set(NotifyPolicy::getName, row.getName())
            .setSql("effective_at=IF(enabled<>{0},NOW(6),effective_at)", row.getEnabled())
            .set(NotifyPolicy::getEnabled, row.getEnabled())
            .setSql("row_version=row_version+1,updated_at=NOW(6)"));
  }
}
