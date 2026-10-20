package com.windasoft.eam.service.business;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.windasoft.eam.common.FieldValidationException;
import com.windasoft.eam.repository.business.BusinessConfigRepository;
import com.windasoft.eam.service.system.AuthContext;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BusinessValueService {
  private final BusinessConfigRepository repo;
  private final BusinessConfigService configs;
  private final BusinessReferenceService references;
  private final AuthContext auth;
  private final ObjectMapper json;

  public BusinessValueService(
      BusinessConfigRepository repo,
      BusinessConfigService configs,
      BusinessReferenceService references,
      AuthContext auth,
      ObjectMapper json) {
    this.repo = repo;
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
    int version = ((Number) schema.get("published_version")).intValue();
    var fs = configs.fields(configs.decode(schema.get("published")));
    var grouped = new HashMap<Long, Map<String, List<Map<String, Object>>>>();
    for (var row : repo.values(auth.tenantId(), object, ids))
      grouped
          .computeIfAbsent(((Number) row.get("record_id")).longValue(), x -> new LinkedHashMap<>())
          .computeIfAbsent(row.get("field_id").toString(), x -> new ArrayList<>())
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

  /** Read a whole authorized result set in one query, never one query per row. */
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
    // Internal source-generated stock drafts have no user input yet. Defaults are applied on edit.
    if (isNew && version == null && !body.containsKey("customFields")) return;
    save(object, id, json.valueToTree(body.get("customFields")), version, "", isNew, false);
  }

  public void validateStored(String object, long id) {
    var current = read(object, id);
    save(object, id, json.createObjectNode(), current.customSchemaVersion(), "", false, true);
  }

  public void remove(String object, long id) {
    repo.removeValues(auth.tenantId(), object, id);
  }

  public Map<Long, String> repairCategories(Collection<Long> ids) {
    return repo.repairCategories(auth.tenantId(), ids);
  }

  public Values read(String object, long id) {
    return batch(object, List.of(id)).get(id);
  }

  private Object read(JsonNode f, Map<String, Object> row) {
    return switch (f.path("type").asText()) {
      case "NUMBER" -> Objects.toString(row.get("number_value"), "");
      case "DATE" -> Objects.toString(row.get("date_value"), "");
      case "DATETIME" ->
          row.get("datetime_value") instanceof java.sql.Timestamp t
              ? t.toLocalDateTime()
                  .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
              : Objects.toString(row.get("datetime_value"), "").replace(' ', 'T');
      case "BOOLEAN" -> BusinessReferenceService.enabled(row.get("bool_value"));
      case "CHOICE", "MULTI_CHOICE", "USER", "DEPARTMENT" ->
          Objects.toString(row.get("reference_id"), "");
      default -> Objects.toString(row.get("text_value"), "");
    };
  }

  private String display(JsonNode f, Map<String, Object> row) {
    if (BusinessRules.reference(f)) return Objects.toString(row.get("reference_name"), "");
    if (f.path("type").asText().equals("BOOLEAN"))
      return BusinessReferenceService.enabled(row.get("bool_value")) ? "是" : "否";
    if (f.path("type").asText().equals("NUMBER"))
      return new BigDecimal(row.get("number_value").toString())
          .setScale(2, java.math.RoundingMode.HALF_UP)
          .toPlainString();
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

  public void save(
      String object,
      long record,
      JsonNode submitted,
      Integer version,
      String category,
      boolean isNew,
      boolean complete) {
    var schema = configs.current(object, true);
    int current = ((Number) schema.get("published_version")).intValue();
    var fs = configs.fields(configs.decode(schema.get("published")));
    if (current != 0 && (version == null || version != current))
      throw new com.windasoft.eam.common.LocalizedStatusException(
          HttpStatus.CONFLICT, "server.business.message0047");
    if (submitted == null || submitted.isNull()) submitted = json.createObjectNode();
    if (!submitted.isObject())
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0048");
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
                    "customFields." + e.getKey(),
                    com.windasoft.eam.common.MessageRef.of("server.business.message0049"));
              merged.set(e.getKey(), e.getValue());
            });
    var previousRows = repo.values(auth.tenantId(), object, List.of(record));
    var changes = new LinkedHashMap<String, Object>();
    for (var e : fs.entrySet()) {
      String id = e.getKey();
      var f = e.getValue();
      boolean visible = BusinessRules.visible(f, fs, merged, category);
      JsonNode value = merged.path(id), before = json.valueToTree(old.customFields().get(id));
      if (!visible) {
        if (submitted.has(id) && !defaulted.contains(id) && !same(value, before))
          throw new FieldValidationException(
              "customFields." + id,
              com.windasoft.eam.common.MessageRef.of("server.business.message0050"));
        continue;
      }
      if (complete && BusinessRules.required(f, fs, merged, category) && BusinessRules.empty(value))
        throw new FieldValidationException(
            "customFields." + id,
            com.windasoft.eam.common.MessageRef.of(
                "server.business.message0051", f.path("name").asText()));
      if (!submitted.has(id)) continue;
      var rows =
          normalize(
              object,
              f,
              value,
              previousRows.stream().filter(r -> id.equals(r.get("field_id"))).toList());
      repo.replace(auth.tenantId(), object, record, id, rows);
      if (!same(value, before))
        changes.put(id, Map.of("name", f.path("name").asText(), "before", before, "after", value));
    }
    if (!changes.isEmpty()) configs.event(object, record, "修改扩展字段", changes);
  }

  private boolean same(JsonNode a, JsonNode b) {
    return BusinessRules.empty(a) && BusinessRules.empty(b) || a.equals(b);
  }

  public List<Map<String, Object>> normalize(
      String object, JsonNode f, JsonNode value, List<Map<String, Object>> previous) {
    return normalizeWithNames(object, f, value, previous, null);
  }

  private List<Map<String, Object>> normalizeWithNames(
      String object,
      JsonNode f,
      JsonNode value,
      List<Map<String, Object>> previous,
      Map<String, String> referenceNames) {
    if (BusinessRules.empty(value)) return List.of();
    String field = "customFields." + f.path("id").asText();
    try {
      var rows = new ArrayList<Map<String, Object>>();
      var values = new ArrayList<JsonNode>();
      if (BusinessRules.multiple(f)) {
        if (!value.isArray() || value.size() > 200)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0052");
        value.forEach(values::add);
      } else {
        if (value.isContainerNode())
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0053");
        values.add(value);
      }
      var seen = new HashSet<String>();
      for (var v : values) {
        if (!seen.add(v.asText()))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0054");
        var row = new HashMap<String, Object>();
        switch (f.path("type").asText()) {
          case "TEXT", "LONG_TEXT" -> {
            if (!v.isTextual())
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.business.message0055");
            String text = v.asText();
            if (text.length()
                > f.path("maxLength")
                    .asInt(f.path("type").asText().equals("LONG_TEXT") ? 4000 : 200))
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.business.message0056");
            row.put("text_value", text);
          }
          case "NUMBER" -> {
            var n = new BigDecimal(v.asText());
            if (n.scale() > 6 || n.precision() - (long) n.scale() > 18)
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.business.message0057");
            for (String bound : List.of("min", "max")) {
              String limit = f.path(bound).asText();
              if (!limit.isBlank()
                  && (bound.equals("min")
                      ? n.compareTo(new BigDecimal(limit)) < 0
                      : n.compareTo(new BigDecimal(limit)) > 0))
                throw new IllegalArgumentException(bound.equals("min") ? "数值小于下限" : "数值大于上限");
            }
            row.put("number_value", n);
          }
          case "DATE" -> row.put("date_value", LocalDate.parse(v.asText()));
          case "DATETIME" ->
              row.put("datetime_value", LocalDateTime.parse(v.asText().replace(' ', 'T')));
          case "BOOLEAN" -> {
            if (!v.isBoolean())
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.business.message0058");
            row.put("bool_value", v.asBoolean() ? 1 : 0);
          }
          default -> {
            String id = v.asText();
            if (id.isBlank() || id.length() > 64)
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.business.message0044");
            var retained =
                previous.stream().filter(r -> id.equals(r.get("reference_id"))).findFirst();
            String name;
            if (retained.isPresent()) name = retained.get().get("reference_name").toString();
            else if (referenceNames != null) {
              name = referenceNames.get(id);
              if (name == null)
                throw new com.windasoft.eam.common.LocalizedArgumentException(
                    "server.business.message0059");
            } else name = references.resolve(object, f, id);
            row.put("reference_id", id);
            row.put("reference_name", name);
          }
        }
        rows.add(row);
      }
      return rows;
    } catch (Exception e) {
      throw new FieldValidationException(
          field,
          e instanceof java.time.format.DateTimeParseException
              ? "请选择有效日期或时间"
              : e instanceof NumberFormatException
                  ? "请输入有效数值"
                  : e.getMessage() == null ? "字段值格式无效" : e.getMessage());
    }
  }

  /** Pure new-record validation for imports; rules and value normalization are shared with save. */
  public Map<String, List<Map<String, Object>>> prepareNew(
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
    var prepared = new LinkedHashMap<String, List<Map<String, Object>>>();
    for (var entry : fs.entrySet()) {
      var f = entry.getValue();
      String id = entry.getKey();
      var value = values.path(id);
      if (!BusinessRules.visible(f, fs, values, category)) {
        if (!BusinessRules.empty(value) && !defaulted.contains(id))
          errors.put(
              id, com.windasoft.eam.common.MessageContext.text("server.business.message0080"));
        continue;
      }
      if (BusinessRules.required(f, fs, values, category) && BusinessRules.empty(value)) {
        errors.put(
            id,
            com.windasoft.eam.common.MessageContext.text(
                "server.business.message0051", f.path("name").asText()));
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
      String object, long id, Map<String, List<Map<String, Object>>> prepared) {
    for (var entry : prepared.entrySet())
      if (!entry.getValue().isEmpty())
        repo.replace(auth.tenantId(), object, id, entry.getKey(), entry.getValue());
    if (prepared.values().stream().anyMatch(v -> !v.isEmpty()))
      configs.event(object, id, "导入扩展字段", Map.of("fields", prepared.keySet()));
  }

  public void defaults(String object, JsonNode configuration) {
    for (var f : configuration.path("fields"))
      if (f.path("enabled").asBoolean(true) && !BusinessRules.empty(f.get("defaultValue")))
        normalize(object, f, f.get("defaultValue"), List.of());
  }
}
