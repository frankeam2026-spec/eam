package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.windasoft.eam.dto.system.MenuGrantForm;
import com.windasoft.eam.dto.system.RoleForm;
import com.windasoft.eam.repository.system.AuthenticationRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.service.system.RoleManagementService;
import com.windasoft.eam.service.system.UserManagementService;
import com.windasoft.eam.dto.system.UserStatusForm;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** 仅连接隔离的工程测试库，验证真实 MySQL 事务回滚。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_ENGINEERING_DB_URL", matches = ".+")
class UserManagementMySqlIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    String url = System.getenv("EAM_ENGINEERING_DB_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/eam_engineering_test(\\?.*)?"))
      throw new IllegalStateException("Requires isolated eam_engineering_test database");
    properties.add("spring.datasource.url", () -> url);
    properties.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_USER", "eam"));
    properties.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_PASSWORD", ""));
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean AuthenticationRepository sessions;
  @Autowired UserManagementService users;
  @Autowired RoleManagementService roles;
  @Autowired JdbcTemplate db;

  @Test
  @Transactional
  void roleSavesExistingActionCodesAndRequiresView() {
    when(auth.tenantId()).thenReturn(10001L);
    var code = "QA-TYPED-PERMISSION";
    var withoutView = new RoleForm(code, "权限编码验证", "", 1,
        Map.of("workspace", new MenuGrantForm(List.of("团队工作查看"), List.of("all"), List.of())));
    assertThrows(IllegalArgumentException.class, () -> roles.createRole(withoutView));
    var form = new RoleForm(code, "权限编码验证", "", 1,
        Map.of("workspace", new MenuGrantForm(List.of("查看", "团队工作查看"), List.of("all"), List.of())));
    var created = roles.createRole(form);
    var roleId = Long.parseLong(created.id());
    var loaded = roles.roles().stream().filter(role -> role.id().equals(created.id())).findFirst().orElseThrow();
    assertEquals(List.of("查看", "团队工作查看"), loaded.menuPermissions().get("workspace").actions());
    var edit = new RoleForm(code, "更新权限编码验证", "", 1,
        Map.of("workspace", new MenuGrantForm(List.of("查看"), List.of("all"), List.of())));
    assertEquals(List.of("查看"), roles.updateRole(roleId, edit).menuPermissions().get("workspace").actions());
    assertTrue(roles.roles().stream().anyMatch(role -> role.id().equals(created.id())
        && role.menuPermissions().get("workspace").actions().equals(List.of("查看"))));
  }

  @Test
  void statusUpdateRollsBackWhenSessionRevocationFails() {
    long tenant = 10001L;
    var fixture = db.queryForMap(
        "SELECT id,status FROM eam_sys_user WHERE tenant_id=? AND deleted=0"
            + " AND status=1 ORDER BY id LIMIT 1",
        tenant);
    long id = ((Number) fixture.get("id")).longValue();

    when(auth.tenantId()).thenReturn(tenant);
    when(auth.userId()).thenReturn(-1L);
    doThrow(new IllegalStateException("session revocation failed"))
        .when(sessions).revokeSessions(tenant, id);

    assertThrows(
        IllegalStateException.class,
        () -> users.userStatus(id, new UserStatusForm(0)));
    Integer actual = db.queryForObject(
        "SELECT status FROM eam_sys_user WHERE tenant_id=? AND id=?",
        Integer.class,
        tenant,
        id);
    assertEquals(1, actual);
  }
}
