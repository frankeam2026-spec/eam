package com.windasoft.eam.repository.system;

import com.windasoft.eam.entity.system.BrandingEvent;
import org.springframework.stereotype.Repository;

@Repository
public class BrandingEventRepository {
  private final BrandingEventMapper mapper;

  public BrandingEventRepository(BrandingEventMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(BrandingEvent event) {
    mapper.insert(event);
  }
}
