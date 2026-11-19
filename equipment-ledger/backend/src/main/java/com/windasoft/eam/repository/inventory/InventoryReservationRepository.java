package com.windasoft.eam.repository.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.inventory.InventoryReservation;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryReservationRepository {
  private final InventoryReservationMapper mapper;

  public InventoryReservationRepository(InventoryReservationMapper mapper) {
    this.mapper = mapper;
  }

  public void create(
      long id, long tenant, long requestId, long lineId, long balanceId, BigDecimal quantity) {
    var reservation = new InventoryReservation();
    reservation.setId(id);
    reservation.setTenantId(tenant);
    reservation.setRequisitionId(requestId);
    reservation.setRequisitionLineId(lineId);
    reservation.setBalanceId(balanceId);
    reservation.setQuantity(quantity);
    reservation.setRemainingQuantity(quantity);
    mapper.insert(reservation);
  }

  /** 释放前按仓位和预留行固定顺序加锁，随后按相同仓位顺序更新库存余额。 */
  public List<InventoryReservation> lockRemaining(long tenant, long requestId) {
    return mapper.selectList(
        new LambdaQueryWrapper<InventoryReservation>()
            .select(
                InventoryReservation::getId,
                InventoryReservation::getBalanceId,
                InventoryReservation::getRemainingQuantity)
            .eq(InventoryReservation::getTenantId, tenant)
            .eq(InventoryReservation::getRequisitionId, requestId)
            .gt(InventoryReservation::getRemainingQuantity, BigDecimal.ZERO)
            .orderByAsc(InventoryReservation::getBalanceId, InventoryReservation::getId)
            .last("FOR UPDATE"));
  }

  /** 预览与确认之间预留可能变化，扣减只接受当前足够的预留并返回更新结果。 */
  public int consume(long tenant, long id, BigDecimal quantity) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<InventoryReservation>()
            .eq(InventoryReservation::getTenantId, tenant)
            .eq(InventoryReservation::getId, id)
            .ge(InventoryReservation::getRemainingQuantity, quantity)
            .setSql("remaining_quantity=remaining_quantity-{0}", quantity));
  }

  public void release(long tenant, long id) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<InventoryReservation>()
            .eq(InventoryReservation::getTenantId, tenant)
            .eq(InventoryReservation::getId, id)
            .set(InventoryReservation::getRemainingQuantity, BigDecimal.ZERO));
  }
}
