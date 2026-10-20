package com.windasoft.eam.service.business;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates publication validation and version creation in a single transaction. */
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
  public Map<String, Object> publish(String object, int version) {
    var current = configuration.configuration(object);
    values.defaults(object, (JsonNode) current.get("draft"));
    return configuration.publish(object, version);
  }
}
