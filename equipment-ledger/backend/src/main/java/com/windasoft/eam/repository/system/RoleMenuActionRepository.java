package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.system.RoleMenuAction;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RoleMenuActionRepository {
  private final RoleMenuActionMapper mapper;

  public RoleMenuActionRepository(RoleMenuActionMapper mapper) {
    this.mapper = mapper;
  }

  public List<RoleMenuAction> find(long tenant, Collection<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return mapper.selectList(
        new LambdaQueryWrapper<RoleMenuAction>()
            .eq(RoleMenuAction::getTenantId, tenant)
            .in(RoleMenuAction::getGrantId, ids)
            .orderByAsc(RoleMenuAction::getSortNo));
  }

  public void delete(long tenant, long owner) {
    mapper.delete(
        new LambdaQueryWrapper<RoleMenuAction>()
            .eq(RoleMenuAction::getTenantId, tenant)
            .eq(RoleMenuAction::getGrantId, owner));
  }

  public void insert(List<RoleMenuAction> rows) {
    if (!rows.isEmpty()) mapper.insert(rows);
  }
}
