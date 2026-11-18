package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.config.security.AuthFilter;
import com.windasoft.eam.dto.system.ChangePasswordForm;
import com.windasoft.eam.dto.system.LoginForm;
import com.windasoft.eam.repository.system.AuthenticationRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.AuthenticationService;
import com.windasoft.eam.service.system.PermissionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 仅在本专项隔离库验证会话摘要、改密回滚、初始化保护和租户隔离。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class AuthenticationMySqlIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    SystemUserQueryMySqlIT.database(p);
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean NotificationScheduler notificationScheduler;
  @MockitoBean MaintenanceScheduler maintenanceScheduler;
  @MockitoBean InspectionScheduler inspectionScheduler;
  @MockitoBean InventoryReservationCleanup cleanup;
  @MockitoBean RepairPlanScheduler repairScheduler;
  @MockitoBean KnowledgeScheduler knowledgeScheduler;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;

  @Autowired AuthenticationService authentication;
  @MockitoSpyBean SystemUserRepository users;

  @MockitoSpyBean AuthenticationRepository sessions;

  private long tenant;
  private long user;
  private String account;

  @BeforeEach
  void identity() {
    tenant = IdWorker.getId();
    user = IdWorker.getId();
    account = "auth-" + user;
    when(auth.tenantId()).thenReturn(tenant);
    when(auth.userId()).thenReturn(user);
  }

  private void create() {
    db.update(
        "INSERT INTO eam_org_unit(id,tenant_id,code,name,org_type) VALUES(?,?,?, ?,'公司')",
        user,
        tenant,
        account,
        "认证组织");
    db.update(
        "INSERT INTO eam_sys_user(id,tenant_id,account,display_name,password_hash,org_id)"
            + " VALUES(?,?,?,?,?,?)",
        user,
        tenant,
        account,
        "认证回归",
        new BCryptPasswordEncoder().encode("old-password"),
        user);
  }

  @Test
  void loginStoresHashAndPasswordChangeRevokesSessionsWithoutOverwritingProfile() {
    create();
    var result = authentication.login(new LoginForm(tenant, account, "old-password"));
    assertEquals(String.valueOf(user), result.user().id());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_auth_session WHERE tenant_id=? AND user_id=?",
            Integer.class,
            tenant,
            user));
    assertEquals(
        AuthFilter.hash(result.token()),
        db.queryForObject(
            "SELECT token_hash FROM eam_auth_session WHERE tenant_id=? AND user_id=?",
            String.class,
            tenant,
            user));
    db.update(
        "UPDATE eam_sys_user SET email='preserve@example.com',email_version=7 WHERE id=?", user);
    authentication.changePassword(
        new ChangePasswordForm("old-password", "new-password"), result.token());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_auth_session WHERE tenant_id=? AND user_id=?",
            Integer.class,
            tenant,
            user));
    assertEquals(
        "preserve@example.com",
        db.queryForObject("SELECT email FROM eam_sys_user WHERE id=?", String.class, user));
    assertEquals(
        7,
        db.queryForObject(
            "SELECT email_version FROM eam_sys_user WHERE id=?", Integer.class, user));
    assertThrows(
        RuntimeException.class,
        () -> authentication.login(new LoginForm(tenant + 1, account, "new-password")));
  }

  @Test
  void initializationCannotOverwriteExistingPassword() {
    create();
    var before =
        db.queryForObject("SELECT password_hash FROM eam_sys_user WHERE id=?", String.class, user);
    users.initializePassword(tenant, user, "unexpected");
    assertEquals(
        before,
        db.queryForObject("SELECT password_hash FROM eam_sys_user WHERE id=?", String.class, user));
    users.initializePassword(tenant + 1, user, "unexpected");
    assertEquals(
        before,
        db.queryForObject("SELECT password_hash FROM eam_sys_user WHERE id=?", String.class, user));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void sessionFailureRollsBackPassword() {
    create();
    String before =
        db.queryForObject("SELECT password_hash FROM eam_sys_user WHERE id=?", String.class, user);
    try {
      doThrow(new IllegalStateException("会话撤销失败"))
          .when(sessions)
          .revokeOtherSessions(tenant, user, null);
      assertThrows(
          IllegalStateException.class,
          () ->
              authentication.changePassword(
                  new ChangePasswordForm("old-password", "new-password"), null));
      assertEquals(
          before,
          db.queryForObject(
              "SELECT password_hash FROM eam_sys_user WHERE id=?", String.class, user));
    } finally {
      deleteFixture();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void loginReadingOldCredentialsCannotIssueSessionAfterChange() throws Exception {
    create();
    var observed = new CountDownLatch(1);
    var changed = new CountDownLatch(1);
    var worker = Executors.newSingleThreadExecutor();
    try {
      doAnswer(
              call -> {
                var result = call.callRealMethod();
                observed.countDown();
                if (!changed.await(10, TimeUnit.SECONDS)) throw new AssertionError("改密未完成");
                return result;
              })
          .when(users)
          .activeUser(tenant, account);
      var login =
          worker.submit(
              () ->
                  assertThrows(
                      ResponseStatusException.class,
                      () -> authentication.login(new LoginForm(tenant, account, "old-password"))));
      assertTrue(observed.await(10, TimeUnit.SECONDS));
      authentication.changePassword(new ChangePasswordForm("old-password", "new-password"), null);
      changed.countDown();
      assertEquals(HttpStatus.UNAUTHORIZED, login.get(10, TimeUnit.SECONDS).getStatusCode());
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_auth_session WHERE tenant_id=? AND user_id=?",
              Integer.class,
              tenant,
              user));
    } finally {
      changed.countDown();
      worker.shutdownNow();
      worker.awaitTermination(10, TimeUnit.SECONDS);
      deleteFixture();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void staleProfileCannotOverwriteChangedCredentials() {
    create();
    try {
      var former = users.user(tenant, user);
      authentication.changePassword(new ChangePasswordForm("old-password", "new-password"), null);
      former.setDisplayName("资料已更新");
      users.updateProfile(former);
      users.updateStatus(former);
      users.updateDepartment(former);
      var current = users.user(tenant, user);
      var encoder = new BCryptPasswordEncoder();
      assertEquals("资料已更新", current.getDisplayName());
      assertTrue(encoder.matches("new-password", current.getPasswordHash()));
      assertFalse(encoder.matches("old-password", current.getPasswordHash()));
    } finally {
      deleteFixture();
    }
  }

  private void deleteFixture() {
    db.update("DELETE FROM eam_auth_session WHERE tenant_id=?", tenant);
    db.update("DELETE FROM eam_sys_user WHERE tenant_id=?", tenant);
    db.update("DELETE FROM eam_org_unit WHERE tenant_id=?", tenant);
  }
}
