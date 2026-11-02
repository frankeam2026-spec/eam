package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.repair.RepairEvent;
import java.util.List;
import org.springframework.stereotype.Repository;

/** RepairEvent 主仓储：集中维护租户条件及持久化操作，不决定业务授权和事务。 */
@Repository
public class RepairEventRepository {
  private final RepairEventMapper mapper;

  public RepairEventRepository(RepairEventMapper mapper) {
    this.mapper = mapper;
  }

  public void event(
      long id, long tenant, long order, String action, long actor, String name, String details) {
    var row = new RepairEvent();
    row.setId(id);
    row.setTenantId(tenant);
    row.setOrderId(order);
    row.setAction(action);
    row.setActorId(actor);
    row.setActorName(name);
    row.setDetailsJson(details);
    mapper.insertAtDatabaseTime(row);
  }

  public List<RepairEvent> events(long tenant, long order) {
    return mapper.selectList(
        new LambdaQueryWrapper<RepairEvent>()
            .eq(RepairEvent::getTenantId, tenant)
            .eq(RepairEvent::getOrderId, order)
            .orderByDesc(RepairEvent::getCreatedAt, RepairEvent::getId));
  }
}
