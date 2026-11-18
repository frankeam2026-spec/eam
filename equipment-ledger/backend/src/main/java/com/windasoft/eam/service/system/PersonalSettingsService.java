package com.windasoft.eam.service.system;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.dto.system.PersonalSettingsForm;
import com.windasoft.eam.dto.system.PersonalSettingsView;
import com.windasoft.eam.dto.system.ThemeView;
import com.windasoft.eam.repository.system.RoleManagementRepository;
import com.windasoft.eam.repository.system.UserPreferenceRepository;
import com.windasoft.eam.service.i18n.LanguageService;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalSettingsService {
  private final UserPreferenceRepository preferences;
  private final RoleManagementRepository roles;
  private final AuthContext auth;
  private final LanguageService languages;

  public PersonalSettingsService(
      UserPreferenceRepository preferences,
      RoleManagementRepository roles,
      AuthContext auth,
      LanguageService languages) {
    this.preferences = preferences;
    this.roles = roles;
    this.auth = auth;
    this.languages = languages;
  }

  /** 语言有效性仍以LanguageService为权威；只读取当前可信用户的皮肤与有效角色。 */
  public PersonalSettingsView settings() {
    var locale = languages.preference();
    return new PersonalSettingsView(
        locale.locale(),
        locale.effectiveLocale(),
        preferences.theme(auth.tenantId(), auth.userId()),
        roles.roleNames(auth.tenantId(), auth.userId()));
  }

  @Transactional
  public ThemeView save(PersonalSettingsForm form) {
    if (!Set.of("blue", "navy", "pink").contains(form.theme()))
      throw new LocalizedArgumentException("server.system.message0004");
    preferences.saveTheme(auth.tenantId(), auth.userId(), form.theme());
    return new ThemeView(form.theme());
  }
}
