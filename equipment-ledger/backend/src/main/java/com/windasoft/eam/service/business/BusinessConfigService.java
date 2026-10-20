package com.windasoft.eam.service.business;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.windasoft.eam.repository.business.BusinessConfigRepository;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessConfigService {
  public static final String EXTENSION_GROUP = "扩展信息";
  public static final Map<String, String> OBJECTS =
      BusinessObjects.ALL.entrySet().stream()
          .collect(
              java.util.stream.Collectors.toMap(
                  Map.Entry::getKey, e -> e.getValue().name(), (a, c) -> a, LinkedHashMap::new));
  public static final Map<String, String> BASES =
      Map.of(
          "CATEGORY",
          "设备分类",
          "UNIT",
          "单位",
          "USE_STATUS",
          "使用状态",
          "SOURCE",
          "设备来源",
          "FAULT_TYPE",
          "故障类型",
          "FAULT_CAUSE",
          "故障原因",
          "PROCESS_RESULT",
          "处理结果",
          "MATERIAL_CATEGORY",
          "物料分类");
  public static final Set<String> TYPES =
      Set.of(
          "TEXT",
          "LONG_TEXT",
          "NUMBER",
          "DATE",
          "DATETIME",
          "CHOICE",
          "MULTI_CHOICE",
          "BOOLEAN",
          "USER",
          "DEPARTMENT");
  private final BusinessConfigRepository repo;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;

  public BusinessConfigService(
      BusinessConfigRepository repo,
      ObjectMapper json,
      AuthContext auth,
      PermissionService permissions) {
    this.repo = repo;
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
  }

  public void object(String object) {
    if (!OBJECTS.containsKey(object))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0006");
  }

  public String menu(String object) {
    object(object);
    return BusinessObjects.get(object).menu();
  }

  public ObjectNode decode(Object value) {
    try {
      return hydrate((ObjectNode) json.readTree(value.toString()));
    } catch (Exception e) {
      throw new IllegalStateException("字段配置无法读取", e);
    }
  }

  /** Read old dictionary-backed definitions as field-owned options, without rewriting history. */
  private ObjectNode hydrate(ObjectNode config) {
    // Normalize legacy grouping only in the returned configuration; persisted version text stays
    // immutable.
    var fields = new ArrayList<JsonNode>();
    config.path("fields").forEach(fields::add);
    if (fields.stream().anyMatch(f -> !EXTENSION_GROUP.equals(f.path("group").asText()))) {
      var legacyGroups = List.of("基本信息", "报修信息", "技术与补充信息", "补充信息");
      fields.sort(
          Comparator.<JsonNode>comparingInt(
                  f -> {
                    int index = legacyGroups.indexOf(f.path("group").asText());
                    return index < 0 ? legacyGroups.size() : index;
                  })
              .thenComparingInt(f -> f.path("sortOrder").asInt()));
      var normalized = json.createArrayNode();
      for (int i = 0; i < fields.size(); i++) {
        var field = (ObjectNode) fields.get(i);
        field.put("group", EXTENSION_GROUP);
        field.put("sortOrder", i);
        normalized.add(field);
      }
      config.set("fields", normalized);
    }
    var cached = new HashMap<String, ArrayNode>();
    for (var field : config.path("fields")) {
      String source = field.path("source").asText();
      if (!Set.of("CHOICE", "MULTI_CHOICE").contains(field.path("type").asText())
          || field.has("options")
          || !source.startsWith("dictionary:")) continue;
      var options =
          cached.computeIfAbsent(
              source,
              key -> {
                var result = json.createArrayNode();
                var dictionaries = repo.dictionary(auth.tenantId(), key.substring(11), false);
                boolean enabled =
                    !dictionaries.isEmpty()
                        && BusinessReferenceService.enabled(dictionaries.getFirst().get("enabled"));
                for (var option : repo.options(auth.tenantId(), key.substring(11))) {
                  var row = json.createObjectNode();
                  row.put("id", option.get("id").toString());
                  row.put("name", option.get("name").toString());
                  row.put(
                      "enabled",
                      enabled && BusinessReferenceService.enabled(option.get("enabled")));
                  row.put("sortOrder", ((Number) option.get("sortOrder")).intValue());
                  result.add(row);
                }
                return result;
              });
      ((ObjectNode) field).set("options", options.deepCopy());
    }
    return config;
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          com.windasoft.eam.common.MessageRef.of("server.business.message0020"), e);
    }
  }

  public Map<String, JsonNode> fields(JsonNode config) {
    var result = new LinkedHashMap<String, JsonNode>();
    for (var f : config.path("fields")) result.put(f.path("id").asText(), f);
    return result;
  }

  public List<Map<String, Object>> objects() {
    permissions.require("businessConfig", "查看");
    return OBJECTS.entrySet().stream()
        .map(e -> Map.<String, Object>of("key", e.getKey(), "name", e.getValue()))
        .toList();
  }

  public Map<String, Object> configuration(String object) {
    object(object);
    permissions.require("businessConfig", "查看");
    return schemaView(repo.schema(auth.tenantId(), object, false));
  }

  public Map<String, Object> schemaView(Map<String, Object> row) {
    return Map.of(
        "draft",
        decode(row.get("draft")),
        "published",
        decode(row.get("published")),
        "version",
        row.get("row_version"),
        "publishedVersion",
        row.get("published_version"));
  }

  public Map<String, Object> runtime(String object) {
    object(object);
    var row = repo.schema(auth.tenantId(), object, false);
    return Map.of(
        "configuration", decode(row.get("published")), "version", row.get("published_version"));
  }

  public Map<String, Object> current(String object, boolean lock) {
    object(object);
    return repo.schema(auth.tenantId(), object, lock);
  }

  public void event(String object, Long record, String action, Object details) {
    repo.event(
        IdWorker.getId(),
        auth.tenantId(),
        object,
        record,
        auth.userId(),
        auth.user().getDisplayName(),
        action,
        encode(details));
  }

  public void checkVersion(Map<String, Object> row, int version) {
    if (((Number) row.get("row_version")).intValue() != version)
      throw new com.windasoft.eam.common.LocalizedStatusException(
          HttpStatus.CONFLICT, "server.business.message0021");
  }

  @Transactional
  public Map<String, Object> save(String object, JsonNode body) {
    object(object);
    permissions.require("businessConfig", "编辑");
    var row = repo.schema(auth.tenantId(), object, true);
    checkVersion(row, body.path("version").asInt(-1));
    var draft =
        body.path("configuration").isObject()
            ? hydrate(((ObjectNode) body.path("configuration")).deepCopy())
            : body.path("configuration");
    validate(object, draft, decode(row.get("published")));
    repo.draft(auth.tenantId(), object, encode(draft), body.path("version").asInt(), auth.userId());
    event(object, null, "保存字段草稿", draft);
    return configuration(object);
  }

  @Transactional
  public Map<String, Object> publish(String object, int version) {
    object(object);
    permissions.require("businessConfig", "发布");
    var row = repo.schema(auth.tenantId(), object, true);
    checkVersion(row, version);
    var draft = decode(row.get("draft"));
    validate(object, draft, decode(row.get("published")));
    repo.publish(
        auth.tenantId(),
        object,
        encode(draft),
        ((Number) row.get("published_version")).intValue() + 1,
        auth.userId());
    event(object, null, "发布业务字段", draft);
    return schemaView(repo.schema(auth.tenantId(), object, false));
  }

  @Transactional
  public Map<String, Object> restore(String object, int target, int version) {
    object(object);
    permissions.require("businessConfig", "编辑");
    var row = repo.schema(auth.tenantId(), object, true);
    checkVersion(row, version);
    var old = decode(repo.version(auth.tenantId(), object, target));
    // Fields added in later publications remain in history as disabled definitions.
    var restored = fields(old);
    for (var f : fields(decode(row.get("published"))).values())
      if (!restored.containsKey(f.path("id").asText())) {
        var disabled = f.deepCopy();
        ((ObjectNode) disabled).put("enabled", false);
        int columns = old.path("layout").path("columns").asInt(2);
        if (disabled.has("span"))
          ((ObjectNode) disabled)
              .put(
                  "span",
                  disabled.path("width").asText().equals("full")
                      ? columns
                      : Math.min(disabled.path("span").asInt(1), columns));
        ((ArrayNode) old.path("fields")).add(disabled);
      }
    for (var current : fields(decode(row.get("published"))).values()) {
      var targetField = restored.get(current.path("id").asText());
      if (targetField == null
          || !BusinessRules.inlineOptions(current)
          || !BusinessRules.inlineOptions(targetField)) continue;
      var ids = new HashSet<String>();
      targetField.path("options").forEach(o -> ids.add(o.path("id").asText()));
      for (var option : current.path("options"))
        if (!ids.contains(option.path("id").asText())) {
          var disabledOption = (ObjectNode) option.deepCopy();
          disabledOption.put("enabled", false);
          ((ArrayNode) targetField.path("options")).add(disabledOption);
        }
    }
    validate(object, old, decode(row.get("published")));
    repo.draft(auth.tenantId(), object, encode(old), version, auth.userId());
    event(object, null, "恢复配置草稿", Map.of("version", target));
    return configuration(object);
  }

  public List<Map<String, Object>> versions(String object) {
    object(object);
    permissions.require("businessConfig", "查看");
    return repo.versions(auth.tenantId(), object);
  }

  public List<Map<String, Object>> events(String object) {
    object(object);
    permissions.require("businessConfig", "查看");
    return repo.events(auth.tenantId(), object, null);
  }

  private void validate(String object, JsonNode config, JsonNode published) {
    if (!config.isObject()
        || !config.path("fields").isArray()
        || config.path("fields").size() > 100)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0022");
    BusinessRules.validateLayout(config);
    BusinessRules.validateOptions(config, published);
    var fs = fields(config);
    if (fs.size() != config.path("fields").size())
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0023");
    var names = new HashSet<String>();
    for (var f : fs.values()) {
      String id = f.path("id").asText(),
          type = f.path("type").asText(),
          name = f.path("name").asText().trim(),
          source = f.path("source").asText();
      try {
        UUID.fromString(id);
      } catch (Exception e) {
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.business.message0024");
      }
      if (name.isBlank() || name.length() > 100 || !names.add(name) || !TYPES.contains(type))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.business.message0025");
      if (!EXTENSION_GROUP.equals(f.path("group").asText(EXTENSION_GROUP))
          || !Set.of("half", "full").contains(f.path("width").asText("half")))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.business.message0026");
      if (type.equals("USER") && !source.equals("user")
          || type.equals("DEPARTMENT") && !source.equals("department"))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.business.message0027");
      if (type.equals("CHOICE") || type.equals("MULTI_CHOICE")) {
        if (BusinessRules.inlineOptions(f)) {
          if (!source.isBlank() && !source.startsWith("dictionary:"))
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.business.message0028");
        } else if (!source.startsWith("base:") || !BASES.containsKey(source.substring(5)))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0029");
      } else if (!type.equals("USER") && !type.equals("DEPARTMENT") && !source.isBlank())
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.business.message0030");
      if (!Set.of("USER", "DEPARTMENT").contains(type) && f.path("multiple").asBoolean())
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.business.message0031");
      int max = f.path("maxLength").asInt(type.equals("LONG_TEXT") ? 4000 : 200);
      if (max < 1 || max > 20000)
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.business.message0032");
      if (type.equals("NUMBER"))
        for (String bound : List.of("min", "max"))
          if (!f.path(bound).asText().isBlank()) new java.math.BigDecimal(f.path(bound).asText());
      if (type.equals("NUMBER")
          && !f.path("min").asText().isBlank()
          && !f.path("max").asText().isBlank()
          && new java.math.BigDecimal(f.path("min").asText())
                  .compareTo(new java.math.BigDecimal(f.path("max").asText()))
              > 0)
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.business.message0033");
      var identity = repo.identity(id);
      if (identity != null) {
        if (!identity.get("tenant_id").toString().equals(Long.toString(auth.tenantId()))
            || !identity.get("object_key").equals(object))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0034");
        if (((Number) identity.get("ever_published")).intValue() == 1
            && (!identity.get("data_type").equals(type)
                || !identity.get("source_key").equals(source)))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0035");
      }
      repo.identity(id, auth.tenantId(), object, type, source);
    }
    for (var previous : published.path("fields"))
      if (!fs.containsKey(previous.path("id").asText()))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.business.message0036");
    for (var previous : published.path("fields")) {
      var f = fs.get(previous.path("id").asText());
      if (f != null && BusinessRules.multiple(f) != BusinessRules.multiple(previous))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.business.message0037");
    }
    if (!Set.of("equipment", "repair_report", "repair_process").contains(object))
      for (var field : fs.values())
        for (String rule : List.of("visibleWhen", "requiredWhen"))
          for (var condition : field.path(rule).path("items"))
            if (condition.path("field").asText().equals("categoryId"))
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.business.message0038");
    BusinessRules.validate(fs);
  }
}
