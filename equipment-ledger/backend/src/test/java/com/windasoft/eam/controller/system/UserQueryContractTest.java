package com.windasoft.eam.controller.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.ApiExceptionHandler;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.system.UserListView;
import com.windasoft.eam.dto.system.UserSummary;
import com.windasoft.eam.repository.system.UserListRepository;
import com.windasoft.eam.service.system.OrganizationService;
import com.windasoft.eam.service.system.RoleManagementService;
import com.windasoft.eam.service.system.UserListService;
import com.windasoft.eam.service.system.UserManagementService;
import com.windasoft.eam.support.ContractTypes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 本批只覆盖列表和导出完整固定响应，不放宽既有写入接口的存量基线。 */
class UserQueryContractTest {
  @Test
  void listAndExportHaveExplicitNestedResponseTypes() throws Exception {
    for (String method : List.of("users", "userExport")) {
      ContractTypes.assertExplicit(
          SystemManagementController.class
              .getDeclaredMethod(method, Map.class)
              .getGenericReturnType());
    }
  }

  @Test
  void summaryAllowsOnlyItsRealRoleCountDictionaryAndRepositoryRowsStayTyped() throws Exception {
    assertEquals(
        UserSummary.class,
        SystemManagementController.class.getDeclaredMethod("userSummary").getReturnType());
    for (var component : UserSummary.class.getRecordComponents()) {
      if (component.getName().equals("roleCounts")) {
        assertEquals(
            new TypeReference<Map<String, Long>>() {}.getType(), component.getGenericType());
      } else {
        ContractTypes.assertExplicit(component.getGenericType());
      }
    }
    ContractTypes.assertExplicit(
        UserListRepository.class
            .getDeclaredMethod("page", UserListRepository.Selection.class, ListQuery.class)
            .getGenericReturnType());
    assertThrows(
        AssertionError.class,
        () ->
            ContractTypes.assertExplicit(
                new TypeReference<DataResponse<List<Object>>>() {}.getType()));
  }

  @Test
  void actualHttpEndpointsPreserveListExportAndSummaryEnvelopes() throws Exception {
    var lists = mock(UserListService.class);
    var row =
        new UserListView(
            "9007199254740993", "test", "测试", "", "", "", List.of("12"), "启用", "", "", "", 7);
    when(lists.query(any())).thenReturn(new PageResult<>(List.of(row), 2, 20, 21));
    when(lists.export(any())).thenReturn(List.of(row));
    when(lists.summary()).thenReturn(new UserSummary(Map.of("12", 1L)));
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new SystemManagementController(
                    mock(OrganizationService.class),
                    mock(UserManagementService.class),
                    mock(RoleManagementService.class),
                    lists))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    mvc.perform(get("/api/v1/system/users").param("page", "2").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.number").value(2))
        .andExpect(jsonPath("$.data[0].id").value("9007199254740993"));
    mvc.perform(get("/api/v1/system/users/export").param("q", "测试"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].emailVersion").value(7));
    mvc.perform(get("/api/v1/system/users/summary"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"roleCounts\":{\"12\":1}}"));
    mvc.perform(get("/api/v1/system/users").param("page", "0")).andExpect(status().isBadRequest());
    verify(lists, times(1)).query(any());
  }

  @Test
  void userListPreservesEmptyStringsIdsAndEmailVersion() {
    var row =
        new UserListView(
            "9007199254740993", "test", "测试", "", "", "", List.of("12"), "启用", "", "", "", 7);
    var tree = new ObjectMapper().valueToTree(new PageResult<>(List.of(row), 1, 20, 1));
    assertTrue(tree.at("/data/0/id").isTextual());
    assertEquals("", tree.at("/data/0/orgId").asText());
    assertEquals("", tree.at("/data/0/email").asText());
    assertEquals(7, tree.at("/data/0/emailVersion").asInt());
    assertEquals(20, tree.at("/page/size").asInt());
    assertEquals("12", tree.at("/data/0/roleIds/0").asText());
  }
}
