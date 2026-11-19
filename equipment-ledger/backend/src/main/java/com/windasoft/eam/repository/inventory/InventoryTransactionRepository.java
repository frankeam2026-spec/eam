package com.windasoft.eam.repository.inventory;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.entity.inventory.InventoryTransaction;
import java.math.BigDecimal;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryTransactionRepository {
  private final InventoryTransactionMapper mapper;

  public InventoryTransactionRepository(InventoryTransactionMapper mapper) {
    this.mapper = mapper;
  }

  /** 流水保留调用方算定的数量、金额符号及来源行，数据库继续约束同一来源不能重复记账。 */
  public void append(
      long tenant,
      long company,
      long warehouse,
      long balance,
      long document,
      long sourceLine,
      String kind,
      BigDecimal quantity,
      BigDecimal amount,
      long actorId) {
    var row = new InventoryTransaction();
    row.setId(IdWorker.getId());
    row.setTenantId(tenant);
    row.setCompanyId(company);
    row.setWarehouseId(warehouse);
    row.setBalanceId(balance);
    row.setDocumentId(document);
    row.setSourceLineId(sourceLine);
    row.setKind(kind);
    row.setQuantity(quantity);
    row.setAmount(amount);
    row.setActorId(actorId);
    mapper.insert(row);
  }
}
