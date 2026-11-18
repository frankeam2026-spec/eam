package com.windasoft.eam.service.business;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageRef;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.EventView;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.ObjectOption;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.RuntimeView;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.SaveDraftRequest;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.SchemaView;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.VersionView;
import com.windasoft.eam.entity.business.BusinessConfigEvent;
import com.windasoft.eam.entity.business.BusinessSchema;
import com.windasoft.eam.entity.business.BusinessSchemaVersion;
import com.windasoft.eam.repository.business.BusinessConfigEventRepository;
import com.windasoft.eam.repository.business.BusinessDictionaryRepository;
import com.windasoft.eam.repository.business.BusinessFieldRepository;
import com.windasoft.eam.repository.business.BusinessOptionRepository;
import com.windasoft.eam.repository.business.BusinessSchemaRepository;
import com.windasoft.eam.repository.business.BusinessSchemaVersionRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessConfigService {
  public static final String EXTENSION_GROUP = "扩展信息";
  public static final Map<String, String> OBJECTS =
      BusinessObjects.ALL.entrySet().stream()
          .collect(
              Collectors.toMap(
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
  private final BusinessDictionaryRepository dictionaries;
  private final BusinessOptionRepository dictionaryOptions;
  private final BusinessSchemaRepository schemas;
  private final BusinessSchemaVersionRepository versions;
  private final BusinessFieldRepository identities;
  private final BusinessConfigEventRepository events;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;

  public BusinessConfigService(
      BusinessDictionaryRepository dictionaries,
      BusinessOptionRepository dictionaryOptions,
      BusinessSchemaRepository schemas,
      BusinessSchemaVersionRepository versions,
      BusinessFieldRepository identities,
      BusinessConfigEventRepository events,
      ObjectMapper json,
      AuthContext auth,
      PermissionService permissions) {
    this.dictionaries = dictionaries;
    this.dictionaryOptions = dictionaryOptions;
    this.schemas = schemas;
    this.versions = versions;
    this.identities = identities;
    this.events = events;
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
  }

  public void object(String object) {
    if (!OBJECTS.containsKey(object))
      throw new LocalizedArgumentException("server.business.message0006");
  }

  public String menu(String object) {
    object(object);
    return BusinessObjects.get(object).menu();
  }

  public ObjectNode decode(String value) {
    try {
      return hydrate((ObjectNode) json.readTree(value));
    } catch (Exception e) {
      throw new IllegalStateException("字段配置无法读取", e);
    }
  }

  /** 返回配置时将旧字典选项还原为字段自带选项；不重写历史发布版本，已停用选项仍保留显示身份。 */
  private ObjectNode hydrate(ObjectNode config) {
    // 分组归一仅影响返回副本，历史版本文本保持不可变。
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
                var dictionary = dictionaries.find(auth.tenantId(), key.substring(11));
                boolean enabled =
                    dictionary != null && BusinessReferenceService.enabled(dictionary.getEnabled());
                for (var option : dictionaryOptions.options(auth.tenantId(), key.substring(11))) {
                  var row = json.createObjectNode();
                  row.put("id", option.getId());
                  row.put("name", option.getName());
                  row.put(
                      "enabled", enabled && BusinessReferenceService.enabled(option.getEnabled()));
                  row.put("sortOrder", option.getSortNo());
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
      throw new LocalizedArgumentException(MessageRef.of("server.business.message0020"), e);
    }
  }

  public Map<String, JsonNode> fields(JsonNode config) {
    var result = new LinkedHashMap<String, JsonNode>();
    for (var f : config.path("fields")) result.put(f.path("id").asText(), f);
    return result;
  }

  public List<ObjectOption> objects() {
    permissions.require("businessConfig", "查看");
    return OBJECTS.entrySet().stream()
        .map(e -> new ObjectOption(e.getKey(), e.getValue()))
        .toList();
  }

  public SchemaView configuration(String object) {
    object(object);
    permissions.require("businessConfig", "查看");
    return schemaView(schemas.find(auth.tenantId(), object, false));
  }

  private SchemaView schemaView(BusinessSchema row) {
    return new SchemaView(
        decode(row.getDraft()),
        decode(row.getPublished()),
        row.getRowVersion(),
        row.getPublishedVersion());
  }

  public RuntimeView runtime(String object) {
    object(object);
    var row = schemas.find(auth.tenantId(), object, false);
    return new RuntimeView(decode(row.getPublished()), row.getPublishedVersion());
  }

  public BusinessSchema current(String object, boolean lock) {
    object(object);
    return schemas.find(auth.tenantId(), object, lock);
  }

  public void event(String object, Long record, String action, Object details) {
    var event = new BusinessConfigEvent();
    event.setId(IdWorker.getId());
    event.setTenantId(auth.tenantId());
    event.setObjectKey(object);
    event.setRecordId(record);
    event.setActorId(auth.userId());
    event.setActorName(auth.user().getDisplayName());
    event.setAction(action);
    event.setDetails(encode(details));
    events.insert(event);
  }

  private void checkVersion(BusinessSchema row, int version) {
    if (row.getRowVersion() != version)
      throw new LocalizedStatusException(HttpStatus.CONFLICT, "server.business.message0021");
  }

  @Transactional
  public SchemaView save(String object, SaveDraftRequest body) {
    object(object);
    permissions.require("businessConfig", "编辑");
    var row = schemas.find(auth.tenantId(), object, true);
    checkVersion(row, body.version());
    var draft =
        body.configuration().isObject()
            ? hydrate(body.configuration().deepCopy())
            : body.configuration();
    validate(object, draft, decode(row.getPublished()));
    schemas.draft(auth.tenantId(), object, encode(draft), body.version(), auth.userId());
    event(object, null, "保存字段草稿", draft);
    return configuration(object);
  }

  /** 取得当前配置行锁后发布；版本归档、当前配置、字段标记和审计均共享原事务及原写入顺序。 */
  @Transactional
  public SchemaView publish(String object, int version) {
    object(object);
    permissions.require("businessConfig", "发布");
    var row = schemas.find(auth.tenantId(), object, true);
    checkVersion(row, version);
    var draft = decode(row.getDraft());
    validate(object, draft, decode(row.getPublished()));
    int nextVersion = row.getPublishedVersion() + 1;
    var archived = new BusinessSchemaVersion();
    archived.setTenantId(auth.tenantId());
    archived.setObjectKey(object);
    archived.setVersion(nextVersion);
    archived.setConfiguration(encode(draft));
    archived.setActorId(auth.userId());
    // 当前配置行锁已取得；归档、当前版本和已发布字段标记沿用原写入顺序，失败必须整体回滚。
    versions.insert(archived);
    schemas.published(
        auth.tenantId(), object, archived.getConfiguration(), nextVersion, auth.userId());
    identities.markPublished(auth.tenantId(), object);
    event(object, null, "发布业务字段", draft);
    return schemaView(schemas.find(auth.tenantId(), object, false));
  }

  @Transactional
  public SchemaView restore(String object, int target, int version) {
    object(object);
    permissions.require("businessConfig", "编辑");
    var row = schemas.find(auth.tenantId(), object, true);
    checkVersion(row, version);
    var old = decode(versions.configuration(auth.tenantId(), object, target));
    // 较晚发布的字段不能从历史身份中消失；恢复旧版时保留为停用定义。
    var restored = fields(old);
    for (var f : fields(decode(row.getPublished())).values())
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
    for (var current : fields(decode(row.getPublished())).values()) {
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
    validate(object, old, decode(row.getPublished()));
    schemas.draft(auth.tenantId(), object, encode(old), version, auth.userId());
    event(object, null, "恢复配置草稿", Map.of("version", target));
    return configuration(object);
  }

  public List<VersionView> versions(String object) {
    object(object);
    permissions.require("businessConfig", "查看");
    return versions.versions(auth.tenantId(), object);
  }

  public List<EventView> events(String object) {
    object(object);
    permissions.require("businessConfig", "查看");
    return events.events(auth.tenantId(), object, null);
  }

  private void validate(String object, JsonNode config, JsonNode published) {
    if (!config.isObject()
        || !config.path("fields").isArray()
        || config.path("fields").size() > 100)
      throw new LocalizedArgumentException("server.business.message0022");
    BusinessRules.validateLayout(config);
    BusinessRules.validateOptions(config, published);
    var fs = fields(config);
    if (fs.size() != config.path("fields").size())
      throw new LocalizedArgumentException("server.business.message0023");
    var names = new HashSet<String>();
    for (var f : fs.values()) {
      String id = f.path("id").asText(),
          type = f.path("type").asText(),
          name = f.path("name").asText().trim(),
          source = f.path("source").asText();
      try {
        UUID.fromString(id);
      } catch (Exception e) {
        throw new LocalizedArgumentException("server.business.message0024");
      }
      if (name.isBlank() || name.length() > 100 || !names.add(name) || !TYPES.contains(type))
        throw new LocalizedArgumentException("server.business.message0025");
      if (!EXTENSION_GROUP.equals(f.path("group").asText(EXTENSION_GROUP))
          || !Set.of("half", "full").contains(f.path("width").asText("half")))
        throw new LocalizedArgumentException("server.business.message0026");
      if (type.equals("USER") && !source.equals("user")
          || type.equals("DEPARTMENT") && !source.equals("department"))
        throw new LocalizedArgumentException("server.business.message0027");
      if (type.equals("CHOICE") || type.equals("MULTI_CHOICE")) {
        if (BusinessRules.inlineOptions(f)) {
          if (!source.isBlank() && !source.startsWith("dictionary:"))
            throw new LocalizedArgumentException("server.business.message0028");
        } else if (!source.startsWith("base:") || !BASES.containsKey(source.substring(5)))
          throw new LocalizedArgumentException("server.business.message0029");
      } else if (!type.equals("USER") && !type.equals("DEPARTMENT") && !source.isBlank())
        throw new LocalizedArgumentException("server.business.message0030");
      if (!Set.of("USER", "DEPARTMENT").contains(type) && f.path("multiple").asBoolean())
        throw new LocalizedArgumentException("server.business.message0031");
      int max = f.path("maxLength").asInt(type.equals("LONG_TEXT") ? 4000 : 200);
      if (max < 1 || max > 20000)
        throw new LocalizedArgumentException("server.business.message0032");
      if (type.equals("NUMBER"))
        for (String bound : List.of("min", "max"))
          if (!f.path(bound).asText().isBlank()) new BigDecimal(f.path(bound).asText());
      if (type.equals("NUMBER")
          && !f.path("min").asText().isBlank()
          && !f.path("max").asText().isBlank()
          && new BigDecimal(f.path("min").asText())
                  .compareTo(new BigDecimal(f.path("max").asText()))
              > 0) throw new LocalizedArgumentException("server.business.message0033");
      var identity = identities.identity(id);
      if (identity != null) {
        if (!identity.getTenantId().toString().equals(Long.toString(auth.tenantId()))
            || !identity.getObjectKey().equals(object))
          throw new LocalizedArgumentException("server.business.message0034");
        if (identity.getEverPublished() == 1
            && (!identity.getDataType().equals(type) || !identity.getSourceKey().equals(source)))
          throw new LocalizedArgumentException("server.business.message0035");
      }
      identities.definition(id, auth.tenantId(), object, type, source);
    }
    for (var previous : published.path("fields"))
      if (!fs.containsKey(previous.path("id").asText()))
        throw new LocalizedArgumentException("server.business.message0036");
    for (var previous : published.path("fields")) {
      var f = fs.get(previous.path("id").asText());
      if (f != null && BusinessRules.multiple(f) != BusinessRules.multiple(previous))
        throw new LocalizedArgumentException("server.business.message0037");
    }
    if (!Set.of("equipment", "repair_report", "repair_process").contains(object))
      for (var field : fs.values())
        for (String rule : List.of("visibleWhen", "requiredWhen"))
          for (var condition : field.path(rule).path("items"))
            if (condition.path("field").asText().equals("categoryId"))
              throw new LocalizedArgumentException("server.business.message0038");
    BusinessRules.validate(fs);
  }
}
