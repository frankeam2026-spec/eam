package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.system.BrandingAsset;
import org.springframework.stereotype.Repository;

@Repository
public class BrandingAssetRepository {
  private final BrandingAssetMapper mapper;

  public BrandingAssetRepository(BrandingAssetMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(BrandingAsset asset) {
    mapper.insert(asset);
  }

  public BrandingAsset find(long tenant, String id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<BrandingAsset>()
            .eq(BrandingAsset::getTenantId, tenant)
            .eq(BrandingAsset::getId, id));
  }

  /** 服务已先锁租户配置；元信息使用当前读并保留共享锁到提交，防止校验后素材消失，不读取BLOB。 */
  public BrandingAsset lockMetadata(long tenant, String id, String kind) {
    return mapper.selectOne(
        new LambdaQueryWrapper<BrandingAsset>()
            .select(BrandingAsset::getId, BrandingAsset::getCreatedBy)
            .eq(BrandingAsset::getTenantId, tenant)
            .eq(BrandingAsset::getId, id)
            .eq(BrandingAsset::getKind, kind)
            .last("FOR SHARE"));
  }

  public int discardUnreferenced(long tenant, long user, String id) {
    return mapper.discardUnreferenced(tenant, user, id);
  }
}
