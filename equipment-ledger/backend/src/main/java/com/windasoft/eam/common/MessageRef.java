package com.windasoft.eam.common;

import java.util.*;

public record MessageRef(String key, Map<String, Object> params) {
  public MessageRef(String key) {
    this(key, Map.of());
  }

  public static MessageRef of(String key, Object... args) {
    var p = new LinkedHashMap<String, Object>();
    for (int i = 0; i < args.length; i++) p.put("p" + i, args[i] == null ? "" : args[i]);
    return new MessageRef(key, p);
  }

  public String defaultText() {
    return BuiltinMessages.defaultText(key, params);
  }
}
