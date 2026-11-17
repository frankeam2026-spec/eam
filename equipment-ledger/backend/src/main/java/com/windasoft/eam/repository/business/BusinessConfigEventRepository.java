package com.windasoft.eam.repository.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.EventView;
import com.windasoft.eam.entity.business.BusinessConfigEvent;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessConfigEventRepository {
  private final BusinessConfigEventMapper mapper;

  public BusinessConfigEventRepository(BusinessConfigEventMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(BusinessConfigEvent event) {
    mapper.insert(event);
  }

  /** 管理配置审计只取record_id为空；业务记录审计仍按真实记录筛选，不混淆两种事件。 */
  public List<EventView> events(long tenant, String object, Long record) {
    var query =
        new LambdaQueryWrapper<BusinessConfigEvent>()
            .select(
                BusinessConfigEvent::getActorName,
                BusinessConfigEvent::getAction,
                BusinessConfigEvent::getDetails,
                BusinessConfigEvent::getCreatedAt)
            .eq(BusinessConfigEvent::getTenantId, tenant)
            .eq(BusinessConfigEvent::getObjectKey, object)
            .isNull(record == null, BusinessConfigEvent::getRecordId)
            .eq(record != null, BusinessConfigEvent::getRecordId, record)
            .orderByDesc(BusinessConfigEvent::getCreatedAt, BusinessConfigEvent::getId)
            .last("LIMIT 100");
    return mapper.selectList(query).stream()
        .map(
            row ->
                new EventView(
                    row.getActorName(), row.getAction(), row.getDetails(), row.getCreatedAt()))
        .toList();
  }
}
