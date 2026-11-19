package com.windasoft.eam.repository.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.inventory.InventoryBin;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryBinRepository {
  private static final String DEFAULT_CODE = "DEFAULT";
  private static final String DEFAULT_NAME = "默认库位";
  private final InventoryBinMapper mapper;

  public InventoryBinRepository(InventoryBinMapper mapper) {
    this.mapper = mapper;
  }

  /** 主数据更新已在库存事务内校验旧版本并锁定原行，写入后递增版本。 */
  public void saveMaster(long tenant, long id, InventoryBin row, boolean create) {
    if (create) {
      row.setTenantId(tenant);
      row.setId(id);
      mapper.insert(row);
      return;
    }
    var update =
        new LambdaUpdateWrapper<InventoryBin>()
            .eq(InventoryBin::getTenantId, tenant)
            .eq(InventoryBin::getId, id)
            .setSql("row_version=row_version+1");
    mapper.update(row, update);
  }

  /** 已有仓库沿用最早建立的库位，新增仓库才补建默认库位。 */
  public Long firstId(long tenant, long warehouse) {
    var first =
        mapper.selectOne(
            new LambdaQueryWrapper<InventoryBin>()
                .select(InventoryBin::getId)
                .eq(InventoryBin::getTenantId, tenant)
                .eq(InventoryBin::getWarehouseId, warehouse)
                .orderByAsc(InventoryBin::getId)
                .last("LIMIT 1"));
    return first == null ? null : first.getId();
  }

  public void createDefault(long tenant, long warehouse, long id) {
    var row = new InventoryBin();
    row.setId(id);
    row.setTenantId(tenant);
    row.setWarehouseId(warehouse);
    row.setCode(DEFAULT_CODE);
    row.setName(DEFAULT_NAME);
    mapper.insert(row);
  }
}
