package com.windasoft.eam.repository.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.windasoft.eam.entity.inventory.Material;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 物料所属的数据访问能力，候选的计数和取数使用同一条件。 */
@Repository
public class MaterialRepository {
  private final MaterialMapper mapper;
  private final JdbcTemplate db;

  public MaterialRepository(MaterialMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
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

  public LambdaQueryWrapper<Material> enabledCandidates(long tenant, String keyword) {
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

  public List<Map<String, Object>> lockMaterial(long tenant, long id) {
    return db.queryForList(
        "SELECT id,code,name,model,unit,enabled FROM inv_material WHERE tenant_id=? AND id=? FOR"
            + " UPDATE",
        tenant,
        id);
  }

  /** 基础单位改名只同步同租户、同ID且名称变化的物料；每行版本递增，原业务事务负责一起回滚。 */
  public void renameUnit(long tenant, long unitId, String name) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<Material>()
            .eq(Material::getTenantId, tenant)
            .eq(Material::getUnitId, unitId)
            .ne(Material::getUnit, name)
            .set(Material::getUnit, name)
            .setSql("row_version=row_version+1"));
  }

  /** 分类改名保留与单位相同的原子版本语义，不通过动态列名决定写入字段。 */
  public void renameCategory(long tenant, long categoryId, String name) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<Material>()
            .eq(Material::getTenantId, tenant)
            .eq(Material::getCategoryId, categoryId)
            .ne(Material::getCategory, name)
            .set(Material::getCategory, name)
            .setSql("row_version=row_version+1"));
  }
}
