package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.system.TenantBranding;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantBrandingMapper extends BaseMapper<TenantBranding> {
  /** 首次保存可能并发，INSERT IGNORE以数据库主键保证初始化唯一；先查后插不等价。 */
  @Insert("INSERT IGNORE INTO eam_tenant_branding(tenant_id) VALUES(#{tenant})")
  int ensureConfiguration(@Param("tenant") long tenant);
}
