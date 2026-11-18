package com.windasoft.eam.controller.knowledge;

import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class KnowledgeManagementContractTest {
  @Test
  void documentManagementHasExplicitContracts() {
    for (var controller :
        new Class<?>[] {KnowledgeDocumentController.class, KnowledgeRepairCaseController.class})
      for (var m : controller.getDeclaredMethods())
        if (Modifier.isPublic(m.getModifiers())) {
          ContractTypes.assertExplicit(m.getGenericReturnType());
          for (var t : m.getGenericParameterTypes()) ContractTypes.assertExplicit(t);
        }
  }
}
