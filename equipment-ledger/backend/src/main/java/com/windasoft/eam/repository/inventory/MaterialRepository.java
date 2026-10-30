package com.windasoft.eam.repository.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.windasoft.eam.entity.inventory.Material;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 物料所属的数据访问能力，候选的计数和取数使用同一条件。 */
@Repository
public class MaterialRepository {
  private final MaterialMapper mapper;

  public MaterialRepository(MaterialMapper mapper) {
    this.mapper = mapper;
  }

  public long countEnabledCandidates(long tenant, String keyword) {
    return mapper.selectCount(enabledCandidates(tenant, keyword));
  }

  public List<Material> enabledCandidates(long tenant, String keyword, int page, int size) {
    var query =
        enabledCandidates(tenant, keyword)
            .select(
                Material::getId,
                Material::getCode,
                Material::getName,
                Material::getModel,
                Material::getUnit,
                Material::getCategory)
            .orderByAsc(Material::getCode, Material::getId);
    // 总数由独立计数取得，避免分页插件再发一次COUNT；插件以long计算偏移。
    return mapper.selectPage(new Page<Material>(page, size, false), query).getRecords();
  }

  private LambdaQueryWrapper<Material> enabledCandidates(long tenant, String keyword) {
    return new LambdaQueryWrapper<Material>()
        .eq(Material::getTenantId, tenant)
        .eq(Material::getEnabled, true)
        .and(
            q ->
                q.like(Material::getCode, keyword)
                    .or()
                    .like(Material::getName, keyword)
                    .or()
                    .like(Material::getModel, keyword)
                    .or()
                    .like(Material::getCategory, keyword));
  }
}
