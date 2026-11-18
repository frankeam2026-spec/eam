package com.windasoft.eam.controller.printing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.windasoft.eam.dto.printing.PrintViews;
import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.junit.jupiter.api.Test;

class PrintContractTest {
  private void check(Type type) {
    if (type instanceof ParameterizedType p) {
      for (var t : p.getActualTypeArguments()) check(t);
      return;
    }
    if (type == PrintViews.Catalog.class) {
      for (var field : PrintViews.Catalog.class.getRecordComponents())
        if (field.getName().equals("headers") || field.getName().equals("columns"))
          assertEquals(
              "java.util.Map<java.lang.String, java.lang.String>",
              field.getGenericType().getTypeName());
        else ContractTypes.assertExplicit(field.getGenericType());
      return;
    }
    ContractTypes.assertExplicit(type);
  }

  @Test
  void endpointsHaveExplicitNestedContracts() {
    for (var method : DocumentPrintController.class.getDeclaredMethods())
      if (Modifier.isPublic(method.getModifiers())) check(method.getGenericReturnType());
  }
}
