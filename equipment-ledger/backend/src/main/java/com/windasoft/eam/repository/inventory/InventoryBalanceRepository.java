package com.windasoft.eam.repository.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.inventory.InventoryBalance;
import java.math.BigDecimal;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryBalanceRepository {
  private final InventoryBalanceMapper mapper;

  public InventoryBalanceRepository(InventoryBalanceMapper mapper) {
    this.mapper = mapper;
  }

  public void lock(long tenant, long balanceId) {
    var row =
        mapper.selectOne(
            new LambdaQueryWrapper<InventoryBalance>()
                .select(InventoryBalance::getId)
                .eq(InventoryBalance::getTenantId, tenant)
                .eq(InventoryBalance::getId, balanceId)
                .last("FOR UPDATE"));
    if (row == null) throw new EmptyResultDataAccessException(1);
  }

  /** 余额由单据事务先锁定，过账时原子更新数量和版本，不直接覆盖原库存。 */
  public void changeQuantity(long tenant, long balanceId, BigDecimal delta) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<InventoryBalance>()
            .eq(InventoryBalance::getTenantId, tenant)
            .eq(InventoryBalance::getId, balanceId)
            .setSql("quantity=quantity+{0},row_version=row_version+1", delta));
  }

  /** 出库同时消费实物与本单预留，保留数量检查和受影响行数供 Service 处理竞争失败。 */
  public int takeForIssue(long tenant, long balanceId, BigDecimal quantity, BigDecimal reserved) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<InventoryBalance>()
            .eq(InventoryBalance::getTenantId, tenant)
            .eq(InventoryBalance::getId, balanceId)
            .ge(InventoryBalance::getQuantity, quantity)
            .ge(InventoryBalance::getReservedQuantity, reserved)
            .setSql(
                "quantity=quantity-{0},reserved_quantity=reserved_quantity-{1},row_version=row_version+1",
                quantity, reserved));
  }

  /** 正负增量分别用于预留和释放；库存约束和版本递增由数据库同一次更新执行。 */
  public void changeReserved(long tenant, long balanceId, BigDecimal delta) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<InventoryBalance>()
            .eq(InventoryBalance::getTenantId, tenant)
            .eq(InventoryBalance::getId, balanceId)
            .setSql("reserved_quantity=reserved_quantity+{0},row_version=row_version+1", delta));
  }
}
