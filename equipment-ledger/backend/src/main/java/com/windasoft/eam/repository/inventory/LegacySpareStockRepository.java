package com.windasoft.eam.repository.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.inventory.LegacySpareStock;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

/** LegacySpareStock 主仓储：集中维护租户条件及持久化操作，不决定业务授权和事务。 */
@Repository
public class LegacySpareStockRepository {
  private final LegacySpareStockMapper mapper;

  public LegacySpareStockRepository(LegacySpareStockMapper mapper) {
    this.mapper = mapper;
  }

  /** 历史领料安装仍需锁定旧备件记录；不重新开放旧库存新增和出库。 */
  public LegacySpareStock lockStock(long tenant, long id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<LegacySpareStock>()
            .eq(LegacySpareStock::getTenantId, tenant)
            .eq(LegacySpareStock::getId, id)
            .last("FOR UPDATE"));
  }

  public List<LegacySpareStock> stocks(long tenant, long company) {
    return mapper.selectList(
        new LambdaQueryWrapper<LegacySpareStock>()
            .eq(LegacySpareStock::getTenantId, tenant)
            .eq(LegacySpareStock::getCompanyId, company)
            .orderByAsc(LegacySpareStock::getCode));
  }

  /** 保养标准仅校验历史备件引用，不进行库存操作，因此不复用安装流程的 FOR UPDATE。 */
  public LegacySpareStock find(long tenant, long id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<LegacySpareStock>()
            .eq(LegacySpareStock::getTenantId, tenant)
            .eq(LegacySpareStock::getId, id));
  }

  /** 服务层按可查看菜单合并公司范围，仓储在数据库中落实；空范围必须返回空集合， 不能因省略空 IN 而扩大为整个租户。此查询不追加可用量或库存状态条件。 */
  public List<LegacySpareStock> listVisible(long tenant, boolean all, Set<Long> companies) {
    if (!all && companies.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<LegacySpareStock>()
            .eq(LegacySpareStock::getTenantId, tenant)
            .in(!all, LegacySpareStock::getCompanyId, companies)
            .orderByAsc(LegacySpareStock::getName, LegacySpareStock::getId));
  }
}
