package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.system.RoleLegacyAction;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RoleLegacyActionRepository {
  private final RoleLegacyActionMapper mapper;

  public RoleLegacyActionRepository(RoleLegacyActionMapper mapper) {
    this.mapper = mapper;
  }

  public List<RoleLegacyAction> find(long tenant, Collection<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return mapper.selectList(
        new LambdaQueryWrapper<RoleLegacyAction>()
            .eq(RoleLegacyAction::getTenantId, tenant)
            .in(RoleLegacyAction::getRoleId, ids)
            .orderByAsc(RoleLegacyAction::getSortNo));
  }

  public void delete(long tenant, long owner) {
    mapper.delete(
        new LambdaQueryWrapper<RoleLegacyAction>()
            .eq(RoleLegacyAction::getTenantId, tenant)
            .eq(RoleLegacyAction::getRoleId, owner));
  }

  public void insert(List<RoleLegacyAction> rows) {
    if (!rows.isEmpty()) mapper.insert(rows);
  }
}
