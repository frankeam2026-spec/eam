package com.windasoft.eam.service.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.dto.business.BusinessFilterTerm;
import com.windasoft.eam.dto.business.BusinessFilterTerm.Operator;
import com.windasoft.eam.repository.business.*;
import com.windasoft.eam.repository.common.SqlPredicate;
import com.windasoft.eam.service.system.AuthContext;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class BusinessQueryService {
  private final BusinessConfigService config;
  private final BusinessConfigRepository repo;
  private final BusinessQueryRepository queries;
  private final BusinessReferenceService references;
  private final AuthContext auth;
  private final ObjectMapper json;

  public BusinessQueryService(
      BusinessConfigService config,
      BusinessConfigRepository repo,
      BusinessReferenceService references,
      AuthContext auth,
      ObjectMapper json,
      BusinessQueryRepository queries) {
    this.config = config;
    this.repo = repo;
    this.references = references;
    this.auth = auth;
    this.json = json;
    this.queries = queries;
  }

  private JsonNode conditions(String raw) {
    if (raw == null || raw.isBlank()) return json.createArrayNode();
    if (raw.length() > 256000)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0007");
    try {
      var value = json.readTree(raw);
      if (!value.isArray() || value.size() > 150) throw new IllegalArgumentException();
      return value;
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0014");
    }
  }

  public String standard(String raw) {
    var result = json.createArrayNode();
    for (var c : conditions(raw))
      if (!c.path("field").asText().startsWith("custom:")) result.add(c);
    return result.toString();
  }

  public Map<String, JsonNode> fields(String object) {
    BusinessObjects.get(object);
    return config.fields(config.decode(config.current(object, false).get("published")));
  }

  private List<BusinessFilterTerm> terms(String object, String raw) {
    var conditions = conditions(raw);
    if (object == null) {
      for (var c : conditions)
        if (c.path("field").asText().startsWith("custom:"))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0008");
      return List.of();
    }
    var fields = fields(object);
    var seen = new HashSet<String>();
    var result = new ArrayList<BusinessFilterTerm>();
    for (var c : conditions) {
      String key = c.path("field").asText();
      if (!key.startsWith("custom:")) continue;
      String id = key.substring(7);
      var f = fields.get(id);
      if (f == null || !f.path("filterable").asBoolean() || !seen.add(id))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.inventory.message0005");
      UUID.fromString(id);
      var vals = c.path("values");
      if (!vals.isArray() || vals.size() > 2000)
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0011");
      for (var v : vals)
        if (!v.isTextual() || v.asText().length() > 200)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0009");
      String type = f.path("type").asText(), op = c.path("operator").asText();
      var values = new ArrayList<Object>();
      Operator operator;
      boolean exclusive = false;
      if (Set.of("TEXT", "LONG_TEXT").contains(type)) {
        if (!op.equals("contains") || vals.size() != 1)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0010");
        operator = Operator.CONTAINS;
        values.add(vals.get(0).asText());
      } else if (Set.of("NUMBER", "DATE", "DATETIME").contains(type)) {
        if (!op.equals("range") || vals.size() != 2)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0011");
        operator = Operator.RANGE;
        for (int i = 0; i < 2; i++) {
          String value = vals.get(i).asText();
          if (value.isBlank()) {
            values.add(null);
            continue;
          }
          try {
            Object bound;
            if (type.equals("NUMBER")) bound = new BigDecimal(value);
            else if (type.equals("DATE")) bound = LocalDate.parse(value);
            else if (value.length() == 10) {
              bound =
                  i == 1
                      ? LocalDate.parse(value).plusDays(1).atStartOfDay()
                      : LocalDate.parse(value).atStartOfDay();
              exclusive = i == 1;
            } else bound = LocalDateTime.parse(value.replace(' ', 'T'));
            values.add(bound);
          } catch (Exception e) {
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.business.message0012");
          }
        }
        if (values.get(0) != null
            && values.get(1) != null
            && ((Comparable) values.get(0)).compareTo(values.get(1)) > 0)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.common.message0012");
      } else {
        if (!op.equals("in") || vals.isEmpty())
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0013");
        operator = Operator.IN;
        var selected = new LinkedHashSet<String>();
        vals.forEach(v -> selected.add(v.asText()));
        if (type.equals("DEPARTMENT")) {
          var allowed =
              references.all(object, "department", true).stream()
                  .map(r -> r.get("id").toString())
                  .collect(java.util.stream.Collectors.toSet());
          if (!allowed.containsAll(selected))
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.business.message0014");
          var orgs = repo.organizations(auth.tenantId());
          boolean changed;
          do {
            changed = false;
            for (var r : orgs)
              if (selected.contains(Objects.toString(r.get("parentId"), ""))
                  && allowed.contains(r.get("id").toString()))
                changed |= selected.add(r.get("id").toString());
          } while (changed);
        } else if (BusinessRules.reference(f)) {
          var allowed =
              references.fieldOptions(object, f, true).stream()
                  .map(r -> r.get("id").toString())
                  .collect(java.util.stream.Collectors.toSet());
          if (!allowed.containsAll(selected))
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.business.message0015");
        }
        for (String value : selected) {
          if (type.equals("BOOLEAN")) {
            if (!Set.of("true", "false").contains(value))
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.business.message0016");
            values.add(value.equals("true") ? 1 : 0);
          } else values.add(value);
        }
      }
      result.add(new BusinessFilterTerm(id, column(f), operator, values, exclusive));
    }
    return result;
  }

  public SqlPredicate predicate(String object, String raw, String alias) {
    return queries.predicate(auth.tenantId(), object, alias, terms(object, raw));
  }

  public <T> void apply(LambdaQueryWrapper<T> query, String object, String raw) {
    var predicate =
        predicate(object, raw, object.equals("equipment") ? "eam_equipment" : "eam_repair_order");
    var sql = new StringBuilder();
    int index = 0;
    for (char c : predicate.sql().toCharArray())
      sql.append(c == '?' ? "{" + (index++) + "}" : String.valueOf(c));
    query.apply(sql.toString(), predicate.arguments().toArray());
  }

  public String orderExpression(String object, String key, String alias) {
    if (!key.startsWith("custom:")) return null;
    var f = fields(object).get(key.substring(7));
    if (f == null
        || !f.path("listable").asBoolean()
        || BusinessRules.multiple(f)
        || f.path("type").asText().equals("LONG_TEXT"))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0017");
    return queries.orderExpression(
        auth.tenantId(),
        object,
        key.substring(7),
        BusinessRules.reference(f) ? "reference_name" : column(f),
        alias);
  }

  public List<Map<String, Object>> filter(
      String object, List<Map<String, Object>> rows, String raw) {
    var terms = terms(object, raw);
    if (terms.isEmpty()) return rows;
    var ids =
        rows.stream().map(r -> r.get("id").toString()).filter(v -> v.matches("[0-9]+")).toList();
    var matched = new HashSet<String>();
    for (int start = 0; start < ids.size(); start += 500)
      matched.addAll(
          queries.matchAuthorized(
              auth.tenantId(),
              object,
              ids.subList(start, Math.min(start + 500, ids.size())),
              terms));
    return rows.stream().filter(r -> matched.contains(r.get("id").toString())).toList();
  }

  public Map<String, JsonNode> combinedFields(String... objects) {
    var result = new LinkedHashMap<String, JsonNode>();
    for (String object : objects) result.putAll(fields(object));
    return result;
  }

  public <T> void applyAcross(LambdaQueryWrapper<T> query, String raw, String... objects) {
    var all = combinedFields(objects);
    for (var c : conditions(raw))
      if (c.path("field").asText().startsWith("custom:")
          && !all.containsKey(c.path("field").asText().substring(7)))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.inventory.message0005");
    for (String object : objects) {
      var own = fields(object);
      var selected = json.createArrayNode();
      for (var c : conditions(raw))
        if (c.path("field").asText().startsWith("custom:")
            && own.containsKey(c.path("field").asText().substring(7))) selected.add(c);
      apply(query, object, selected.toString());
    }
  }

  public <T> boolean sortAcross(
      LambdaQueryWrapper<T> query, String key, String direction, String... objects) {
    if (key == null || !key.startsWith("custom:")) return false;
    for (String object : objects)
      if (fields(object).containsKey(key.substring(7))) return sort(query, object, key, direction);
    throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0018");
  }

  public <T> boolean sort(
      LambdaQueryWrapper<T> query, String object, String key, String direction) {
    if (key == null || !key.startsWith("custom:")) return false;
    if (!Set.of("asc", "desc").contains(direction))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0019");
    String table = object.equals("equipment") ? "eam_equipment" : "eam_repair_order";
    query.last(
        "ORDER BY "
            + orderExpression(object, key, table)
            + " "
            + direction
            + ", "
            + table
            + ".id ASC");
    return true;
  }

  private String column(JsonNode f) {
    return switch (f.path("type").asText()) {
      case "NUMBER" -> "number_value";
      case "DATE" -> "date_value";
      case "DATETIME" -> "datetime_value";
      case "BOOLEAN" -> "bool_value";
      case "CHOICE", "MULTI_CHOICE", "USER", "DEPARTMENT" -> "reference_id";
      default -> "text_value";
    };
  }
}
