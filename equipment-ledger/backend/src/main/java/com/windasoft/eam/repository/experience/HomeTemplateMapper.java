package com.windasoft.eam.repository.experience;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.dto.experience.HomeContracts.Published;
import com.windasoft.eam.entity.experience.HomeTemplate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HomeTemplateMapper extends BaseMapper<HomeTemplate> {
  /** 预置键唯一约束保证多用户首次进入只初始化一次，普通insert不等价。 */
  @Insert(
      "INSERT IGNORE INTO eam_home_template(id,tenant_id,name,draft,priority,created_by,preset_key)"
          + " VALUES(#{row.id},#{row.tenantId},#{row.name},#{row.draft},#{row.priority},#{row.createdBy},#{row.presetKey})")
  int insertPreset(@Param("row") HomeTemplate row);

  /** 当前模板与其已发布版本必须同租户关联，保持已发布版本的名称/优先级语义。 */
  @Select(
      "SELECT t.id,t.name,t.published_version publishedVersion,v.payload,v.priority FROM"
          + " eam_home_template t JOIN eam_home_template_version v ON v.tenant_id=t.tenant_id AND"
          + " v.template_id=t.id AND v.version=t.published_version WHERE t.tenant_id=#{tenant} AND"
          + " t.enabled=1 ORDER BY v.priority,t.id")
  List<Published> published(@Param("tenant") long tenant);
}
