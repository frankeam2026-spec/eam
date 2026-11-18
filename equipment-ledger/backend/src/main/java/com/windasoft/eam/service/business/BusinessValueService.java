package com.windasoft.eam.service.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.windasoft.eam.common.FieldValidationException;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.common.MessageRef;
import com.windasoft.eam.dto.business.BusinessFieldValue;
import com.windasoft.eam.entity.business.BusinessValue;
import com.windasoft.eam.repository.business.BusinessConfigRepository;
import com.windasoft.eam.repository.business.BusinessValueRepository;
import com.windasoft.eam.service.system.AuthContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 扩展字段按已发布配置校验和规范化；记录权限与完整事务沿用所属业务调用方。存储行固定建模，对外字段ID字典保持动态。 */
@Service
public class BusinessValueService {
  private static final int MAX_MULTIPLE_VALUES = 200;
  private final BusinessConfigRepository repo;
  private final BusinessValueRepository storage;
  private final BusinessConfigService configs;
  private final BusinessReferenceService references;
  private final AuthContext auth;
  private final ObjectMapper json;

  public BusinessValueService(
      BusinessConfigRepository repo,
      BusinessValueRepository storage,
      BusinessConfigService configs,
      BusinessReferenceService references,
      AuthContext auth,
      ObjectMapper json) {
    this.repo = repo;
    this.storage = storage;
    this.configs = configs;
    this.references = references;
    this.auth = auth;
    this.json = json;
  }

  public record Values(
      Map<String, Object> customFields,
      Map<String, String> customFieldLabels,
      int customSchemaVersion) {}

  public Map<Long, Values> batch(String object, Collection<Long> ids) {
    var schema = configs.current(object, false);
    int version = schema.getPublishedVersion();
    var fs = configs.fields(configs.decode(schema.getPublished()));
    var grouped = new HashMap<Long, Map<String, List<BusinessValue>>>();
    for (var row : storage.values(auth.tenantId(), object, ids))
      grouped
          .computeIfAbsent(row.getRecordId(), x -> new LinkedHashMap<>())
          .computeIfAbsent(row.getFieldId(), x -> new ArrayList<>())
          .add(row);
    var result = new HashMap<Long, Values>();
    for (long id : ids) {
      var values = new LinkedHashMap<String, Object>();
      var labels = new LinkedHashMap<String, String>();
      for (var entry : grouped.getOrDefault(id, Map.of()).entrySet()) {
        var f = fs.get(entry.getKey());
        if (f == null) continue;
        var rows = entry.getValue();
        var vals = rows.stream().map(r -> read(f, r)).toList();
        values.put(entry.getKey(), BusinessRules.multiple(f) ? vals : vals.getFirst());
        labels.put(
            entry.getKey(), String.join("、", rows.stream().map(r -> display(f, r)).toList()));
      }
      result.put(id, new Values(values, labels, version));
    }
    return result;
  }

  public void attach(String object, Map<String, Object> row) {
    attach(object, List.of(row));
  }

  /** 授权后的业务结果集一次附加扩展字段；这里不扩大所属业务已经限定的记录范围。 */
  public void attach(String object, List<Map<String, Object>> rows) {
    if (object == null || rows.isEmpty()) return;
    var ids = rows.stream().map(r -> Long.parseLong(r.get("id").toString())).toList();
    var extra = batch(object, ids);
    for (var row : rows) {
      var value = extra.get(Long.parseLong(row.get("id").toString()));
      row.put("customFields", value.customFields());
      row.put("customFieldLabels", value.customFieldLabels());
      row.put("customSchemaVersion", value.customSchemaVersion());
    }
  }

  public void save(String object, long id, Map<String, Object> body, boolean isNew) {
    save(
        object,
        id,
        json.valueToTree(body.get("customFields")),
        body.get("customSchemaVersion") instanceof Number n ? n.intValue() : null,
        "",
        isNew);
  }

  public void saveDraft(String object, long id, Map<String, Object> body) {
    saveDraft(object, id, body, false);
  }

