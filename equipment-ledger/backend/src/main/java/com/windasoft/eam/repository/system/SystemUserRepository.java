package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
}
