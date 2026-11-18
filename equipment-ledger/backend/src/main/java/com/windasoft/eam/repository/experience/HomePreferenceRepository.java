package com.windasoft.eam.repository.experience;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.experience.HomePreference;
import org.springframework.stereotype.Repository;

/** 默认首页与最近访问共用表，唯一主仓储按字段更新，不能相互覆盖。 */
@Repository
public class HomePreferenceRepository {
  private final HomePreferenceMapper mapper;

  public HomePreferenceRepository(HomePreferenceMapper mapper) {
    this.mapper = mapper;
  }

  public HomePreference find(long tenant, long user) {
    return mapper.selectOne(
        new LambdaQueryWrapper<HomePreference>()
            .eq(HomePreference::getTenantId, tenant)
            .eq(HomePreference::getUserId, user));
  }

  public void saveDefault(long tenant, long user, long template) {
    mapper.saveDefault(tenant, user, template);
  }

  public HomePreference lockRecent(long tenant, long user) {
    mapper.ensureRecent(tenant, user);
    return mapper.selectOne(
        new LambdaQueryWrapper<HomePreference>()
            .eq(HomePreference::getTenantId, tenant)
            .eq(HomePreference::getUserId, user)
            .last("FOR UPDATE"));
  }

  public void saveRecent(long tenant, long user, String value) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<HomePreference>()
            .eq(HomePreference::getTenantId, tenant)
            .eq(HomePreference::getUserId, user)
            .set(HomePreference::getRecentJson, value));
  }
}
