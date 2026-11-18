package com.windasoft.eam.controller.experience;


import com.windasoft.eam.dto.experience.HomeContracts;
import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HomeTemplateContractTest {
  private static final ContractTypes.DynamicField PERMISSIONS =
      new ContractTypes.DynamicField(
          HomeContracts.State.class,
          "permissions",
          "java.util.Map<java.lang.String, java.util.Set<java.lang.String>>");

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
          .contains(method.getName())) ContractTypes.assertEndpoint(method, PERMISSIONS);
    for (var method : HomeOverviewController.class.getDeclaredMethods())
      if (Modifier.isPublic(method.getModifiers()))
        ContractTypes.assertEndpoint(method, PERMISSIONS);
  }
}
