package com.windasoft.eam.common;

import com.fasterxml.jackson.core.JsonProcessingException;
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
    if (value instanceof List<?> list) {
      return list.stream().map(NotificationMessages::render).toList();
    }
    if (value instanceof Map<?, ?> map) {
      var result = new LinkedHashMap<String, Object>();
      map.forEach((k, v) -> result.put(String.valueOf(k), render(v)));
      Object key = result.get("messageKey");
      if (key != null && !String.valueOf(key).isBlank()) {
        try {
          result.put("title", translatedTitle(String.valueOf(key), result.get("messageArgs")));
        } catch (Exception ignored) {
          // 历史元数据缺失时保留原标题，不能清空标题或让整页消息查询失败。
        }
      }
      return result;
    }
    return value;
  }

  /** 类型化消息只翻译标题；无法解析的历史元数据继续保留原文，与旧递归渲染使用同一规则。 */
  public static String renderTitle(String historical, String key, Object arguments) {
    if (key == null || key.isBlank()) {
      return historical;
    }
    try {
      return translatedTitle(key, arguments);
    } catch (Exception ignored) {
      return historical;
    }
  }

  private static String translatedTitle(String key, Object arguments)
      throws JsonProcessingException {
    Map<String, Object> params =
        arguments instanceof Map<?, ?> map
            ? JSON.convertValue(map, new TypeReference<>() {})
            : JSON.readValue(String.valueOf(arguments), new TypeReference<>() {});
    return MessageContext.render(new MessageRef(key, params));
  }

  private NotificationMessages() {}
}
