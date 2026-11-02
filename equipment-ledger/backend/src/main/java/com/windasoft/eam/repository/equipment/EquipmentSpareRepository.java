package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.dto.equipment.EquipmentInstalledSpareView;
import com.windasoft.eam.entity.equipment.EquipmentSpare;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Repository;

/** EquipmentSpare 主仓储：集中维护租户条件及持久化操作，不决定业务授权和事务。 */
@Repository
public class EquipmentSpareRepository {
  private final EquipmentSpareMapper mapper;

  public EquipmentSpareRepository(EquipmentSpareMapper mapper) {
    this.mapper = mapper;
  }

  public List<EquipmentInstalledSpareView> installed(long tenant, long equipment) {
    return mapper.installed(tenant, equipment);
  }

  public void install(
      long id, long tenant, long equipment, long stock, long order, BigDecimal quantity) {
    var row = new EquipmentSpare();
    row.setId(id);
    row.setTenantId(tenant);
    row.setEquipmentId(equipment);
    row.setStockId(stock);
    row.setOrderId(order);
    row.setQuantity(quantity);
    row.setStatus("在用");
    mapper.insertAtDatabaseTime(row);
  }

  /** 仅在用记录可拆卸，调用方必须检查影响行数并与履历写入处于同一事务。 */
  public int remove(long tenant, long equipment, long id) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<EquipmentSpare>()
            .eq(EquipmentSpare::getTenantId, tenant)
            .eq(EquipmentSpare::getEquipmentId, equipment)
            .eq(EquipmentSpare::getId, id)
            .eq(EquipmentSpare::getStatus, "在用")
            .set(EquipmentSpare::getStatus, "已换下")
            .setSql("removed_at=NOW()"));
  }
}
