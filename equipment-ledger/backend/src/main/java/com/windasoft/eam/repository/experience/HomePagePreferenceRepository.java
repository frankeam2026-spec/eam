package com.windasoft.eam.repository.experience;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.experience.HomePagePreference;
import org.springframework.stereotype.Repository;

@Repository
public class HomePagePreferenceRepository {
  private final HomePagePreferenceMapper mapper;

  public HomePagePreferenceRepository(HomePagePreferenceMapper mapper) {
    this.mapper = mapper;
  }

  public HomePagePreference find(long tenant, long user, String key) {
    return mapper.selectOne(
        new LambdaQueryWrapper<HomePagePreference>()
            .eq(HomePagePreference::getTenantId, tenant)
            .eq(HomePagePreference::getUserId, user)
            .eq(HomePagePreference::getPageKey, key));
  }

  public void save(long tenant, long user, String key, String value) {
    var row = new HomePagePreference();
    row.setTenantId(tenant);
    row.setUserId(user);
    row.setPageKey(key);
    row.setPreferences(value);
    mapper.save(row);
  }
}
