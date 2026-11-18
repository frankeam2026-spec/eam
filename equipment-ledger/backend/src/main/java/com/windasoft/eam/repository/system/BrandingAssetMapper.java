package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.system.BrandingAsset;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BrandingAssetMapper extends BaseMapper<BrandingAsset> {
  /** 取消上传必须原子检查当前配置和历史审计；JSON_SEARCH及跨表NOT EXISTS不能用普通单表删除替代。 */
  @Delete(
      """
      DELETE FROM eam_branding_asset
      WHERE id=#{id} AND tenant_id=#{tenant} AND created_by=#{user}
      AND NOT EXISTS (SELECT 1 FROM eam_tenant_branding WHERE tenant_id=#{tenant}
        AND (logo_file_id=#{id} OR background_file_id=#{id}))
      AND NOT EXISTS (SELECT 1 FROM eam_branding_event WHERE tenant_id=#{tenant}
        AND JSON_SEARCH(after_json,'one',#{id}) IS NOT NULL)
      """)
  int discardUnreferenced(
      @Param("tenant") long tenant, @Param("user") long user, @Param("id") String id);
}
