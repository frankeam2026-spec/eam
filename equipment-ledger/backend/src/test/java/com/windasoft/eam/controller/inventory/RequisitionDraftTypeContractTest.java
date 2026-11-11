package com.windasoft.eam.controller.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.inventory.RequisitionDraftForm;
import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
      ContractTypes.assertExplicit(method.getGenericReturnType());
      for (Type input : method.getGenericParameterTypes()) {
        ContractTypes.assertExplicit(input);
      }
    }
  }

  @Test
  void nestedObjectMapRawCollectionsAndWildcardsAreRejected() throws Exception {
    for (String method : List.of("object", "map", "raw", "wildcard")) {
      Type type = Invalid.class.getDeclaredMethod(method).getGenericReturnType();
      assertThrows(AssertionError.class, () -> ContractTypes.assertExplicit(type));
    }
    assertDoesNotThrow(() -> ContractTypes.assertExplicit(RequisitionDraftForm.class));
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
