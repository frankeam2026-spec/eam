package com.windasoft.eam.repository.experience;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.experience.HomeLayout;
import org.springframework.stereotype.Repository;

@Repository
public class HomeLayoutRepository {
  private final HomeLayoutMapper mapper;

  public HomeLayoutRepository(HomeLayoutMapper mapper) {
    this.mapper = mapper;
  }

  public HomeLayout find(long tenant, long user, long template) {
    return mapper.selectOne(
        new LambdaQueryWrapper<HomeLayout>()
            .eq(HomeLayout::getTenantId, tenant)
            .eq(HomeLayout::getUserId, user)
            .eq(HomeLayout::getTemplateId, template));
  }

  public void insert(long tenant, long user, long template, int version, String payload) {
    var row = new HomeLayout();
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
        new LambdaUpdateWrapper<HomeLayout>()
            .eq(HomeLayout::getTenantId, tenant)
            .eq(HomeLayout::getUserId, user)
            .eq(HomeLayout::getTemplateId, template)
            .eq(HomeLayout::getRowVersion, rowVersion)
            .set(HomeLayout::getTemplateVersion, version)
            .set(HomeLayout::getPayload, payload)
            .setSql("row_version=row_version+1,updated_at=NOW(3)"));
  }

  public void delete(long tenant, long user, long template) {
    mapper.delete(
        new LambdaQueryWrapper<HomeLayout>()
            .eq(HomeLayout::getTenantId, tenant)
            .eq(HomeLayout::getUserId, user)
            .eq(HomeLayout::getTemplateId, template));
  }
}
