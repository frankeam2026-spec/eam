package com.windasoft.eam.repository.experience;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.dto.experience.HomeContracts.Published;
import com.windasoft.eam.entity.experience.HomeTemplate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class HomeTemplateRepository {
  private final HomeTemplateMapper mapper;

  public HomeTemplateRepository(HomeTemplateMapper mapper) {
    this.mapper = mapper;
  }

  public List<HomeTemplate> list(long tenant) {
    return mapper.selectList(
        new LambdaQueryWrapper<HomeTemplate>()
            .eq(HomeTemplate::getTenantId, tenant)
            .orderByAsc(HomeTemplate::getPriority)
            .orderByAsc(HomeTemplate::getId));
  }

  public HomeTemplate find(long tenant, long id, boolean lock) {
    var q =
        new LambdaQueryWrapper<HomeTemplate>()
            .eq(HomeTemplate::getTenantId, tenant)
            .eq(HomeTemplate::getId, id);
    if (lock) q.last("FOR UPDATE");
    return mapper.selectOne(q);
  }

  public void insert(HomeTemplate row) {
    mapper.insert(row);
  }

  public boolean insertPreset(HomeTemplate row) {
    return mapper.insertPreset(row) == 1;
  }

  public void update(HomeTemplate row) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<HomeTemplate>()
            .eq(HomeTemplate::getTenantId, row.getTenantId())
            .eq(HomeTemplate::getId, row.getId())
            .set(HomeTemplate::getName, row.getName())
            .set(HomeTemplate::getDraft, row.getDraft())
            .set(HomeTemplate::getPriority, row.getPriority())
            .setSql("row_version=row_version+1,updated_at=NOW(3)"));
  }

  public void overview(long tenant, long id, String value) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<HomeTemplate>()
            .eq(HomeTemplate::getTenantId, tenant)
            .eq(HomeTemplate::getId, id)
            .set(HomeTemplate::getOverviewDraft, value));
  }

  public void publish(long tenant, long id, int version, boolean preset) {
    var update =
        new LambdaUpdateWrapper<HomeTemplate>()
            .eq(HomeTemplate::getTenantId, tenant)
            .eq(HomeTemplate::getId, id)
            .set(HomeTemplate::getPublishedVersion, version);
    if (!preset) update.set(HomeTemplate::getEnabled, 1).setSql("row_version=row_version+1");
    mapper.update(null, update);
  }

  public void disable(long tenant, long id) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<HomeTemplate>()
            .eq(HomeTemplate::getTenantId, tenant)
            .eq(HomeTemplate::getId, id)
            .set(HomeTemplate::getEnabled, 0)
            .setSql("row_version=row_version+1"));
  }

  public List<Published> published(long tenant) {
    return mapper.published(tenant);
  }
}
