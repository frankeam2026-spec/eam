package com.windasoft.eam.repository.experience;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.experience.HomeOverviewLayout;
import org.springframework.stereotype.Repository;

@Repository
public class HomeOverviewRepository {
  private final HomeOverviewLayoutMapper mapper;

  public HomeOverviewRepository(HomeOverviewLayoutMapper mapper) {
    this.mapper = mapper;
  }

  public HomeOverviewLayout find(long tenant, long user, long template) {
    return mapper.selectOne(
        new LambdaQueryWrapper<HomeOverviewLayout>()
            .eq(HomeOverviewLayout::getTenantId, tenant)
            .eq(HomeOverviewLayout::getUserId, user)
            .eq(HomeOverviewLayout::getTemplateId, template));
  }

  public HomeOverviewLayout latest(long tenant, long user) {
    return mapper.selectOne(
        new LambdaQueryWrapper<HomeOverviewLayout>()
            .eq(HomeOverviewLayout::getTenantId, tenant)
            .eq(HomeOverviewLayout::getUserId, user)
            .orderByDesc(HomeOverviewLayout::getUpdatedAt)
            .orderByAsc(HomeOverviewLayout::getTemplateId)
            .last("LIMIT 1"));
  }

  public void insert(long tenant, long user, long template, int version, String payload) {
    var row = new HomeOverviewLayout();
    row.setTenantId(tenant);
    row.setUserId(user);
    row.setTemplateId(template);
    row.setTemplateVersion(version);
    row.setPayload(payload);
    mapper.insert(row);
  }

  public int update(
      long tenant, long user, long template, int version, int rowVersion, String payload) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<HomeOverviewLayout>()
            .eq(HomeOverviewLayout::getTenantId, tenant)
            .eq(HomeOverviewLayout::getUserId, user)
            .eq(HomeOverviewLayout::getTemplateId, template)
            .eq(HomeOverviewLayout::getRowVersion, rowVersion)
            .set(HomeOverviewLayout::getTemplateVersion, version)
            .set(HomeOverviewLayout::getPayload, payload)
            .setSql("row_version=row_version+1,updated_at=NOW(3)"));
  }
}
