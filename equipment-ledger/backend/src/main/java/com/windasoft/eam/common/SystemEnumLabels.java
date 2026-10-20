package com.windasoft.eam.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Explicit display-only mappings. Never use on IDs, customer dictionaries or write values. */
public final class SystemEnumLabels {
  private static final Map<String, Map<String, String>> KEYS;

  static {
    try (var in = SystemEnumLabels.class.getResourceAsStream("/i18n/enum-keys.json")) {
      KEYS =
          new ObjectMapper()
              .readValue(in, new TypeReference<Map<String, Map<String, String>>>() {});
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static String display(String kind, Object value) {
    String raw = Objects.toString(value, "");
    String key = KEYS.getOrDefault(kind, Map.of()).get(raw);
    return key == null ? raw : MessageContext.text(key);
  }

  private SystemEnumLabels() {}
}
