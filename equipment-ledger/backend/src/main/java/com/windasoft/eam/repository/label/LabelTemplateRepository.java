package com.windasoft.eam.repository.label;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.label.LabelTemplate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class LabelTemplateRepository {
  private final LabelTemplateMapper templates;

  public LabelTemplateRepository(LabelTemplateMapper templates) {
    this.templates = templates;
  }

  public List<LabelTemplate> list(long tenant) {
    return templates.selectList(
        new LambdaQueryWrapper<LabelTemplate>()
            .eq(LabelTemplate::getTenantId, tenant)
            .orderByDesc(LabelTemplate::getUpdatedAt, LabelTemplate::getId));
  }

  public LabelTemplate find(long tenant, Long id) {
    return templates.selectOne(
        new LambdaQueryWrapper<LabelTemplate>()
            .eq(LabelTemplate::getTenantId, tenant)
            .eq(LabelTemplate::getId, id));
  }

  public long nameCount(long tenant, String name, Long current) {
    var q =
        new LambdaQueryWrapper<LabelTemplate>()
            .eq(LabelTemplate::getTenantId, tenant)
            .eq(LabelTemplate::getName, name);
    if (current != null) q.ne(LabelTemplate::getId, current);
    return templates.selectCount(q);
  }

  public void insert(LabelTemplate row) {
    templates.insert(row);
  }

  public int update(LabelTemplate row) {
    return templates.updateById(row);
  }

  public void delete(long tenant, Long id) {
    templates.delete(
        new LambdaQueryWrapper<LabelTemplate>()
            .eq(LabelTemplate::getTenantId, tenant)
            .eq(LabelTemplate::getId, id));
  }
}
