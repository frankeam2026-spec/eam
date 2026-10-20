package com.windasoft.eam.common;

import java.util.*;

/**
 * Request/worker-owned translation snapshot. Never reads repositories or changes business values.
 */
public final class MessageContext {
  private static final ThreadLocal<String> LOCALE = new ThreadLocal<>();

  public static String locale() {
    return Objects.requireNonNullElse(LOCALE.get(), "zh-CN");
  }

  public static void setLocale(String locale) {
    LOCALE.set(locale);
  }

  private static final ThreadLocal<Map<String, String>> CURRENT = new ThreadLocal<>();

  public static Map<String, String> capture() {
    var m = CURRENT.get();
    return m == null ? BuiltinMessages.get("zh-CN") : m;
  }

  public static AutoCloseable open(Map<String, String> messages) {
    var previous = CURRENT.get();
    CURRENT.set(messages);
    return () -> {
      if (previous == null) CURRENT.remove();
      else CURRENT.set(previous);
    };
  }

  public static void clear() {
    CURRENT.remove();
    LOCALE.remove();
  }

  public static void set(Map<String, String> messages) {
    CURRENT.set(messages);
  }

  public static String annotation(String message) {
    return message != null && message.startsWith("{") && message.endsWith("}")
        ? text(message.substring(1, message.length() - 1))
        : message;
  }

  public static String text(String key, Object... args) {
    return render(MessageRef.of(key, args));
  }

  public static String render(MessageRef ref) {
    return BuiltinMessages.format(
        capture()
            .getOrDefault(
                ref.key(), BuiltinMessages.get("zh-CN").getOrDefault(ref.key(), "请求无法完成")),
        ref.params());
  }

  private MessageContext() {}
}
