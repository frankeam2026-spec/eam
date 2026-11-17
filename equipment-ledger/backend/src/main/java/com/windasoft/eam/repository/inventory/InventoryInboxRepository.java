package com.windasoft.eam.repository.inventory;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.inventory.InventoryInbox;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 库存消息主仓储；本次接入工作流，不改变其他库存用例的历史写入路径。 */
@Repository
public class InventoryInboxRepository {
  private final InventoryInboxMapper mapper;

  public InventoryInboxRepository(InventoryInboxMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(InventoryInbox row) {
    mapper.insert(row);
  }

  public void complete(long tenant, List<Long> ids) {
    if (ids.isEmpty()) {
      return;
    }
    mapper.update(
        null,
        new LambdaUpdateWrapper<InventoryInbox>()
            .eq(InventoryInbox::getTenantId, tenant)
            .in(InventoryInbox::getId, ids)
            .set(InventoryInbox::getDone, 1));
  }
}
