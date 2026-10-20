package com.windasoft.eam.service.imports;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class EquipmentImportPathTest {
  @Test
  void displayAndLegacyPathsUseTheSameOriginalNames() {
    Map<Long, Map<String, Object>> records = new HashMap<>();
    records.put(1L, new HashMap<>(Map.of("name", "集团")));
    records.put(2L, new HashMap<>(Map.of("name", "公司", "parent_id", 1L)));
    records.put(3L, new HashMap<>(Map.of("name", "部门", "parent_id", 2L)));
    assertEquals("集团 > 公司 > 部门", EquipmentImportValidation.path(records, 3L, " > "));
    assertEquals("集团 · 公司 · 部门", EquipmentImportValidation.path(records, 3L, " · "));
  }
}
