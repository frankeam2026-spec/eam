package com.windasoft.eam.service.business;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.*;

public final class BusinessRules {
  private BusinessRules() {}

  public static void validateLayout(JsonNode config) {
    var layout = config.path("layout");
    if (!layout.isMissingNode() && !layout.isObject())
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0060");
    var columnsNode = layout.path("columns");
    int columns = columnsNode.asInt(2);
    if ((!columnsNode.isMissingNode() && !columnsNode.isIntegralNumber())
        || columns < 1
        || columns > 4)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0061");
    var widthNode = layout.path("maxWidth");
    int width = widthNode.asInt(0);
    if ((!widthNode.isMissingNode() && !widthNode.isIntegralNumber())
        || width != 0 && (width < 480 || width > 1920))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0062");
    for (var field : config.path("fields")) {
      var span = field.path("span");
      if (!span.isMissingNode()
          && (!span.isIntegralNumber() || span.asInt() < 1 || span.asInt() > columns))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.business.message0063");
    }
  }

  public static boolean inlineOptions(JsonNode field) {
    return Set.of("CHOICE", "MULTI_CHOICE").contains(field.path("type").asText())
        && field.path("options").isArray();
  }

  public static void validateOptions(JsonNode config, JsonNode published) {
    var previous = new HashMap<String, JsonNode>();
    for (var f : published.path("fields")) previous.put(f.path("id").asText(), f);
    for (var field : config.path("fields")) {
      boolean choice = Set.of("CHOICE", "MULTI_CHOICE").contains(field.path("type").asText());
      if (!choice && field.has("options"))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.business.message0064");
      if (!inlineOptions(field)) continue;
      var options = field.path("options");
      if (options.size() > 200 || field.path("enabled").asBoolean(true) && options.isEmpty())
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.business.message0065", field.path("name").asText());
      var ids = new HashSet<String>();
      var names = new HashSet<String>();
      for (var option : options) {
        try {
          UUID.fromString(option.path("id").asText());
        } catch (Exception e) {
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0066");
        }
        String name = option.path("name").asText().trim();
        if (!option.isObject()
            || !option.path("name").isTextual()
            || name.isBlank()
            || name.length() > 100
            || !ids.add(option.path("id").asText())
            || !names.add(name.toLowerCase(Locale.ROOT)))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0067", field.path("name").asText());
        if (!option.path("enabled").isBoolean()
            || !option.path("sortOrder").isIntegralNumber()
            || option.path("sortOrder").asInt() < 0
            || option.path("sortOrder").asInt() > 999999)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0068");
      }
      var old = previous.get(field.path("id").asText());
      if (old != null)
        for (var option : old.path("options"))
          if (!ids.contains(option.path("id").asText()))
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.business.message0069");
    }
    var fields = new HashMap<String, JsonNode>();
    for (var f : config.path("fields")) fields.put(f.path("id").asText(), f);
    for (var f : fields.values()) {
      if (!f.path("enabled").asBoolean(true)) continue;
      for (var rule : List.of(f.path("visibleWhen"), f.path("requiredWhen")))
        for (var condition : rule.path("items")) {
          var source = fields.get(condition.path("field").asText());
          if (source == null || !inlineOptions(source)) continue;
          var ids = new HashSet<String>();
          source.path("options").forEach(o -> ids.add(o.path("id").asText()));
          for (var value : condition.path("values"))
            if (!ids.contains(value.asText()))
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.business.message0070");
        }
    }
  }

  public static boolean empty(JsonNode value) {
    return value == null
        || value.isNull()
        || value.isMissingNode()
        || value.isArray() && value.isEmpty()
        || value.isTextual() && value.asText().isBlank();
  }

  public static boolean multiple(JsonNode f) {
    return f.path("type").asText().equals("MULTI_CHOICE") || f.path("multiple").asBoolean();
  }

  public static boolean reference(JsonNode f) {
    return Set.of("CHOICE", "MULTI_CHOICE", "USER", "DEPARTMENT").contains(f.path("type").asText());
  }

  public static boolean visible(
      JsonNode field, Map<String, JsonNode> fields, JsonNode values, String category) {
    return field.path("enabled").asBoolean(true)
        && rule(field.path("visibleWhen"), fields, values, category, true, new HashSet<>());
  }

