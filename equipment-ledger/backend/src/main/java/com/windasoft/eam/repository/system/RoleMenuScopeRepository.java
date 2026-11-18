package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.system.RoleMenuScope;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RoleMenuScopeRepository {
  private final RoleMenuScopeMapper mapper;

  public RoleMenuScopeRepository(RoleMenuScopeMapper mapper) {
    this.mapper = mapper;
  }

  public List<RoleMenuScope> find(long tenant, Collection<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return mapper.selectList(
        new LambdaQueryWrapper<RoleMenuScope>()
            .eq(RoleMenuScope::getTenantId, tenant)
            .in(RoleMenuScope::getGrantId, ids)
            .orderByAsc(RoleMenuScope::getSortNo));
  }

  public void delete(long tenant, long owner) {
    mapper.delete(
        new LambdaQueryWrapper<RoleMenuScope>()
            .eq(RoleMenuScope::getTenantId, tenant)
            .eq(RoleMenuScope::getGrantId, owner));
  }

  public void insert(List<RoleMenuScope> rows) {
    if (!rows.isEmpty()) mapper.insert(rows);
  }
}
