package com.windasoft.eam.common.experience;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;

public final class ExperienceSupport {
  public static long id(Object x) {
    try {
      return Long.parseLong(Objects.toString(x, "0"));
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.experience.message0004");
    }
  }

  public static String text(Map<String, Object> x, String key) {
    return Objects.toString(x.get(key), "").trim();
  }

  public static String required(Map<String, Object> x, String key, int max) {
    var s = text(x, key);
    if (s.isBlank() || s.length() > max)
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.experience.message0005", key, max);
    return s;
  }

  public static void check(boolean ok, String message) {
    if (!ok) throw new IllegalArgumentException(message);
  }

  public static void conflict() {
    throw new com.windasoft.eam.common.LocalizedStatusException(
        HttpStatus.CONFLICT, "server.experience.message0006");
  }

  public static List<Map<String, Object>> rows(Object x) {
    if (!(x instanceof List<?> values)) return List.of();
    return values.stream()
        .map(
            v -> {
              if (!(v instanceof Map<?, ?>))
                throw new com.windasoft.eam.common.LocalizedArgumentException(
                    "server.experience.message0007");
              return (Map<String, Object>) v;
            })
        .toList();
  }

  public static Object decode(ObjectMapper json, Object value) {
    try {
      return value == null ? null : json.readValue(value.toString(), Object.class);
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.experience.message0008");
    }
  }

  public static Map<String, Object> object(ObjectMapper json, Object value) {
    var x = decode(json, value);
    return x instanceof Map<?, ?> ? (Map<String, Object>) x : new LinkedHashMap<>();
  }

  public static String encode(ObjectMapper json, Object x) {
    try {
      return json.writeValueAsString(x);
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.experience.message0009");
    }
  }

  public static Map<String, Object> view(Map<String, Object> input) {
    var v = new LinkedHashMap<String, Object>();
    input.forEach(
        (key, value) ->
            v.put(
                key,
                value instanceof java.sql.Timestamp t
                    ? t.toLocalDateTime().toString()
                    : value instanceof java.math.BigDecimal d
                        ? d.toPlainString()
                        : value instanceof Long ? value.toString() : value));
    return v;
  }

  public static java.util.function.Predicate<Map<String, Object>> filters(
      ObjectMapper json, String raw, Set<String> fields) {
    if (raw == null || raw.isBlank()) return r -> true;
    check(raw.length() <= 12000, "筛选条件过长");
    var conditions = rows(decode(json, raw));
    check(conditions.size() <= 20, "筛选条件过多");
    for (var c : conditions) {
      check(fields.contains(text(c, "field")), "不支持的筛选字段");
      check(
          Set.of("contains", "eq", "in", "range", "empty", "notEmpty")
              .contains(text(c, "operator")),
          "不支持的筛选方式");
      check(c.get("values") instanceof List<?> l && l.size() <= 100, "筛选值格式无效");
    }
    return r ->
        conditions.stream()
            .allMatch(
                c -> {
                  String actual = Objects.toString(r.get(text(c, "field")), "").replace('T', ' ');
                  var values = (List<?>) c.get("values");
                  String first =
                      values.isEmpty() ? "" : values.getFirst().toString().replace('T', ' ');
                  return switch (text(c, "operator")) {
                    case "contains" ->
                        actual.toLowerCase(Locale.ROOT).contains(first.toLowerCase(Locale.ROOT));
                    case "eq" -> actual.equals(first);
                    case "in" -> values.stream().anyMatch(v -> actual.equals(v.toString()));
                    case "empty" -> actual.isBlank();
                    case "notEmpty" -> !actual.isBlank();
                    case "range" -> {
                      String high =
                          values.size() < 2 ? "" : values.get(1).toString().replace('T', ' ');
                      yield com.windasoft.eam.common.AdvancedFilters.matchesRange(
                          actual, first, high, actual.matches("-?[0-9]+(\\.[0-9]+)?"));
                    }
                    default -> false;
                  };
                });
  }
}