  private static boolean rule(
      JsonNode rule,
      Map<String, JsonNode> fields,
      JsonNode values,
      String category,
      boolean fallback,
      Set<String> stack) {
    if (!rule.path("items").isArray() || rule.path("items").isEmpty()) return fallback;
    var results = new ArrayList<Boolean>();
    for (var c : rule.path("items")) {
      String key = c.path("field").asText();
      JsonNode actual = values.path(key);
      if (key.equals("categoryId"))
        actual =
            com.fasterxml.jackson.databind.node.TextNode.valueOf(category == null ? "" : category);
      else if (fields.containsKey(key)) {
        if (!stack.add(key))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.business.message0071");
        var source = fields.get(key);
        if (!source.path("enabled").asBoolean(true)
            || !rule(source.path("visibleWhen"), fields, values, category, true, stack))
          actual = com.fasterxml.jackson.databind.node.NullNode.instance;
        stack.remove(key);
      }
      var selected = new HashSet<String>();
      if (actual.isArray()) actual.forEach(v -> selected.add(v.asText()));
      else if (!empty(actual)) selected.add(actual.asText());
      var wanted = new HashSet<String>();
      c.path("values").forEach(v -> wanted.add(v.asText()));
      boolean match =
          switch (c.path("operator").asText()) {
            case "eq", "in" -> selected.stream().anyMatch(wanted::contains);
            case "ne" -> selected.stream().noneMatch(wanted::contains);
            case "range" -> {
              try {
                var n = new BigDecimal(actual.asText());
                String lo = c.path("values").path(0).asText(),
                    hi = c.path("values").path(1).asText();
                yield (lo.isBlank() || n.compareTo(new BigDecimal(lo)) >= 0)
                    && (hi.isBlank() || n.compareTo(new BigDecimal(hi)) <= 0);
              } catch (Exception e) {
                yield false;
              }
            }
            default ->
                throw new com.windasoft.eam.common.LocalizedArgumentException(
                    "server.business.message0072");
          };
      results.add(match);
    }
    return rule.path("mode").asText("all").equals("any")
        ? results.stream().anyMatch(Boolean::booleanValue)
        : results.stream().allMatch(Boolean::booleanValue);
  }

  public static boolean required(
      JsonNode field, Map<String, JsonNode> fields, JsonNode values, String category) {
    return visible(field, fields, values, category)
        && (field.path("required").asBoolean()
            || rule(field.path("requiredWhen"), fields, values, category, false, new HashSet<>()));
  }

  public static void validate(Map<String, JsonNode> fields) {
    for (var f : fields.values())
      if (f.path("enabled").asBoolean(true))
        for (String name : List.of("visibleWhen", "requiredWhen")) {
          var rule = f.path(name);
          if (rule.isMissingNode() || rule.isNull()) continue;
          if (!Set.of("all", "any").contains(rule.path("mode").asText("all"))
              || !rule.path("items").isArray())
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.business.message0073");
          for (var c : rule.path("items")) {
            String key = c.path("field").asText(), op = c.path("operator").asText();
            if (!key.equals("categoryId")
                && (!fields.containsKey(key) || !fields.get(key).path("enabled").asBoolean(true)))
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.business.message0074");
            if (!Set.of("eq", "ne", "in", "range").contains(op)
                || !c.path("values").isArray()
                || c.path("values").isEmpty())
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.business.message0075");
            if (op.equals("range")) {
              if (!fields.containsKey(key)
                  || !fields.get(key).path("type").asText().equals("NUMBER"))
                throw new com.windasoft.eam.common.LocalizedArgumentException(
                    "server.business.message0076");
              String lo = c.path("values").path(0).asText(), hi = c.path("values").path(1).asText();
              if (lo.isBlank() && hi.isBlank())
                throw new com.windasoft.eam.common.LocalizedArgumentException(
                    "server.business.message0077");
              if (!lo.isBlank()) new BigDecimal(lo);
              if (!hi.isBlank()) new BigDecimal(hi);
              if (!lo.isBlank()
                  && !hi.isBlank()
                  && new BigDecimal(lo).compareTo(new BigDecimal(hi)) > 0)
                throw new com.windasoft.eam.common.LocalizedArgumentException(
                    "server.business.message0078");
            }
          }
        }
    for (String key : fields.keySet())
      if (fields.get(key).path("enabled").asBoolean(true))
        walk(key, fields, new HashSet<>(), new HashSet<>());
  }

  private static void walk(
      String key, Map<String, JsonNode> fields, Set<String> path, Set<String> done) {
    if (done.contains(key)) return;
    if (!path.add(key))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0079");
    for (String name : List.of("visibleWhen", "requiredWhen"))
      for (var c : fields.get(key).path(name).path("items")) {
        String dep = c.path("field").asText();
        if (fields.containsKey(dep)) walk(dep, fields, path, done);
      }
    path.remove(key);
    done.add(key);
  }
}
