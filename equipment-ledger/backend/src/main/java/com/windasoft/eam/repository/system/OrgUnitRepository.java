package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.system.OrgUnit;
import org.springframework.stereotype.Repository;

/** 组织表的数据访问，业务事务和多组织加锁顺序由调用Service编排。 */
@Repository
public class OrgUnitRepository {
  private final OrgUnitMapper mapper;

  public OrgUnitRepository(OrgUnitMapper mapper) {
    this.mapper = mapper;
  }

  public boolean lockExisting(long tenant, long id) {
    // 沿用原条件：同租户且未删除，不追加启用状态或组织类型限制。
    return mapper.selectOne(
            new LambdaQueryWrapper<OrgUnit>()
                .select(OrgUnit::getId)
                .eq(OrgUnit::getTenantId, tenant)
                .eq(OrgUnit::getId, id)
                .last("FOR UPDATE"))
        != null;
  }
 
  public OrgUnit activeDepartment(long tenant, String id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getId, id)
            .eq(OrgUnit::getOrgType, "部门")
            .eq(OrgUnit::getStatus, 1));
  }

  public OrgUnit organization(Long id) {
    return mapper.selectById(id);
  }
}
