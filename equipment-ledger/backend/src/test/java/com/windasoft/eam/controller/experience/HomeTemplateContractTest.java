package com.windasoft.eam.controller.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.windasoft.eam.dto.experience.HomeContracts;
import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HomeTemplateContractTest {
  private void check(Type type) {
    if (type instanceof ParameterizedType generic) {
      for (var item : generic.getActualTypeArguments()) check(item);
      return;
    }
    if (type == HomeContracts.State.class) {
      for (var field : HomeContracts.State.class.getRecordComponents())
        if (field.getName().equals("permissions"))
          assertEquals(
              "java.util.Map<java.lang.String, java.util.Set<java.lang.String>>",
              field.getGenericType().getTypeName());
        else ContractTypes.assertExplicit(field.getGenericType());
      return;
    }
    ContractTypes.assertExplicit(type);
  }

  /** 仅覆盖本批首页配置和模板入口，既有工作中心任务/组件查询不冒称已治理。 */
  @Test
  void templateManagementHasExplicitContracts() {
    for (var method : HomeController.class.getDeclaredMethods())
      if (Set.of(
              "templates",
              "roles",
              "create",
              "save",
              "publish",
              "disable",
              "home",
              "layout",
              "reset",
              "preferred",
              "recent",
              "pagePreferences",
              "catalog")
          .contains(method.getName())) check(method.getGenericReturnType());
    for (var method : HomeOverviewController.class.getDeclaredMethods())
      if (Modifier.isPublic(method.getModifiers())) check(method.getGenericReturnType());
  }
}
