package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.repair.RepairCollaborator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/** RepairCollaborator 主仓储：集中维护租户条件及持久化操作，不决定业务授权和事务。 */
@Repository
public class RepairCollaboratorRepository {
  private final RepairCollaboratorMapper mapper;

  public RepairCollaboratorRepository(RepairCollaboratorMapper mapper) {
    this.mapper = mapper;
  }

  public List<RepairCollaborator> list(long tenant, List<Long> orders) {
    if (orders.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<RepairCollaborator>()
            .eq(RepairCollaborator::getTenantId, tenant)
            .in(RepairCollaborator::getOrderId, orders)
            .orderByAsc(RepairCollaborator::getSortNo));
  }

  /** 由调用方事务执行整单替换；仅删除当前租户的当前工单关系。 */
  public void replace(long tenant, long order, List<RepairCollaborator> rows) {
    for (var row : rows) {
      if (!Objects.equals(row.getTenantId(), tenant) || !Objects.equals(row.getOrderId(), order)) {
        throw new IllegalArgumentException("协同人员关系范围不一致");
      }
    }
    mapper.delete(
        new LambdaQueryWrapper<RepairCollaborator>()
            .eq(RepairCollaborator::getTenantId, tenant)
            .eq(RepairCollaborator::getOrderId, order));
    for (var row : rows) {
      mapper.insert(row);
    }
  }
}
