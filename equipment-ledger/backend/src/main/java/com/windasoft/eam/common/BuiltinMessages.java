package com.windasoft.eam.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public final class BuiltinMessages {
  public static final Map<String, String> NAMES =
      Map.of("zh-CN", "简体中文", "zh-TW", "繁體中文", "en-US", "English");
  private static final Map<String, Map<String, String>> DATA = new HashMap<>();

  static {
    for (String locale : NAMES.keySet())
      try (var in = BuiltinMessages.class.getResourceAsStream("/i18n/" + locale + ".json")) {
        DATA.put(
            locale,
            Collections.unmodifiableMap(
                new ObjectMapper().readValue(in, new TypeReference<Map<String, String>>() {})));
      } catch (Exception e) {
        throw new ExceptionInInitializerError(e);
      }
  }

  public static Map<String, String> get(String locale) {
    return DATA.getOrDefault(locale, DATA.get("zh-CN"));
  }

  public static String format(String value, Map<String, ?> args) {
    String result = value;
    for (var p : args.entrySet())
      result = result.replace("{" + p.getKey() + "}", String.valueOf(p.getValue()));
    return result;
  }

  public static String defaultText(String key, Map<String, ?> args) {
    return format(get("zh-CN").getOrDefault(key, "请求无法完成"), args);
  }

  private BuiltinMessages() {}
}
