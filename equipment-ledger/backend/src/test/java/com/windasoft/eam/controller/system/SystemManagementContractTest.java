package com.windasoft.eam.controller.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.system.MenuGrantView;
import com.windasoft.eam.dto.system.RoleForm;
import com.windasoft.eam.dto.system.RoleView;
import com.windasoft.eam.dto.system.UserForm;
import com.windasoft.eam.dto.system.UserView;
import com.windasoft.eam.service.system.OrganizationService;
import com.windasoft.eam.service.system.RoleManagementService;
import com.windasoft.eam.service.system.UserListService;
import com.windasoft.eam.service.system.UserManagementService;
import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 管理端固定契约穿透DTO；菜单字典只放行String键及固定MenuGrantView值。 */
class SystemManagementContractTest {
  @Test
  void allFixedManagementResponsesAreExplicitAndMenuDictionaryIsBounded() throws Exception {
    for (var method : SystemManagementController.class.getDeclaredMethods()) {
      if (!Modifier.isPublic(method.getModifiers())) {
        continue;
      }
      if (List.of("roles", "createRole", "updateRole").contains(method.getName())) {
        continue;
      }
      if (method.getName().equals("userSummary")) {
        continue;
      }
      ContractTypes.assertExplicit(method.getGenericReturnType());
    }
    assertEquals(
        new TypeReference<DataResponse<List<RoleView>>>() {}.getType(),
        SystemManagementController.class.getDeclaredMethod("roles").getGenericReturnType());
    assertEquals(
        new TypeReference<DataResponse<RoleView>>() {}.getType(),
        SystemManagementController.class
            .getDeclaredMethod("createRole", RoleForm.class)
            .getGenericReturnType());
    assertEquals(
        new TypeReference<DataResponse<RoleView>>() {}.getType(),
        SystemManagementController.class
            .getDeclaredMethod("updateRole", Long.class, RoleForm.class)
            .getGenericReturnType());
    for (var field : RoleView.class.getRecordComponents()) {
      if (field.getName().equals("menuPermissions")) {
        var map = (ParameterizedType) field.getGenericType();
        assertEquals(Map.class, map.getRawType());
        assertEquals(String.class, map.getActualTypeArguments()[0]);
        assertEquals(MenuGrantView.class, map.getActualTypeArguments()[1]);
        ContractTypes.assertExplicit(map.getActualTypeArguments()[1]);
      } else {
        ContractTypes.assertExplicit(field.getGenericType());
      }
    }
  }

  @Test
  void httpKeepsDataEnvelopeIdPrecisionAndDeleteField() throws Exception {
    var users = mock(UserManagementService.class);
    var orgs = mock(OrganizationService.class);
    var roles = mock(RoleManagementService.class);
    when(users.createUser(any(UserForm.class)))
        .thenReturn(
            new UserView("9007199254740993", "t", "测试", "", "", "71003", List.of(), "启用", "", 0));
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new SystemManagementController(orgs, users, roles, mock(UserListService.class)))
            .build();
    mvc.perform(
            post("/api/v1/system/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"t\",\"name\":\"测试\",\"orgId\":\"71003\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("9007199254740993"))
        .andExpect(jsonPath("$.data.emailVersion").value(0));
    mvc.perform(delete("/api/v1/system/organizations/9007199254740993"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.deleted").value(true));
  }
}
