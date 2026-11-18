package com.windasoft.eam.repository.experience;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.experience.HomeTemplateVersionRole;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HomeTemplateVersionRoleMapper extends BaseMapper<HomeTemplateVersionRole> {
  /** 批量读取全部当前发布角色，避免对每份模板逐次查询版本关系。 */
  @Select(
      "SELECT r.* FROM eam_home_template_version_role r JOIN eam_home_template t ON"
          + " t.tenant_id=r.tenant_id AND t.id=r.template_id AND t.published_version=r.version"
          + " WHERE t.tenant_id=#{tenant} AND t.enabled=1 ORDER BY r.template_id,r.sort_no")
  List<HomeTemplateVersionRole> published(@Param("tenant") long tenant);
}
