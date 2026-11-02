package com.windasoft.eam.repository.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.inventory.LegacySpareStock;
import java.util.List;
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
}
