package com.windasoft.eam.service.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OverviewLayoutValidationTest {
  private Map<String, Object> input(List<?> metrics) {
    var input = new LinkedHashMap<String, Object>();
    input.put("blocks", List.of(Map.of("id", "work1", "type", "work", "column", "main")));
    input.put("metrics", metrics);
    return input;
  }

  private Map<String, Object> normalize(List<?> metrics) {
    return OverviewLayoutSchema.normalize(
        input(metrics), Set.of("work"), false, value -> Map.of("blocks", value.get("blocks")));
  }

  @Test
  void invalidMetricElementsUseInputErrorInsteadOfNullPointer() {
    for (List<?> metrics :
        List.of(
            Arrays.asList("pending", null),
            List.of(3),
            List.of(Map.of("id", "pending")),
            List.of("pending", "pending"),
            List.of("unknown"))) {
      var error = assertThrows(IllegalArgumentException.class, () -> normalize(metrics));
      assertEquals("工作指标配置无效", error.getMessage());
    }
  }

  @Test
  void validMetricsKeepOrderAndEmptySelection() {
    assertEquals(
        List.of("today", "pending"), normalize(List.of("today", "pending")).get("metrics"));
    assertEquals(List.of(), normalize(List.of()).get("metrics"));
  }
}
