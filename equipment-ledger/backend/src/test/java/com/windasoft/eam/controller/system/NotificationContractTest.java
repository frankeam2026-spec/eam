package com.windasoft.eam.controller.system;

import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class NotificationContractTest {
  @Test
  void allManagementResponsesHaveExplicitNestedTypes() {
    for (var method : NotificationController.class.getDeclaredMethods())
      if (Modifier.isPublic(method.getModifiers()))
        ContractTypes.assertExplicit(method.getGenericReturnType());
  }
}