  public void saveDraft(String object, long id, Map<String, Object> body, boolean isNew) {
    var version = body.get("customSchemaVersion") instanceof Number n ? n.intValue() : null;
    // 系统生成的库存草稿尚无用户输入，沿用后续编辑时补默认值的原规则。
    if (isNew && version == null && !body.containsKey("customFields")) return;
    save(object, id, json.valueToTree(body.get("customFields")), version, "", isNew, false);
  }

  public void validateStored(String object, long id) {
    var current = read(object, id);
    save(object, id, json.createObjectNode(), current.customSchemaVersion(), "", false, true);
  }

  public void remove(String object, long id) {
    storage.remove(auth.tenantId(), object, id);
  }

  public Map<Long, String> repairCategories(Collection<Long> ids) {
    return repo.repairCategories(auth.tenantId(), ids);
  }

  public Values read(String object, long id) {
    return batch(object, List.of(id)).get(id);
  }

  private Object read(JsonNode f, BusinessValue row) {
    return switch (f.path("type").asText()) {
      case "NUMBER" -> Objects.toString(row.getNumberValue(), "");
      case "DATE" -> Objects.toString(row.getDateValue(), "");
      case "DATETIME" -> Objects.toString(row.getDatetimeValue(), "");
      case "BOOLEAN" -> BusinessReferenceService.enabled(row.getBoolValue());
      case "CHOICE", "MULTI_CHOICE", "USER", "DEPARTMENT" ->
          Objects.toString(row.getReferenceId(), "");
      default -> Objects.toString(row.getTextValue(), "");
    };
  }

  private String display(JsonNode f, BusinessValue row) {
    if (BusinessRules.reference(f)) return Objects.toString(row.getReferenceName(), "");
    if (f.path("type").asText().equals("BOOLEAN"))
      return BusinessReferenceService.enabled(row.getBoolValue()) ? "是" : "否";
    if (f.path("type").asText().equals("NUMBER"))
      return row.getNumberValue().setScale(2, RoundingMode.HALF_UP).toPlainString();
    return read(f, row).toString().replace('T', ' ');
  }

  public void save(
      String object,
      long record,
      JsonNode submitted,
      Integer version,
      String category,
      boolean isNew) {
    save(object, record, submitted, version, category, isNew, true);
  }

  /** 在所属业务事务内锁定配置版本，合并原值后校验可见性与必填，再逐字段替换并记录审计。 隐藏字段及未提交字段保留原值；已有引用沿用快照名称，不能强制重新解析已停用的历史人员/选项。 */
  public void save(
      String object,
      long record,
      JsonNode submitted,
      Integer version,
      String category,
      boolean isNew,
      boolean complete) {
    var schema = configs.current(object, true);
    int current = schema.getPublishedVersion();
    var fs = configs.fields(configs.decode(schema.getPublished()));
    if (current != 0 && (version == null || version != current))
      throw new LocalizedStatusException(HttpStatus.CONFLICT, "server.business.message0047");
    if (submitted == null || submitted.isNull()) submitted = json.createObjectNode();
    if (!submitted.isObject()) throw new LocalizedArgumentException("server.business.message0048");
    var input = ((ObjectNode) submitted).deepCopy();
    var defaulted = new HashSet<String>();
    if (isNew)
      for (var f : fs.values())
        if (f.path("enabled").asBoolean(true)
            && !input.has(f.path("id").asText())
            && !BusinessRules.empty(f.get("defaultValue"))) {
          input.set(f.path("id").asText(), f.get("defaultValue"));
          defaulted.add(f.path("id").asText());
        }
    submitted = input;
    var old = read(object, record);
    ObjectNode merged = json.valueToTree(old.customFields());
    submitted
        .fields()
        .forEachRemaining(
            e -> {
              if (!fs.containsKey(e.getKey()))
                throw new FieldValidationException(
                    "customFields." + e.getKey(), MessageRef.of("server.business.message0049"));
              merged.set(e.getKey(), e.getValue());
            });
    var previousRows = storage.values(auth.tenantId(), object, List.of(record));
    var changes = new LinkedHashMap<String, Object>();
    for (var e : fs.entrySet()) {
      String id = e.getKey();
      var f = e.getValue();
      boolean visible = BusinessRules.visible(f, fs, merged, category);
      JsonNode value = merged.path(id), before = json.valueToTree(old.customFields().get(id));
      if (!visible) {
        if (submitted.has(id) && !defaulted.contains(id) && !same(value, before))
          throw new FieldValidationException(
              "customFields." + id, MessageRef.of("server.business.message0050"));
        continue;
      }
      if (complete && BusinessRules.required(f, fs, merged, category) && BusinessRules.empty(value))
        throw new FieldValidationException(
            "customFields." + id,
            MessageRef.of("server.business.message0051", f.path("name").asText()));
      if (!submitted.has(id)) continue;
      var rows =
          normalize(
              object,
              f,
              value,
              previousRows.stream().filter(r -> id.equals(r.getFieldId())).toList());
      storage.replace(auth.tenantId(), object, record, id, rows);
      if (!same(value, before))
        changes.put(id, Map.of("name", f.path("name").asText(), "before", before, "after", value));
    }
    if (!changes.isEmpty()) configs.event(object, record, "修改扩展字段", changes);
  }

