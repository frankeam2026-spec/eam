package com.windasoft.eam.repository.system;

import com.windasoft.eam.entity.system.OrganizationEvent;
import org.springframework.stereotype.Repository;

/** 组织审计主仓储，只维护审计表；与组织变更共享事务的责任由组织Service承担。 */
@Repository
public class OrganizationEventRepository {
  private final OrganizationEventMapper mapper;

  public OrganizationEventRepository(OrganizationEventMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(OrganizationEvent row) {
    mapper.insert(row);
  }
}
