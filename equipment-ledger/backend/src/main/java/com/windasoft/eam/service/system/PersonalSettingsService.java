package com.windasoft.eam.service.system;

import com.windasoft.eam.dto.system.PersonalSettingsForm;
import com.windasoft.eam.repository.system.PersonalSettingsRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalSettingsService {
  private final PersonalSettingsRepository repository;
  private final AuthContext auth;
  private final com.windasoft.eam.service.i18n.LanguageService languages;

  public PersonalSettingsService(
      PersonalSettingsRepository repository,
      AuthContext auth,
      com.windasoft.eam.service.i18n.LanguageService languages) {
    this.repository = repository;
    this.auth = auth;
    this.languages = languages;
  }

  public Map<String, Object> settings() {
    var data = new java.util.LinkedHashMap<String, Object>(languages.preference());
    data.put("theme", repository.theme(auth.tenantId(), auth.userId()));
    data.put("roles", repository.roleNames(auth.tenantId(), auth.userId()));
    return Map.of("data", data);
  }

  @Transactional
  public Map<String, Object> save(PersonalSettingsForm form) {
    if (!Set.of("blue", "navy", "pink").contains(form.theme()))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.system.message0004");
    repository.saveTheme(auth.tenantId(), auth.userId(), form.theme());
    return Map.of("data", Map.of("theme", form.theme()));
  }
}
