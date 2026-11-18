package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.system.UserPreference;
import org.springframework.stereotype.Repository;

/** 语言与皮肤共用偏好表的唯一写入入口，各自更新不覆盖另一项。 */
@Repository
public class UserPreferenceRepository {
  private final UserPreferenceMapper mapper;

  public UserPreferenceRepository(UserPreferenceMapper mapper) {
    this.mapper = mapper;
  }

  public UserPreference find(long tenant, long user) {
    return mapper.selectOne(
        new LambdaQueryWrapper<UserPreference>()
            .eq(UserPreference::getTenantId, tenant)
            .eq(UserPreference::getUserId, user));
  }

  public String theme(long tenant, long user) {
    var row = find(tenant, user);
    return row == null ? "blue" : row.getTheme();
  }

  public String locale(long tenant, long user) {
    var row = find(tenant, user);
    return row == null ? "zh-CN" : row.getLocale();
  }

  public void saveTheme(long tenant, long user, String theme) {
    mapper.theme(tenant, user, theme);
  }

  public void saveLocale(long tenant, long user, String locale) {
    mapper.locale(tenant, user, locale);
  }
}
