package com.windasoft.eam.domain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ActivationStatusTest {
  private static final Pattern INLINE_LABELS = Pattern.compile(
      "\\?\\s*\"启用\"\\s*:\\s*\"停用\"|(?i:THEN)\\s*'启用'\\s*(?i:ELSE)\\s*'停用'");

  @Test
  void oldFlagsAndBooleanValuesKeepTheirWireLabels() {
    assertEquals(1, ActivationStatus.ENABLED.code());
    assertEquals(0, ActivationStatus.DISABLED.code());
    assertEquals("启用", ActivationStatus.fromFlag(1).label());
    assertEquals("停用", ActivationStatus.fromFlag(0).label());
    assertEquals("停用", ActivationStatus.fromFlag(null).label());
    assertEquals("停用", ActivationStatus.fromFlag(2).label());
    assertEquals("启用", ActivationStatus.fromEnabled(true).label());
    assertEquals("停用", ActivationStatus.fromEnabled(false).label());
  }

  @Test
  void detectsRepeatedStatusMappingsWithoutMatchingOtherBusinessCodes() {
    assertTrue(INLINE_LABELS.matcher("active ? \"启用\" : \"停用\"").find());
    assertTrue(INLINE_LABELS.matcher("CASE WHEN active THEN '启用' ELSE '停用' END").find());
    assertFalse(INLINE_LABELS.matcher("active ? \"启用\" : \"未启用\"").find());
    assertFalse(INLINE_LABELS.matcher("DISABLE(\"停用\")").find());
    assertFalse(INLINE_LABELS.matcher("ActivationStatus.fromFlag(value).label()").find());
  }

  @Test
  void javaAndListSqlReuseActivationLabels() throws Exception {
    var violations = new ArrayList<Path>();
    try (var paths = Files.walk(Path.of("src/main/java/com/windasoft/eam"))) {
      for (var path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
        if (INLINE_LABELS.matcher(Files.readString(path)).find()) violations.add(path);
      }
    }
    assertTrue(violations.isEmpty(), () -> "请复用 ActivationStatus：" + violations);
  }
}
