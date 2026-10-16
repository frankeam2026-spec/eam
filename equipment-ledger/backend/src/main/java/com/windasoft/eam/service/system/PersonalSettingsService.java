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

  public PersonalSettingsService(PersonalSettingsRepository repository, AuthContext auth) {
    this.repository = repository;
    this.auth = auth;
  }

  public Map<String, Object> settings() {
    return Map.of("data", Map.of("theme", repository.theme(auth.tenantId(), auth.userId()),
        "roles", repository.roleNames(auth.tenantId(), auth.userId())));
  }

  @Transactional
  public Map<String, Object> save(PersonalSettingsForm form) {
    if (!Set.of("blue", "navy", "pink").contains(form.theme()))
      throw new IllegalArgumentException("请选择有效主题");
    repository.saveTheme(auth.tenantId(), auth.userId(), form.theme());
    return Map.of("data", Map.of("theme", form.theme()));
  }
}
