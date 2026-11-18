package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.system.RoleOrganization;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RoleOrganizationRepository {
  private final RoleOrganizationMapper mapper;

  public RoleOrganizationRepository(RoleOrganizationMapper mapper) {
    this.mapper = mapper;
  }

  public List<RoleOrganization> find(long tenant, Collection<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return mapper.selectList(
        new LambdaQueryWrapper<RoleOrganization>()
            .eq(RoleOrganization::getTenantId, tenant)
            .in(RoleOrganization::getRoleId, ids)
            .orderByAsc(RoleOrganization::getSortNo));
  }

  public void delete(long tenant, long owner) {
    mapper.delete(
        new LambdaQueryWrapper<RoleOrganization>()
            .eq(RoleOrganization::getTenantId, tenant)
            .eq(RoleOrganization::getRoleId, owner));
  }

  public void insert(List<RoleOrganization> rows) {
    if (!rows.isEmpty()) mapper.insert(rows);
  }
}
