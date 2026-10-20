package com.windasoft.eam.service.common;

import com.windasoft.eam.repository.common.DefinitionNumberRepository;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lifetime sequences are independent of monthly task numbers and legacy Snowflake codes. */
@Service
public class DefinitionNumberService {
  private final DefinitionNumberRepository repository;

  public DefinitionNumberService(DefinitionNumberRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public String next(long tenant, String prefix) {
    String code;
    do {
      code = prefix + "-" + String.format(Locale.ROOT, "%04d", repository.next(tenant, prefix));
    } while (repository.exists(tenant, prefix, code));
    return code;
  }
}
