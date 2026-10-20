package com.windasoft.eam.service.business;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class BusinessRulesTest {
  final ObjectMapper json = new ObjectMapper();

  JsonNode node(String value) throws Exception {
    return json.readTree(value);
  }

  @Test
  void zeroAndFalseAreValues() throws Exception {
    assertFalse(BusinessRules.empty(node("0")));
    assertFalse(BusinessRules.empty(node("false")));
    assertTrue(BusinessRules.empty(node("[]")));
    assertTrue(BusinessRules.empty(node("null")));
  }

  @Test
  void conditionalRequiredAndHiddenPreserveBehavior() throws Exception {
    var f =
        node(
            "{\"id\":\"field\",\"enabled\":true,\"required\":true,\"visibleWhen\":{\"mode\":\"all\",\"items\":[{\"field\":\"categoryId\",\"operator\":\"eq\",\"values\":[\"12\"]}]}}");
    var fields = Map.of("field", f);
    assertTrue(BusinessRules.required(f, fields, node("{}"), "12"));
    assertFalse(BusinessRules.required(f, fields, node("{}"), "13"));
  }

  @Test
  void circularDependenciesAreRejected() throws Exception {
    var a =
        node(
            "{\"id\":\"a\",\"visibleWhen\":{\"items\":[{\"field\":\"b\",\"operator\":\"eq\",\"values\":[\"x\"]}]}}");
    var b =
        node(
            "{\"id\":\"b\",\"requiredWhen\":{\"items\":[{\"field\":\"a\",\"operator\":\"eq\",\"values\":[\"x\"]}]}}");
    assertThrows(
        IllegalArgumentException.class, () -> BusinessRules.validate(Map.of("a", a, "b", b)));
  }

  @Test
  void inactiveSourcesCannotBeReferenced() throws Exception {
    var a =
        node(
            "{\"id\":\"a\",\"visibleWhen\":{\"items\":[{\"field\":\"b\",\"operator\":\"eq\",\"values\":[\"x\"]}]}}");
    var b = node("{\"id\":\"b\",\"enabled\":false}");
    assertThrows(
        IllegalArgumentException.class, () -> BusinessRules.validate(Map.of("a", a, "b", b)));
  }

  @Test
  void layoutSupportsLegacyAndSpanning() throws Exception {
    assertDoesNotThrow(
        () -> BusinessRules.validateLayout(node("{\"fields\":[{\"width\":\"full\"}]}")));
    assertDoesNotThrow(
        () ->
            BusinessRules.validateLayout(
                node("{\"layout\":{\"columns\":4,\"maxWidth\":1200},\"fields\":[{\"span\":3}]}")));
  }

  @Test
  void invalidLayoutIsRejected() throws Exception {
    for (String config :
        List.of(
            "{\"layout\":{\"columns\":5}}",
            "{\"layout\":{\"columns\":2.5}}",
            "{\"layout\":{\"columns\":\"2\"}}",
            "{\"layout\":{\"maxWidth\":200}}",
            "{\"layout\":{\"maxWidth\":9999}}",
            "{\"layout\":{\"columns\":2},\"fields\":[{\"span\":3}]}",
            "{\"fields\":[{\"span\":0}]}")) {
      assertThrows(
          IllegalArgumentException.class, () -> BusinessRules.validateLayout(node(config)));
    }
  }
}
