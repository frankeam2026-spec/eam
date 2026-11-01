package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.windasoft.eam.repository.system.AuthenticationRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.service.system.UserManagementService;
import com.windasoft.eam.dto.system.UserStatusForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
  @Autowired JdbcTemplate db;

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
