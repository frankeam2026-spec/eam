package com.windasoft.eam.repository.experience;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.experience.HomeTemplateRole;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class HomeTemplateRoleRepository {
  private final HomeTemplateRoleMapper mapper;

  public HomeTemplateRoleRepository(HomeTemplateRoleMapper mapper) {
    this.mapper = mapper;
  }

  public List<HomeTemplateRole> find(long tenant, Collection<Long> templates) {
    if (templates.isEmpty()) return List.of();
    return mapper.selectList(
        new LambdaQueryWrapper<HomeTemplateRole>()
            .eq(HomeTemplateRole::getTenantId, tenant)
            .in(HomeTemplateRole::getTemplateId, templates)
            .orderByAsc(HomeTemplateRole::getSortNo));
  }

  public void replace(long tenant, long template, List<Long> roles) {
    mapper.delete(
        new LambdaQueryWrapper<HomeTemplateRole>()
            .eq(HomeTemplateRole::getTenantId, tenant)
            .eq(HomeTemplateRole::getTemplateId, template));
    var rows = new ArrayList<HomeTemplateRole>();
    int at = 0;
    for (long role : new LinkedHashSet<>(roles)) {
      var row = new HomeTemplateRole();
      row.setTenantId(tenant);
      row.setTemplateId(template);
      row.setRoleId(role);
      row.setSortNo(at++);
      rows.add(row);
    }
    if (!rows.isEmpty()) mapper.insert(rows);
  }
}
