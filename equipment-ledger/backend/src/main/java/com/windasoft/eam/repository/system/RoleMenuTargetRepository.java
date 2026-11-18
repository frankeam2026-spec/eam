package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.system.RoleMenuTarget;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RoleMenuTargetRepository {
  private final RoleMenuTargetMapper mapper;

  public RoleMenuTargetRepository(RoleMenuTargetMapper mapper) {
    this.mapper = mapper;
  }

  public List<RoleMenuTarget> find(long tenant, Collection<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return mapper.selectList(
        new LambdaQueryWrapper<RoleMenuTarget>()
            .eq(RoleMenuTarget::getTenantId, tenant)
            .in(RoleMenuTarget::getGrantId, ids)
            .orderByAsc(RoleMenuTarget::getSortNo));
  }

  public void delete(long tenant, long owner) {
    mapper.delete(
        new LambdaQueryWrapper<RoleMenuTarget>()
            .eq(RoleMenuTarget::getTenantId, tenant)
            .eq(RoleMenuTarget::getGrantId, owner));
  }

  public void insert(List<RoleMenuTarget> rows) {
    if (!rows.isEmpty()) mapper.insert(rows);
  }
}
