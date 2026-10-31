package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.equipment.EquipmentHistory;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 历史事件单对象数据访问，不处理维修和巡检业务规则。 */
@Repository
public class EquipmentHistoryRepository {
  private final EquipmentHistoryMapper mapper;

  public EquipmentHistoryRepository(EquipmentHistoryMapper mapper) {
    this.mapper = mapper;
  }

  public List<EquipmentHistory> list(long tenant, long equipmentId) {
    return mapper.selectList(new LambdaQueryWrapper<EquipmentHistory>()
        .eq(EquipmentHistory::getTenantId, tenant)
        .eq(EquipmentHistory::getEquipmentId, equipmentId)
        .orderByDesc(EquipmentHistory::getOccurredAt, EquipmentHistory::getId));
  }
}
