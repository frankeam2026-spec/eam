package com.windasoft.eam.service.business;

import com.windasoft.eam.dto.business.BusinessConfigurationContracts.SchemaView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 发布沿用原事务：校验已有记录的默认值后再进入配置行锁与版本检查，失败时全部回滚。 */
@Service
public class BusinessPublicationService {
  private final BusinessConfigService configuration;
  private final BusinessValueService values;

  public BusinessPublicationService(
      BusinessConfigService configuration, BusinessValueService values) {
    this.configuration = configuration;
    this.values = values;
  }

  @Transactional
  public SchemaView publish(String object, int version) {
    var current = configuration.configuration(object);
    values.defaults(object, current.draft());
    return configuration.publish(object, version);
  }
}
