package com.windasoft.eam.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.OrganizationView;
import com.windasoft.eam.dto.system.RoleView;
import com.windasoft.eam.dto.system.UserView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SystemViewContractTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void organizationResponseHasOriginalJsonFieldNames() throws Exception {
    var value = new OrganizationView("1", "工厂", "FACTORY", "公司", "", "", "启用");
    var actual = json.readValue(json.writeValueAsString(value), Map.class);
    assertEquals(Map.of("id", "1", "name", "工厂", "code", "FACTORY",
        "type", "公司", "parentId", "", "manager", "", "status", "启用"), actual);
  }

  @Test
  void roleResponsePreservesPermissionObject() throws Exception {
    var value = new RoleView("2", "ADMIN", "管理员", "", "启用",
        Map.of("equipment", Map.of("actions", List.of("查看"),
            "dataScopes", List.of("all"), "orgIds", List.of())));
    var actual = json.readTree(json.writeValueAsString(value));
    assertEquals("2", actual.path("id").asText());
    assertEquals("查看", actual.path("menuPermissions").path("equipment")
        .path("actions").get(0).asText());
    assertEquals(6, actual.size());
  }

  @Test
  void userResponsePreservesIdStringsAndEmailVersionNumber() throws Exception {
    var value = new UserView("3", "tester", "测试", "", "", "10",
        List.of("2"), "启用", "test@example.com", 7);
    var actual = json.readTree(json.writeValueAsString(value));
    assertEquals("3", actual.path("id").asText());
    assertEquals("10", actual.path("orgId").asText());
    assertEquals("2", actual.path("roleIds").get(0).asText());
    assertEquals(7, actual.path("emailVersion").asInt());
    assertEquals(10, actual.size());
  }
}
