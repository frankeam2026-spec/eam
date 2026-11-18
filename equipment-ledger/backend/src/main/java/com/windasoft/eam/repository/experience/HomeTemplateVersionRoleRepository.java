package com.windasoft.eam.repository.experience;

import com.windasoft.eam.entity.experience.HomeTemplateVersionRole;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class HomeTemplateVersionRoleRepository {
  private final HomeTemplateVersionRoleMapper mapper;

  public HomeTemplateVersionRoleRepository(HomeTemplateVersionRoleMapper mapper) {
    this.mapper = mapper;
  }

  public List<HomeTemplateVersionRole> published(long tenant) {
    return mapper.published(tenant);
  }

  public void insert(long tenant, long template, int version, List<Long> roles) {
    var rows = new ArrayList<HomeTemplateVersionRole>();
    int at = 0;
    for (long role : new LinkedHashSet<>(roles)) {
      var row = new HomeTemplateVersionRole();
      row.setTenantId(tenant);
      row.setTemplateId(template);
      row.setVersion(version);
      row.setRoleId(role);
      row.setSortNo(at++);
      rows.add(row);
    }
    if (!rows.isEmpty()) mapper.insert(rows);
  }
}
