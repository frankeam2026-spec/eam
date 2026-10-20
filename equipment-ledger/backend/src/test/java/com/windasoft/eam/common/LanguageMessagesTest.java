package com.windasoft.eam.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LanguageMessagesTest {
  @AfterEach
  void clear() {
    MessageContext.clear();
  }

  @Test
  void builtinKeysAndParametersRemainCompatible() {
    var baseline = BuiltinMessages.get("zh-CN");
    for (String locale : BuiltinMessages.NAMES.keySet()) {
      assertEquals(baseline.keySet(), BuiltinMessages.get(locale).keySet());
      assertFalse(
          BuiltinMessages.get(locale).entrySet().stream()
              .anyMatch(e -> !e.getKey().startsWith("component.") && e.getValue().isBlank()));
    }
  }

  @Test
  void requestSnapshotIsRestoredAfterWorkerScope() throws Exception {
    MessageContext.set(BuiltinMessages.get("en-US"));
    String before = MessageContext.text("language.field.displayLanguage");
    try (var ignored = MessageContext.open(BuiltinMessages.get("zh-TW"))) {
      assertEquals("顯示語言", MessageContext.text("language.field.displayLanguage"));
    }
    assertEquals(before, MessageContext.text("language.field.displayLanguage"));
  }

  @Test
  void namedParametersArePlainValues() {
    assertEquals(
        "<customer>", BuiltinMessages.format("{p0}", java.util.Map.of("p0", "<customer>")));
  }

  @Test
  void unknownKeysNeverLeakToCustomers() {
    assertFalse(MessageContext.text("not.registered").contains("not.registered"));
  }
}
