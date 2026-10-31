package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import com.windasoft.eam.entity.system.SystemUser;
import org.springframework.stereotype.Repository;

/** 系统用户自身数据的租户限定读取。 */
@Repository
public class SystemUserRepository {
  private final SystemUserMapper mapper;

  public SystemUserRepository(SystemUserMapper mapper) {
    this.mapper = mapper;
  }

  public SystemUser find(long tenant, Long id) {
    return mapper.selectOne(new LambdaQueryWrapper<SystemUser>()
        .eq(SystemUser::getTenantId, tenant).eq(SystemUser::getId, id));
  }

  public Page<SystemUser> ownerCandidates(
      long tenant, com.windasoft.eam.dto.common.ListQuery query) {
    var where = new LambdaQueryWrapper<SystemUser>()
        .eq(SystemUser::getTenantId, tenant)
        .eq(SystemUser::getStatus, 1);
    if (!query.keyword().isBlank()) {
      where.and(w -> w.like(SystemUser::getDisplayName, query.keyword())
          .or().like(SystemUser::getAccount, query.keyword()));
    }
    where.orderByAsc(SystemUser::getDisplayName).orderByAsc(SystemUser::getId);
    return mapper.selectPage(new Page<>(query.page(), query.size()), where);
  }

  public List<SystemUser> employees(long tenant) {
    return mapper.selectList(new LambdaQueryWrapper<SystemUser>()
        .eq(SystemUser::getTenantId, tenant)
        .eq(SystemUser::getStatus, 1));
  }
}
