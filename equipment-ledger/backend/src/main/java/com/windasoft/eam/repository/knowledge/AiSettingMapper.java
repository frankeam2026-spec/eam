package com.windasoft.eam.repository.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.knowledge.AiSetting;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiSettingMapper extends BaseMapper<AiSetting> {
  /** 原子初始化默认配置，保留首次访问及并发唯一性语义。 */
  @Insert("INSERT IGNORE INTO eam_ai_setting(tenant_id) VALUES(#{tenant})")
  int ensure(@Param("tenant") long tenant);
}
