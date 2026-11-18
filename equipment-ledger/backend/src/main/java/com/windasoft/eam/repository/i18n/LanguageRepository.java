package com.windasoft.eam.repository.i18n;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.i18n.LanguageDefinition;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 语言元数据唯一仓储；词条和事件分别归各自主仓储。 */
@Repository
public class LanguageRepository {
  private final LanguageDefinitionMapper mapper;

  public LanguageRepository(LanguageDefinitionMapper mapper) {
    this.mapper = mapper;
  }

  public List<LanguageDefinition> list(long tenant) {
    return mapper.selectList(
        new LambdaQueryWrapper<LanguageDefinition>()
            .eq(LanguageDefinition::getTenantId, tenant)
            .orderByDesc(LanguageDefinition::getBuiltin)
            .orderByAsc(LanguageDefinition::getLocale));
  }

  public LanguageDefinition find(long tenant, String locale) {
    return mapper.selectOne(
        new LambdaQueryWrapper<LanguageDefinition>()
            .eq(LanguageDefinition::getTenantId, tenant)
            .eq(LanguageDefinition::getLocale, locale));
  }

  public boolean update(
      long tenant, String locale, int version, String name, boolean enabled, long actor) {
    return mapper.update(
            null,
            new LambdaUpdateWrapper<LanguageDefinition>()
                .eq(LanguageDefinition::getTenantId, tenant)
                .eq(LanguageDefinition::getLocale, locale)
                .eq(LanguageDefinition::getRowVersion, version)
                .set(LanguageDefinition::getNativeName, name)
                .set(LanguageDefinition::getEnabled, enabled ? 1 : 0)
                .set(LanguageDefinition::getUpdatedBy, actor)
                .setSql("row_version=row_version+1"))
        == 1;
  }

  public void insert(long tenant, String locale, String name, long actor, boolean builtin) {
    var row = new LanguageDefinition();
    row.setTenantId(tenant);
    row.setLocale(locale);
    row.setNativeName(name);
    row.setBuiltin(builtin ? 1 : 0);
    row.setEnabled(builtin ? 1 : 0);
    row.setRowVersion(1);
    row.setUpdatedBy(actor);
    mapper.insert(row);
  }
}
