package com.windasoft.eam.repository.i18n;

import com.windasoft.eam.entity.i18n.LanguageEvent;
import org.springframework.stereotype.Repository;

@Repository
public class LanguageEventRepository {
  private final LanguageEventMapper mapper;

  public LanguageEventRepository(LanguageEventMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(
      long tenant, String locale, String action, long actor, int version, int count) {
    var row = new LanguageEvent();
    row.setTenantId(tenant);
    row.setLocale(locale);
    row.setActionCode(action);
    row.setActorId(actor);
    row.setRowVersion(version);
    row.setMessageCount(count);
    mapper.insert(row);
  }
}
