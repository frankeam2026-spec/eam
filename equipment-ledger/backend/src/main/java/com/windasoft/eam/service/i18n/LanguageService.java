package com.windasoft.eam.service.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.BuiltinMessages;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.common.MessageRef;
import com.windasoft.eam.dto.i18n.LanguageContracts.LanguagePack;
import com.windasoft.eam.dto.i18n.LanguageContracts.LanguageView;
import com.windasoft.eam.dto.i18n.LanguageContracts.Preference;
import com.windasoft.eam.dto.i18n.LanguageContracts.Resource;
import com.windasoft.eam.dto.i18n.LanguageContracts.Validation;
import com.windasoft.eam.repository.i18n.LanguageEventRepository;
import com.windasoft.eam.repository.i18n.LanguageMessageRepository;
import com.windasoft.eam.repository.i18n.LanguageRepository;
import com.windasoft.eam.repository.system.UserPreferenceRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LanguageService {
  private record CachedMessages(int version, Map<String, String> messages) {}

  private final ConcurrentHashMap<String, CachedMessages> languageCache = new ConcurrentHashMap<>();
  private final LanguageRepository repo;
  private final LanguageMessageRepository translations;
  private final LanguageEventRepository events;
  private final UserPreferenceRepository preferences;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final HttpServletRequest request;
  private final ObjectMapper json;

  public LanguageService(
      LanguageRepository repo,
      LanguageMessageRepository translations,
      LanguageEventRepository events,
      UserPreferenceRepository preferences,
      AuthContext auth,
      PermissionService permissions,
      HttpServletRequest request,
      ObjectMapper json) {
    this.repo = repo;
    this.translations = translations;
    this.events = events;
    this.preferences = preferences;
    this.auth = auth;
    this.permissions = permissions;
    this.request = request;
    this.json = json;
  }

  @Value("${eam.security.demo-tenant-id:10001}")
  private long publicTenant;

  private long tenant() {
    try {
      return auth.tenantId();
    } catch (Exception e) {
      return publicTenant;
    }
  }

  public boolean enabled(String locale) {
    if (BuiltinMessages.NAMES.containsKey(locale)) return true;
    var row = repo.find(tenant(), locale);
    return row != null && row.getEnabled() == 1;
  }

  public String effective(String locale) {
    return enabled(locale) ? locale : "zh-CN";
  }

  public String currentLocale() {
    String h = null;
    try {
      h = request.getHeader("Accept-Language");
    } catch (IllegalStateException ignored) {
    }
    if (h != null)
      try {
        for (var range : Locale.LanguageRange.parse(h)) {
          String tag = Locale.forLanguageTag(range.getRange()).toLanguageTag();
          if (enabled(tag)) return tag;
        }
      } catch (IllegalArgumentException ignored) {
      }
    try {
      return effective(preferences.locale(auth.tenantId(), auth.userId()));
    } catch (Exception e) {
      return "zh-CN";
    }
  }

  public List<LanguageView> catalog(boolean manage) {
    var result = new LinkedHashMap<String, LanguageView>();
    for (String tag : List.of("zh-CN", "zh-TW", "en-US"))
      result.put(
          tag, new LanguageView(tag, BuiltinMessages.NAMES.get(tag), true, true, 0, null, 100));
    for (var row : repo.list(tenant()))
      result.put(
          row.getLocale(),
          new LanguageView(
              row.getLocale(),
              row.getNativeName(),
              row.getBuiltin() == 1,
              row.getEnabled() == 1,
              row.getRowVersion(),
              row.getUpdatedAt(),
              0));
    return result.values().stream()
        .filter(row -> manage || row.enabled())
        .map(
            row -> {
              long present =
                  BuiltinMessages.NAMES.containsKey(row.locale())
                      ? BuiltinMessages.get("zh-CN").size()
                      : overrides(row.locale()).size();
              double coverage =
                  Math.min(
                      100,
                      Math.round(10000.0 * present / BuiltinMessages.get("zh-CN").size()) / 100.0);
              return new LanguageView(
                  row.locale(),
                  row.nativeName(),
                  row.builtin(),
                  row.enabled(),
                  row.rowVersion(),
                  row.updatedAt(),
                  coverage);
            })
        .toList();
  }

  private Map<String, String> overrides(String locale) {
    return translations.messages(tenant(), locale);
  }

  @SuppressWarnings("unchecked")
  public Map<String, String> messages(String locale) {
    String actual = effective(locale);
    String cacheKey = "eam.i18n." + tenant() + "." + actual;
    try {
      var cached = request.getAttribute(cacheKey);
      if (cached instanceof Map) return (Map<String, String>) cached;
    } catch (IllegalStateException ignored) {
    }
    var metadata = repo.find(tenant(), actual);
    int version = metadata == null ? 0 : metadata.getRowVersion();
    var entry =
        languageCache.compute(
            cacheKey,
            (key, previous) -> {
              if (previous != null && previous.version() == version) return previous;
              var combined = new LinkedHashMap<>(BuiltinMessages.get("zh-CN"));
              if (BuiltinMessages.NAMES.containsKey(actual))
                combined.putAll(BuiltinMessages.get(actual));
              combined.putAll(overrides(actual));
              return new CachedMessages(version, Collections.unmodifiableMap(combined));
            });
    var out = entry.messages();
    try {
      request.setAttribute(cacheKey, out);
    } catch (IllegalStateException ignored) {
    }
    return out;
  }

  public String text(String key, Map<String, ?> params) {
    return BuiltinMessages.format(
        messages(currentLocale())
            .getOrDefault(key, BuiltinMessages.get("zh-CN").getOrDefault(key, "请求无法完成")),
        params);
  }

  public String text(String key) {
    return text(key, Map.of());
  }

  public String text(MessageRef ref) {
    return text(ref.key(), ref.params());
  }

  public Resource resource(String locale) {
    String actual = effective(locale);
    var row = repo.find(tenant(), actual);
    String revision = messages(actual).hashCode() + "-" + (row == null ? 0 : row.getRowVersion());
    return new Resource(actual, revision, messages(actual));
  }

  public Preference preference() {
    String saved = preferences.locale(auth.tenantId(), auth.userId());
    return new Preference(saved, effective(saved));
  }

  @Transactional
  public Preference savePreference(String locale) {
    if (!enabled(locale)) throw new LocalizedArgumentException("language.error.unavailable");
    preferences.saveLocale(auth.tenantId(), auth.userId(), locale);
    return preference();
  }

  private void access() {
    permissions.require("language", "查看");
  }

  public List<LanguageView> management() {
    access();
    return catalog(true);
  }

  public LanguagePack template(String locale) {
    access();
    var existing = repo.find(tenant(), locale);
    String name =
        existing == null
            ? BuiltinMessages.NAMES.getOrDefault(locale, "Français")
            : existing.getNativeName();
    Map<String, String> entries;
    if (BuiltinMessages.NAMES.containsKey(locale)) entries = messages(locale);
    else entries = existing == null ? BuiltinMessages.get("zh-CN") : overrides(locale);
    return new LanguagePack(1, locale, name, "ltr", entries);
  }

  private Set<String> placeholders(String value) {
    var result = new TreeSet<String>();
    var matcher = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}").matcher(value);
    while (matcher.find()) result.add(matcher.group(1));
    return result;
  }

  private Map<String, String> read(JsonNode pack, List<String> invalid, List<String> unknown) {
    if (!pack.path("formatVersion").isIntegralNumber() || pack.path("formatVersion").asInt() != 1)
      invalid.add(MessageContext.text("language.validation.version"));
    String locale = pack.path("locale").asText();
    if (!locale.matches("[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-(?:[A-Z]{2}|[0-9]{3}))?"))
      invalid.add(MessageContext.text("language.validation.locale"));
    if (Set.of("ar", "he", "fa", "ur").contains(locale.split("-")[0])
        || !"ltr".equals(pack.path("direction").asText("ltr")))
      invalid.add(MessageContext.text("language.validation.direction"));
    if (!pack.path("nativeName").isTextual()
        || pack.path("nativeName").asText().isBlank()
        || pack.path("nativeName").asText().length() > 100)
      invalid.add(MessageContext.text("language.validation.name"));
    var out = new LinkedHashMap<String, String>();
    var source = BuiltinMessages.get("zh-CN");
    var input = pack.path("messages");
    if (!input.isObject() || input.size() > 30000)
      invalid.add(MessageContext.text("language.validation.entries"));
    input
        .fields()
        .forEachRemaining(
            e -> {
              String k = e.getKey();
              JsonNode v = e.getValue();
              if (k.contains("__proto__") || k.contains("constructor") || k.contains("prototype")) {
                invalid.add(k + ": " + MessageContext.text("language.validation.key"));
                return;
              }
              if (!source.containsKey(k)) {
                unknown.add(k);
                return;
              }
              if (!v.isTextual()
                  || v.asText().length() > 6000
                  || Pattern.compile("<[/!a-zA-Z][^>]*>").matcher(v.asText()).find()) {
                invalid.add(k + ": " + MessageContext.text("language.validation.text"));
                return;
              }
              String value = v.asText();
              if (value.isBlank()) return;
              if (!placeholders(source.get(k)).equals(placeholders(value))) {
                invalid.add(k + ": " + MessageContext.text("language.validation.params"));
                return;
              }
              String plain = value.replaceAll("\\{[a-zA-Z0-9_]+\\}", "");
              boolean badCalendar =
                  k.startsWith("calendar.")
                      && (value.split("\\|", -1).length
                              != (k.startsWith("calendar.months") ? 12 : 7)
                          || Arrays.stream(value.split("\\|", -1)).anyMatch(String::isBlank));
              if (plain.contains("{")
                  || plain.contains("}")
                  || value.contains("@")
                  || (!k.startsWith("calendar.") && value.contains("|"))
                  || badCalendar) {
                invalid.add(k + ": " + MessageContext.text("language.validation.syntax"));
                return;
              }
              out.put(k, value);
            });
    return out;
  }

  public Validation validate(JsonNode pack) {
    access();
    if (pack.toString().getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024)
      throw new LocalizedArgumentException("language.error.tooLarge");
    var invalid = new ArrayList<String>();
    var unknown = new ArrayList<String>();
    var values = read(pack, invalid, unknown);
    String locale = pack.path("locale").asText();
    var before = overrides(locale);
    long changed =
        values.entrySet().stream()
            .filter(
                e ->
                    before.containsKey(e.getKey())
                        && !Objects.equals(before.get(e.getKey()), e.getValue()))
            .count();
    var missing = new ArrayList<>(BuiltinMessages.get("zh-CN").keySet());
    missing.removeAll(values.keySet());
    return new Validation(
        invalid.isEmpty(),
        invalid,
        unknown,
        missing,
        values.keySet().stream().filter(k -> !before.containsKey(k)).count(),
        changed,
        values.size(),
        BuiltinMessages.get("zh-CN").size());
  }

  /** 校验完整语言包后，以元数据版本更新串行化词条替换；审计和词条须与版本一起提交。 */
  @Transactional
  public Validation publish(String locale, int version, JsonNode pack) {
    access();
    var result = validate(pack);
    if (!locale.equals(pack.path("locale").asText()) || !result.valid())
      throw new LocalizedArgumentException("language.error.invalidPackage");
    var row = repo.find(tenant(), locale);
    if (row == null) {
      if (version != 0) conflict();
      repo.insert(
          tenant(),
          locale,
          pack.path("nativeName").asText(),
          auth.userId(),
          BuiltinMessages.NAMES.containsKey(locale));
    } else if (!repo.update(
        tenant(),
        locale,
        version,
        pack.path("nativeName").asText(),
        row.getEnabled() == 1,
        auth.userId())) conflict();
    var messages = read(pack, new ArrayList<>(), new ArrayList<>());
    translations.replace(tenant(), locale, messages);
    events.insert(
        tenant(), locale, "import", auth.userId(), row == null ? 1 : version + 1, messages.size());
    return result;
  }

  /** 内置语言不可停用；启停仍使用发布版本，避免覆盖并发导入。 */
  @Transactional
  public void state(String locale, int version, boolean enabled) {
    access();
    var row = repo.find(tenant(), locale);
    if (BuiltinMessages.NAMES.containsKey(locale) && !enabled)
      throw new LocalizedArgumentException("language.error.builtin");
    if (row == null
        || !repo.update(tenant(), locale, version, row.getNativeName(), enabled, auth.userId()))
      conflict();
    events.insert(tenant(), locale, enabled ? "enable" : "disable", auth.userId(), version + 1, 0);
  }

  /** 恢复仅清除内置语言覆盖词条，不改变系统自带翻译；版本与审计在同一事务。 */
  @Transactional
  public void restore(String locale, int version) {
    access();
    if (!BuiltinMessages.NAMES.containsKey(locale))
      throw new LocalizedArgumentException("language.error.builtinOnly");
    var row = repo.find(tenant(), locale);
    if (row == null) return;
    if (!repo.update(
        tenant(), locale, version, BuiltinMessages.NAMES.get(locale), true, auth.userId()))
      conflict();
    translations.replace(tenant(), locale, Map.of());
    events.insert(tenant(), locale, "restore", auth.userId(), version + 1, 0);
  }

  private void conflict() {
    throw new LocalizedStatusException(HttpStatus.CONFLICT, "language.error.conflict");
  }
}
