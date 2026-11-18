package com.windasoft.eam.repository.i18n;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.i18n.LanguageMessage;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class LanguageMessageRepository {
  private final LanguageMessageMapper mapper;

  public LanguageMessageRepository(LanguageMessageMapper mapper) {
    this.mapper = mapper;
  }

  public Map<String, String> messages(long tenant, String locale) {
    var result = new LinkedHashMap<String, String>();
    for (var row :
        mapper.selectList(
            new LambdaQueryWrapper<LanguageMessage>()
                .eq(LanguageMessage::getTenantId, tenant)
                .eq(LanguageMessage::getLocale, locale)))
      result.put(row.getMessageKey(), row.getMessageText());
    return result;
  }

  /** 已由语言发布事务及版本更新串行化，整包替换失败必须由外层事务一起回滚。 */
  public void replace(long tenant, String locale, Map<String, String> messages) {
    mapper.delete(
        new LambdaQueryWrapper<LanguageMessage>()
            .eq(LanguageMessage::getTenantId, tenant)
            .eq(LanguageMessage::getLocale, locale));
    if (messages.isEmpty()) return;
    var rows =
        messages.entrySet().stream()
            .map(
                e -> {
                  var row = new LanguageMessage();
                  row.setTenantId(tenant);
                  row.setLocale(locale);
                  row.setMessageKey(e.getKey());
                  row.setMessageText(e.getValue());
                  row.setModuleKey(
                      String.join(".", Arrays.stream(e.getKey().split("\\.")).limit(2).toList()));
                  return row;
                })
            .toList();
    mapper.insert(rows, 500);
  }
}
