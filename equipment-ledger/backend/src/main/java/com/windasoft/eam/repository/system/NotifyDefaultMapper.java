package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.system.NotifyDefault;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotifyDefaultMapper extends BaseMapper<NotifyDefault> {
  @Insert(
      "INSERT IGNORE INTO eam_notify_default(tenant_id,business_kind) VALUES(#{tenant},#{kind})")
  void ensure(@Param("tenant") long tenant, @Param("kind") String kind);
}
