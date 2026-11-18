package com.windasoft.eam.repository.experience;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.experience.HomeTemplateVersion;
import org.springframework.stereotype.Repository;

@Repository
public class HomeTemplateVersionRepository {
  private final HomeTemplateVersionMapper mapper;

  public HomeTemplateVersionRepository(HomeTemplateVersionMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(HomeTemplateVersion row) {
    mapper.insert(row);
  }

  public boolean exists(long tenant, long template, int version) {
    return mapper.exists(
        new LambdaQueryWrapper<HomeTemplateVersion>()
            .eq(HomeTemplateVersion::getTenantId, tenant)
            .eq(HomeTemplateVersion::getTemplateId, template)
            .eq(HomeTemplateVersion::getVersion, version));
  }
}