  private boolean same(JsonNode a, JsonNode b) {
    return BusinessRules.empty(a) && BusinessRules.empty(b) || a.equals(b);
  }

  public List<BusinessFieldValue> normalize(
      String object, JsonNode f, JsonNode value, List<BusinessValue> previous) {
    return normalizeWithNames(object, f, value, previous, null);
  }

  private List<BusinessFieldValue> normalizeWithNames(
      String object,
      JsonNode f,
      JsonNode value,
      List<BusinessValue> previous,
      Map<String, String> referenceNames) {
    if (BusinessRules.empty(value)) return List.of();
    String field = "customFields." + f.path("id").asText();
    try {
      var rows = new ArrayList<BusinessFieldValue>();
      var values = new ArrayList<JsonNode>();
      if (BusinessRules.multiple(f)) {
        if (!value.isArray() || value.size() > MAX_MULTIPLE_VALUES)
          throw new LocalizedArgumentException("server.business.message0052");
        value.forEach(values::add);
      } else {
        if (value.isContainerNode())
          throw new LocalizedArgumentException("server.business.message0053");
        values.add(value);
      }
      var seen = new HashSet<String>();
      for (var v : values) {
        if (!seen.add(v.asText()))
          throw new LocalizedArgumentException("server.business.message0054");
        String textValue = null, referenceId = null, referenceName = null;
        BigDecimal numberValue = null;
        LocalDate dateValue = null;
        LocalDateTime datetimeValue = null;
        Integer boolValue = null;
        switch (f.path("type").asText()) {
          case "TEXT", "LONG_TEXT" -> {
            if (!v.isTextual()) throw new LocalizedArgumentException("server.business.message0055");
            String text = v.asText();
            if (text.length()
                > f.path("maxLength")
                    .asInt(f.path("type").asText().equals("LONG_TEXT") ? 4000 : 200))
              throw new LocalizedArgumentException("server.business.message0056");
            textValue = text;
          }
          case "NUMBER" -> {
            var n = new BigDecimal(v.asText());
            // 与既有DECIMAL(24,6)列一致：最多6位小数、18位整数，不在规范化时提前舍入。
            if (n.scale() > 6 || n.precision() - (long) n.scale() > 18)
              throw new LocalizedArgumentException("server.business.message0057");
            for (String bound : List.of("min", "max")) {
              String limit = f.path(bound).asText();
              if (!limit.isBlank()
                  && (bound.equals("min")
                      ? n.compareTo(new BigDecimal(limit)) < 0
                      : n.compareTo(new BigDecimal(limit)) > 0))
                throw new IllegalArgumentException(bound.equals("min") ? "数值小于下限" : "数值大于上限");
            }
            numberValue = n;
          }
          case "DATE" -> dateValue = LocalDate.parse(v.asText());
          case "DATETIME" -> datetimeValue = LocalDateTime.parse(v.asText().replace(' ', 'T'));
          case "BOOLEAN" -> {
            if (!v.isBoolean()) throw new LocalizedArgumentException("server.business.message0058");
            boolValue = v.asBoolean() ? 1 : 0;
          }
          default -> {
            String id = v.asText();
            if (id.isBlank() || id.length() > 64)
              throw new LocalizedArgumentException("server.business.message0044");
            var retained = previous.stream().filter(r -> id.equals(r.getReferenceId())).findFirst();
            String name;
            if (retained.isPresent()) name = retained.get().getReferenceName().toString();
            else if (referenceNames != null) {
              name = referenceNames.get(id);
              if (name == null) throw new LocalizedArgumentException("server.business.message0059");
            } else name = references.resolve(object, f, id);
            referenceId = id;
            referenceName = name;
          }
        }
        rows.add(
            new BusinessFieldValue(
                textValue,
                numberValue,
                dateValue,
                datetimeValue,
                boolValue,
                referenceId,
                referenceName));
      }
      return rows;
    } catch (Exception e) {
      throw new FieldValidationException(
          field,
          e instanceof DateTimeParseException
              ? "请选择有效日期或时间"
              : e instanceof NumberFormatException
                  ? "请输入有效数值"
                  : e.getMessage() == null ? "字段值格式无效" : e.getMessage());
    }
  }

