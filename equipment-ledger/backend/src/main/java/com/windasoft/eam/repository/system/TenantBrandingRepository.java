package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.system.TenantBranding;
import org.springframework.stereotype.Repository;

/** 只维护租户品牌主体；图片和审计写入由服务在同一事务编排。 */
@Repository
public class TenantBrandingRepository {
  private final TenantBrandingMapper mapper;

  public TenantBrandingRepository(TenantBrandingMapper mapper) {
    this.mapper = mapper;
  }

  public TenantBranding find(long tenant) {
    return mapper.selectById(tenant);
  }

  public boolean ensureConfiguration(long tenant) {
    return mapper.ensureConfiguration(tenant) == 1;
  }

  /** 保存和取消素材统一先锁租户配置；FOR UPDATE使用当前读，避免旧快照覆盖最新引用。 */
  public TenantBranding lock(long tenant) {
    return mapper.selectOne(
        new LambdaQueryWrapper<TenantBranding>()
            .eq(TenantBranding::getTenantId, tenant)
            .last("FOR UPDATE"));
  }

  public int update(TenantBranding value, int version) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<TenantBranding>()
            .eq(TenantBranding::getTenantId, value.getTenantId())
            .eq(TenantBranding::getRowVersion, version)
            .set(TenantBranding::getSystemName, value.getSystemName())
            .set(TenantBranding::getLogoFileId, value.getLogoFileId())
            .set(TenantBranding::getBackgroundFileId, value.getBackgroundFileId())
            .set(TenantBranding::getBackgroundPosition, value.getBackgroundPosition())
            .set(TenantBranding::getUpdatedBy, value.getUpdatedBy())
            .setSql("row_version=row_version+1,updated_at=NOW()"));
  }
}
