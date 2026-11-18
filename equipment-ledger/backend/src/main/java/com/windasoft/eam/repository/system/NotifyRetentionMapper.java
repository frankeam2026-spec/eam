package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.system.NotifyRetention;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotifyRetentionMapper extends BaseMapper<NotifyRetention> {
  @Insert("INSERT IGNORE INTO eam_notify_retention(tenant_id) VALUES(#{tenant})")
  void ensure(@Param("tenant") long tenant);
}
