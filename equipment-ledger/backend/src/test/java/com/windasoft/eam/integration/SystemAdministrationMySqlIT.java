package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.MenuGrantForm;
import com.windasoft.eam.dto.system.OrgForm;
import com.windasoft.eam.dto.system.RoleForm;
import com.windasoft.eam.dto.system.UserForm;
import com.windasoft.eam.dto.system.UserStatusForm;
import com.windasoft.eam.repository.system.AuthenticationRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.OrganizationService;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.service.system.RoleManagementService;
import com.windasoft.eam.service.system.UserManagementService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 同一专项隔离库验证真实写入和回滚，不连接业务库。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class SystemAdministrationMySqlIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    SystemUserQueryMySqlIT.database(p);
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean AuthenticationRepository sessions;
  @MockitoBean NotificationScheduler notificationScheduler;
  @MockitoBean MaintenanceScheduler maintenanceScheduler;
  @MockitoBean InspectionScheduler inspectionScheduler;
  @MockitoBean InventoryReservationCleanup cleanup;
  @MockitoBean RepairPlanScheduler repairScheduler;
  @MockitoBean KnowledgeScheduler knowledgeScheduler;
  @Autowired UserManagementService users;
  @Autowired OrganizationService organizations;
  @Autowired RoleManagementService roles;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;

  @BeforeEach
  void identity() {
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(73001L);
  }

  private UserForm form(String account, Integer status, String email, Integer version) {
    return new UserForm(
        account, "系统治理测试", "", "", 71003L, List.of(), status, "666666", email, version);
  }

  private long createUser() {
    var data =
        json.valueToTree(
            users.createUser(form("sys-admin-" + IdWorker.getId(), 1, "before@example.com", 0)));
    assertTrue(data.path("id").isTextual());
    assertEquals(0, data.path("emailVersion").asInt());
    return Long.parseLong(data.path("id").asText());
  }

  @Test
  void userWritePreservesIdsEmailVersionAndRoleAssociation() {
    var role =
        json.valueToTree(
            roles.createRole(
                new RoleForm(
                    "SYS-" + IdWorker.getId(),
                    "治理角色",
                    "",
                    1,
                    Map.of(
                        "equipment",
                        new MenuGrantForm(List.of("查看"), List.of("all"), List.of())))));
    var account = "sys-admin-" + IdWorker.getId();
    var response =
        json.valueToTree(
            users.createUser(
                new UserForm(
                    account,
                    "用户",
                    "",
                    "",
                    71003L,
                    List.of(Long.parseLong(role.path("id").asText())),
                    1,
                    "666666",
                    "owner@example.com",
                    0)));
    long id = Long.parseLong(response.path("id").asText());
    assertEquals(role.path("id").asText(), response.path("roleIds").get(0).asText());
    assertTrue(response.path("emailVersion").isInt());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_sys_user_role WHERE tenant_id=10001 AND user_id=?",
            Integer.class,
            id));
    assertTrue(role.path("menuPermissions").path("equipment").path("actions").get(0).isTextual());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void missingEmailVersionRollsBackOtherUserEdits() {
    long id = createUser();
    try {
      String account =
          db.queryForObject(
              "SELECT account FROM eam_sys_user WHERE tenant_id=10001 AND id=?", String.class, id);
      assertThrows(
          IllegalArgumentException.class,
          () ->
              users.updateUser(
                  id,
                  new UserForm(
                      account,
                      "不应保存",
                      "",
                      "",
                      71003L,
                      List.of(),
                      1,
                      null,
                      "after@example.com",
                      null)));
      assertEquals(
          "系统治理测试",
          db.queryForObject(
              "SELECT display_name FROM eam_sys_user WHERE tenant_id=10001 AND id=?",
              String.class,
              id));
    } finally {
      db.update("DELETE FROM eam_sys_user WHERE tenant_id=10001 AND id=?", id);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void sessionFailureRollsBackStatusChange() {
    long id = createUser();
    try {
      doThrow(new IllegalStateException("模拟会话撤销失败")).when(sessions).revokeSessions(10001L, id);
      assertThrows(IllegalStateException.class, () -> users.userStatus(id, new UserStatusForm(0)));
      assertEquals(
          1,
          db.queryForObject(
              "SELECT status FROM eam_sys_user WHERE tenant_id=10001 AND id=?", Integer.class, id));
    } finally {
      db.update("DELETE FROM eam_sys_user WHERE tenant_id=10001 AND id=?", id);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void emailVersionIsAtomicAndAccountUpdateDoesNotOverwriteIt() {
    long id = createUser();
    try {
      String account =
          db.queryForObject(
              "SELECT account FROM eam_sys_user WHERE tenant_id=10001 AND id=?", String.class, id);
      var changed =
          users.updateUser(
              id,
              new UserForm(
                  account, "已改名", "", "", 71003L, List.of(), 1, null, "after@example.com", 0));
      assertEquals(1, changed.emailVersion());
      assertEquals("after@example.com", changed.email());
      users.updateUser(id, new UserForm(account, "再次改名", "", "", 71003L, List.of(), 1, null));
      assertEquals(
          1,
          db.queryForObject(
              "SELECT email_version FROM eam_sys_user WHERE tenant_id=10001 AND id=?",
              Integer.class,
              id));
      assertThrows(
          RuntimeException.class,
          () ->
              users.updateUser(
                  id,
                  new UserForm(
                      account,
                      "冲突不应保存",
                      "",
                      "",
                      71003L,
                      List.of(),
                      1,
                      null,
                      "stale@example.com",
                      0)));
      assertEquals(
          "再次改名",
          db.queryForObject(
              "SELECT display_name FROM eam_sys_user WHERE tenant_id=10001 AND id=?",
              String.class,
              id));
      assertEquals(
          "after@example.com",
          db.queryForObject(
              "SELECT email FROM eam_sys_user WHERE tenant_id=10001 AND id=?", String.class, id));
    } finally {
      db.update("DELETE FROM eam_sys_user WHERE tenant_id=10001 AND id=?", id);
    }
  }

  @Test
  void changingTenantCannotReadOrUpdateAnotherTenantsUser() {
    long id = createUser();
    when(auth.tenantId()).thenReturn(10002L);
    assertThrows(IllegalArgumentException.class, () -> users.userStatus(id, new UserStatusForm(0)));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT status FROM eam_sys_user WHERE tenant_id=10001 AND id=?", Integer.class, id));
  }

  @Test
  void editingMustNotBypassTheExistingSelfDisableRule() {
    long id = createUser();
    when(auth.userId()).thenReturn(id);
    assertThrows(IllegalArgumentException.class, () -> users.userStatus(id, new UserStatusForm(0)));
    String account =
        db.queryForObject(
            "SELECT account FROM eam_sys_user WHERE tenant_id=10001 AND id=?", String.class, id);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            users.updateUser(
                id, new UserForm(account, "系统治理测试", "", "", 71003L, List.of(), 0, null)));
  }

  @Test
  void organizationMoveRejectsCyclesAndKeepsAuditValues() {
    String code = "SYS-" + IdWorker.getId();
    var parent = organizations.createOrg(new OrgForm("父部门", code, "部门", 71002L, "", 1));
    var child =
        organizations.createOrg(
            new OrgForm("子部门", code + "-C", "部门", Long.valueOf(parent.id()), "", 1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            organizations.updateOrg(
                Long.valueOf(parent.id()),
                new OrgForm("父部门", code, "部门", Long.valueOf(child.id()), "", 1)));
    var updated =
        organizations.updateOrg(
            Long.valueOf(child.id()),
            new OrgForm("子部门已改名", code + "-C", "部门", Long.valueOf(parent.id()), "", 1));
    assertEquals("子部门已改名", updated.name());
    assertEquals(
        "子部门",
        db.queryForObject(
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(before_json,'$.name')) FROM eam_org_event WHERE"
                + " tenant_id=10001 AND org_id=? AND action='编辑'",
            String.class,
            Long.valueOf(child.id())));
  }

  @Test
  void organizationAuditKeepsOriginalBeforeAfterAndDeleteShape() {
    var created =
        json.valueToTree(
            organizations.createOrg(
                new OrgForm("治理部门", "SYS-" + IdWorker.getId(), "部门", 71002L, "", 1)));
    long id = Long.parseLong(created.path("id").asText());
    assertEquals("71002", created.path("parentId").asText());
    assertEquals(
        "治理部门",
        db.queryForObject(
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(after_json,'$.name')) FROM eam_org_event WHERE"
                + " tenant_id=10001 AND org_id=? ORDER BY id LIMIT 1",
            String.class,
            id));
    organizations.deleteOrg(id);
    assertEquals(
        1,
        db.queryForObject(
            "SELECT deleted FROM eam_org_unit WHERE tenant_id=10001 AND id=?", Integer.class, id));
  }
}
