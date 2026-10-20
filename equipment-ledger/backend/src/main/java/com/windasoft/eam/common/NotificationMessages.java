package com.windasoft.eam.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public final class NotificationMessages {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Map<String, String> KEYS;

  static {
    try (var in = NotificationMessages.class.getResourceAsStream("/i18n/notification-keys.json")) {
      KEYS = JSON.readValue(in, new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static String key(Object kind) {
    String value = String.valueOf(kind);
    return value.startsWith("已由") && value.endsWith("接单")
        ? "notification.acceptedBy"
        : KEYS.get(value);
  }

  public static String arguments(Object kind, Object title) {
    var result = new LinkedHashMap<String, Object>();
    result.put("title", title == null ? "" : title);
    String value = String.valueOf(kind);
    if (value.startsWith("已由") && value.endsWith("接单"))
      result.put("actor", value.substring(2, value.length() - 2));
    try {
      return JSON.writeValueAsString(result);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid notification parameters", e);
    }
  }

  public static Object render(Object value) {
    if (value instanceof List<?> list)
      return list.stream().map(NotificationMessages::render).toList();
    if (value instanceof Map<?, ?> map) {
      var result = new LinkedHashMap<String, Object>();
      map.forEach((k, v) -> result.put(String.valueOf(k), render(v)));
      Object key = result.get("messageKey");
      if (key != null && !String.valueOf(key).isBlank())
        try {
          Object args = result.get("messageArgs");
          Map<String, Object> params =
              args instanceof Map<?, ?> m
                  ? JSON.convertValue(m, new TypeReference<>() {})
                  : JSON.readValue(String.valueOf(args), new TypeReference<>() {});
          result.put("title", MessageContext.render(new MessageRef(String.valueOf(key), params)));
        } catch (Exception ignored) {
          /* Keep the original historical text if metadata is unavailable. */
        }
      return result;
    }
    return value;
  }

  private NotificationMessages() {}
}
