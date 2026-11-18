package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.system.UserPreference;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {
  /** 原子更新单项偏好，不能先读再覆盖另一项并发更新；保留数据库默认值。 */
  @Insert(
      "INSERT INTO eam_user_preference(tenant_id,user_id,theme) VALUES(#{tenant},#{user},#{theme})"
          + " ON DUPLICATE KEY UPDATE theme=VALUES(theme)")
  void theme(@Param("tenant") long tenant, @Param("user") long user, @Param("theme") String theme);

  @Insert(
      "INSERT INTO eam_user_preference(tenant_id,user_id,theme,locale)"
          + " VALUES(#{tenant},#{user},'blue',#{locale}) ON DUPLICATE KEY UPDATE"
          + " locale=VALUES(locale)")
  void locale(
      @Param("tenant") long tenant, @Param("user") long user, @Param("locale") String locale);
}
