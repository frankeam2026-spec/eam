package com.windasoft.eam.repository.experience;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.experience.HomePreference;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HomePreferenceMapper extends BaseMapper<HomePreference> {
  /** 各项偏好原子更新，不能覆盖另一个入口保存的最近访问记录。 */
  @Insert(
      "INSERT INTO eam_home_preference(tenant_id,user_id,default_template_id)"
          + " VALUES(#{tenant},#{user},#{template}) ON DUPLICATE KEY UPDATE"
          + " default_template_id=VALUES(default_template_id)")
  void saveDefault(
      @Param("tenant") long tenant, @Param("user") long user, @Param("template") long template);

  @Insert(
      "INSERT IGNORE INTO eam_home_preference(tenant_id,user_id,recent_json)"
          + " VALUES(#{tenant},#{user},JSON_ARRAY())")
  void ensureRecent(@Param("tenant") long tenant, @Param("user") long user);
}
