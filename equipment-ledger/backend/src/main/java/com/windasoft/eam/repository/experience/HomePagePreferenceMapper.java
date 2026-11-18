package com.windasoft.eam.repository.experience;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.experience.HomePagePreference;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HomePagePreferenceMapper extends BaseMapper<HomePagePreference> {
  /** 复合键首次保存与覆盖需原子完成，保留并发upsert和数据库默认时间。 */
  @Insert(
      "INSERT INTO eam_experience_page_preference(tenant_id,user_id,page_key,preferences)"
          + " VALUES(#{row.tenantId},#{row.userId},#{row.pageKey},#{row.preferences}) ON DUPLICATE"
          + " KEY UPDATE preferences=VALUES(preferences)")
  void save(@Param("row") HomePagePreference row);
}
