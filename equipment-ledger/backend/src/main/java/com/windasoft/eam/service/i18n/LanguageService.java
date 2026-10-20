package com.windasoft.eam.service.i18n;

import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.common.*;
import com.windasoft.eam.repository.i18n.LanguageRepository;
import com.windasoft.eam.service.system.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.regex.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LanguageService {
  private record CachedMessages(int version, Map<String, String> messages) {}

  private final java.util.concurrent.ConcurrentHashMap<String, CachedMessages> languageCache =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final LanguageRepository repo;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final HttpServletRequest request;
  private final ObjectMapper json;

  public LanguageService(
      LanguageRepository repo,
      AuthContext auth,
      PermissionService permissions,
      HttpServletRequest request,
      ObjectMapper json) {
    this.repo = repo;
    this.auth = auth;
    this.permissions = permissions;
    this.request = request;
    this.json = json;
  }

  @org.springframework.beans.factory.annotation.Value("${eam.security.demo-tenant-id:10001}")
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
    return row != null && ((Number) row.get("enabled")).intValue() == 1;
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
      return effective(repo.preference(auth.tenantId(), auth.userId()));
    } catch (Exception e) {
      return "zh-CN";
    }
  }

  public List<Map<String, Object>> catalog(boolean manage) {
    var result = new LinkedHashMap<String, Map<String, Object>>();
    for (String tag : List.of("zh-CN", "zh-TW", "en-US")) {
      var e = Map.entry(tag, BuiltinMessages.NAMES.get(tag));
      var row = new LinkedHashMap<String, Object>();
      row.put("locale", e.getKey());
      row.put("nativeName", e.getValue());
      row.put("builtin", true);
      row.put("enabled", true);
      row.put("rowVersion", 0);
      result.put(e.getKey(), row);
    }
    for (var row : repo.list(tenant())) {
      var copy = new LinkedHashMap<>(row);
      copy.put("builtin", ((Number) row.get("builtin")).intValue() == 1);
      copy.put("enabled", ((Number) row.get("enabled")).intValue() == 1);
      result.put(String.valueOf(row.get("locale")), copy);
    }
    for (var row : result.values()) {
      String locale = String.valueOf(row.get("locale"));
      var translated = overrides(locale);
      long present =
          BuiltinMessages.NAMES.containsKey(locale)
              ? BuiltinMessages.get("zh-CN").size()
              : translated.size();
      row.put(
          "coverage",
          Math.min(
              100, Math.round(10000.0 * present / BuiltinMessages.get("zh-CN").size()) / 100.0));
    }
    return result.values().stream()
        .filter(r -> manage || Boolean.TRUE.equals(r.get("enabled")))
        .toList();
  }

  private Map<String, String> overrides(String locale) {
    return repo.messages(tenant(), locale);
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
    int version = metadata == null ? 0 : ((Number) metadata.get("row_version")).intValue();
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

  public Map<String, Object> resource(String locale) {
    String actual = effective(locale);
    var row = repo.find(tenant(), actual);
    String revision =
        messages(actual).hashCode() + "-" + (row == null ? 0 : row.get("row_version"));
    return Map.of("locale", actual, "version", revision, "messages", messages(actual));
  }

  public Map<String, Object> preference() {
    String saved = repo.preference(auth.tenantId(), auth.userId());
    return Map.of("locale", saved, "effectiveLocale", effective(saved));
  }

  @Transactional
  public Map<String, Object> savePreference(String locale) {
    if (!enabled(locale)) throw new LocalizedArgumentException("language.error.unavailable");
    repo.preference(auth.tenantId(), auth.userId(), locale);
    return preference();
  }

  private void access() {
    permissions.require("language", "查看");
  }

  public List<Map<String, Object>> management() {
    access();
    return catalog(true);
  }

  public Map<String, Object> template(String locale) {
    access();
    var existing = repo.find(tenant(), locale);
    String name =
        existing == null
            ? BuiltinMessages.NAMES.getOrDefault(locale, "Français")
            : String.valueOf(existing.get("native_name"));
    Map<String, String> entries;
    if (BuiltinMessages.NAMES.containsKey(locale)) entries = messages(locale);
    else entries = existing == null ? BuiltinMessages.get("zh-CN") : overrides(locale);
    return Map.of(
        "formatVersion",
        1,
        "locale",
        locale,
        "nativeName",
        name,
        "direction",
        "ltr",
        "messages",
        entries);
  }

  private Set<String> placeholders(String value) {
    var result = new TreeSet<String>();
    var matcher = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}").matcher(value);
    while (matcher.find()) result.add(matcher.group(1));
    return result;
  }

  private Map<String, String> read(JsonNode pack, List<String> invalid, List<String> unknown) {
    if (!pack.path("formatVersion").isIntegralNumber() || pack.path("formatVersion").asInt() != 1)
      invalid.add(com.windasoft.eam.common.MessageContext.text("language.validation.version"));
    String locale = pack.path("locale").asText();
    if (!locale.matches("[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-(?:[A-Z]{2}|[0-9]{3}))?"))
      invalid.add(com.windasoft.eam.common.MessageContext.text("language.validation.locale"));
    if (Set.of("ar", "he", "fa", "ur").contains(locale.split("-")[0])
        || !"ltr".equals(pack.path("direction").asText("ltr")))
      invalid.add(com.windasoft.eam.common.MessageContext.text("language.validation.direction"));
    if (!pack.path("nativeName").isTextual()
        || pack.path("nativeName").asText().isBlank()
        || pack.path("nativeName").asText().length() > 100)
      invalid.add(com.windasoft.eam.common.MessageContext.text("language.validation.name"));
    var out = new LinkedHashMap<String, String>();
    var source = BuiltinMessages.get("zh-CN");
    var input = pack.path("messages");
    if (!input.isObject() || input.size() > 30000)
      invalid.add(com.windasoft.eam.common.MessageContext.text("language.validation.entries"));
    input
        .fields()
        .forEachRemaining(
            e -> {
              String k = e.getKey();
              JsonNode v = e.getValue();
              if (k.contains("__proto__") || k.contains("constructor") || k.contains("prototype")) {
                invalid.add(
                    k
                        + ": "
                        + com.windasoft.eam.common.MessageContext.text("language.validation.key"));
                return;
              }
              if (!source.containsKey(k)) {
                unknown.add(k);
                return;
              }
              if (!v.isTextual()
                  || v.asText().length() > 6000
                  || Pattern.compile("<[/!a-zA-Z][^>]*>").matcher(v.asText()).find()) {
                invalid.add(
                    k
                        + ": "
                        + com.windasoft.eam.common.MessageContext.text("language.validation.text"));
                return;
              }
              String value = v.asText();
              if (value.isBlank()) return;
              if (!placeholders(source.get(k)).equals(placeholders(value))) {
                invalid.add(
                    k
                        + ": "
                        + com.windasoft.eam.common.MessageContext.text(
                            "language.validation.params"));
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
                invalid.add(
                    k
                        + ": "
                        + com.windasoft.eam.common.MessageContext.text(
                            "language.validation.syntax"));
                return;
              }
              out.put(k, value);
            });
    return out;
  }

  public Map<String, Object> validate(JsonNode pack) {
    access();
    if (pack.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2 * 1024 * 1024)
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
    return Map.of(
        "valid",
        invalid.isEmpty(),
        "invalid",
        invalid,
        "unknown",
        unknown,
        "missing",
        missing,
        "added",
        values.keySet().stream().filter(k -> !before.containsKey(k)).count(),
        "changed",
        changed,
        "accepted",
        values.size(),
        "total",
        BuiltinMessages.get("zh-CN").size());
  }

  @Transactional
  public Map<String, Object> publish(String locale, int version, JsonNode pack) {
    access();
    var result = validate(pack);
    if (!locale.equals(pack.path("locale").asText()) || !Boolean.TRUE.equals(result.get("valid")))
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
        ((Number) row.get("enabled")).intValue() == 1,
        auth.userId())) conflict();
    var messages = read(pack, new ArrayList<>(), new ArrayList<>());
    repo.replace(tenant(), locale, messages);
    repo.event(
        tenant(), locale, "import", auth.userId(), row == null ? 1 : version + 1, messages.size());
    return result;
  }

  @Transactional
  public void state(String locale, int version, boolean enabled) {
    access();
    var row = repo.find(tenant(), locale);
    if (BuiltinMessages.NAMES.containsKey(locale) && !enabled)
      throw new LocalizedArgumentException("language.error.builtin");
    if (row == null
        || !repo.update(
            tenant(),
            locale,
            version,
            String.valueOf(row.get("native_name")),
            enabled,
            auth.userId())) conflict();
    repo.event(tenant(), locale, enabled ? "enable" : "disable", auth.userId(), version + 1, 0);
  }

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
    repo.replace(tenant(), locale, Map.of());
    repo.event(tenant(), locale, "restore", auth.userId(), version + 1, 0);
  }

  private void conflict() {
    throw new LocalizedStatusException(HttpStatus.CONFLICT, "language.error.conflict");
  }
}