  /** 导入先共享新记录的默认值、可见性及类型校验；只返回规范化值，不在校验阶段写入数据库。 */
  public Map<String, List<BusinessFieldValue>> prepareNew(
      String object,
      JsonNode configuration,
      JsonNode submitted,
      String category,
      Map<String, Map<String, String>> names,
      Map<String, String> errors) {
    var fs = configs.fields(configuration);
    ObjectNode values =
        submitted != null && submitted.isObject()
            ? ((ObjectNode) submitted).deepCopy()
            : json.createObjectNode();
    var defaulted = new HashSet<String>();
    for (var f : fs.values())
      if (f.path("enabled").asBoolean(true)
          && !values.has(f.path("id").asText())
          && !BusinessRules.empty(f.get("defaultValue"))) {
        values.set(f.path("id").asText(), f.get("defaultValue"));
        defaulted.add(f.path("id").asText());
      }
    var prepared = new LinkedHashMap<String, List<BusinessFieldValue>>();
    for (var entry : fs.entrySet()) {
      var f = entry.getValue();
      String id = entry.getKey();
      var value = values.path(id);
      if (!BusinessRules.visible(f, fs, values, category)) {
        if (!BusinessRules.empty(value) && !defaulted.contains(id))
          errors.put(id, MessageContext.text("server.business.message0080"));
        continue;
      }
      if (BusinessRules.required(f, fs, values, category) && BusinessRules.empty(value)) {
        errors.put(id, MessageContext.text("server.business.message0051", f.path("name").asText()));
        continue;
      }
      try {
        prepared.put(
            id,
            normalizeWithNames(
                object,
                f,
                value,
                List.of(),
                BusinessRules.reference(f) ? names.getOrDefault(id, Map.of()) : null));
      } catch (FieldValidationException e) {
        errors.put(id, e.getMessage());
      }
    }
    return prepared;
  }

  public void savePreparedNew(
      String object, long id, Map<String, List<BusinessFieldValue>> prepared) {
    for (var entry : prepared.entrySet())
      if (!entry.getValue().isEmpty())
        storage.replace(auth.tenantId(), object, id, entry.getKey(), entry.getValue());
    if (prepared.values().stream().anyMatch(v -> !v.isEmpty()))
      configs.event(object, id, "导入扩展字段", Map.of("fields", prepared.keySet()));
  }

  public void defaults(String object, JsonNode configuration) {
    for (var f : configuration.path("fields"))
      if (f.path("enabled").asBoolean(true) && !BusinessRules.empty(f.get("defaultValue")))
        normalize(object, f, f.get("defaultValue"), List.of());
  }
}
