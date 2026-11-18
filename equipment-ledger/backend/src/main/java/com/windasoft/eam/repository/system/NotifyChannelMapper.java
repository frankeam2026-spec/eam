package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.system.NotifyChannel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotifyChannelMapper extends BaseMapper<NotifyChannel> {
  @Insert("INSERT IGNORE INTO eam_notify_channel(tenant_id) VALUES(#{tenant})")
  void ensure(@Param("tenant") long tenant);
}
