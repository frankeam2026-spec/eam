package com.windasoft.eam.controller.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.inventory.RequisitionDraftForm;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Scope is the three migrated endpoints, not an exception for the rest of the repository. */
class RequisitionDraftTypeContractTest {
  @Test
  void migratedContractsCannotHideUntypedModels() throws Exception {
    for (String action : List.of("create", "update", "detail")) {
      Method method =
          Arrays.stream(RequisitionController.class.getDeclaredMethods())
              .filter(m -> m.getName().equals(action))
              .findFirst()
              .orElseThrow();
      verifyType(method.getGenericReturnType(), new HashSet<>());
      for (Type input : method.getGenericParameterTypes()) {
        verifyType(input, new HashSet<>());
      }
    }
  }

  @Test
  void nestedObjectMapRawCollectionsAndWildcardsAreRejected() throws Exception {
    for (String method : List.of("object", "map", "raw", "wildcard")) {
      Type type = Invalid.class.getDeclaredMethod(method).getGenericReturnType();
      assertThrows(AssertionError.class, () -> verifyType(type, new HashSet<>()));
    }
    assertDoesNotThrow(() -> verifyType(RequisitionDraftForm.class, new HashSet<>()));
  }

  static void verifyType(Type type, Set<Type> visited) {
    assertFalse(
        type instanceof WildcardType || type instanceof TypeVariable<?>,
        "Unresolved contract: " + type);
    if (!visited.add(type)) {
      return;
    }
    if (type instanceof ParameterizedType parameterized) {
      Class<?> raw = (Class<?>) parameterized.getRawType();
      assertFalse(Map.class.isAssignableFrom(raw), "Fixed model cannot be a Map: " + type);
      for (Type argument : parameterized.getActualTypeArguments()) {
        verifyType(argument, visited);
      }
      if (raw == DataResponse.class || Collection.class.isAssignableFrom(raw)) {
        return;
      }
      verifyType(raw, visited);
      return;
    }
    assertInstanceOf(Class.class, type);
    Class<?> model = (Class<?>) type;
    assertNotEquals(Object.class, model, "Object hides the contract");
    assertFalse(
        Collection.class.isAssignableFrom(model) || Map.class.isAssignableFrom(model),
        "Raw container: " + model);
    assertFalse(
        model.getName().startsWith("com.windasoft.eam.entity."), "HTTP cannot expose an entity");
    if (!model.getName().startsWith("com.windasoft.eam.dto.") || model.isEnum()) {
      return;
    }
    for (Field field : model.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
        verifyType(field.getGenericType(), visited);
      }
    }
  }

  static class Invalid {
    Object object() {
      return null;
    }

    DataResponse<List<Map<String, Object>>> map() {
      return null;
    }

    @SuppressWarnings("rawtypes")
    DataResponse<List> raw() {
      return null;
    }

    DataResponse<?> wildcard() {
      return null;
    }
  }
}
