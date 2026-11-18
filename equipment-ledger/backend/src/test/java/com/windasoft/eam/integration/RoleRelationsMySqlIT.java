package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.MenuGrantForm;
import com.windasoft.eam.dto.system.RoleForm;
import com.windasoft.eam.repository.system.RoleManagementRepository;
import com.windasoft.eam.repository.system.RoleMenuScopeRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.service.system.RoleManagementService;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 仅在本专项隔离库验证品牌配置的版本、素材访问、事务回滚及并发引用一致性。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class RoleRelationsMySqlIT {
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

  @Autowired RoleManagementService roles;
  @Autowired RoleManagementRepository roleReads;

  @MockitoSpyBean RoleMenuScopeRepository scopes;

  private long tenant;

  @BeforeEach
  void identity() {
    tenant = IdWorker.getId();
    when(auth.tenantId()).thenReturn(tenant);
    when(auth.userId()).thenReturn(73001L);
  }

  @Test
  void typedRelationsKeepLegacyOrderTargetsAndTenant() {
    long role = IdWorker.getId(), grant = IdWorker.getId();
    db.update(
        "INSERT INTO eam_sys_role(id,tenant_id,code,name,data_scope) VALUES(?,?,?,?,'指定部门')",
        role,
        tenant,
        "R-" + role,
        "旧授权");
    db.update("INSERT INTO eam_role_organization VALUES(?,?,?,0)", tenant, role, 9007199254740993L);
    db.update(
        "INSERT INTO eam_role_legacy_action VALUES(?,?,?, ?,0)", tenant, role, "equipment", "view");
    db.update(
        "INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key)"
            + " VALUES(?,?,?,'equipment')",
        grant,
        tenant,
        role);
    db.update("INSERT INTO eam_role_menu_action VALUES(?,?,?,0)", tenant, grant, "查看");
    db.update("INSERT INTO eam_role_menu_scope VALUES(?,?,?,0)", tenant, grant, "selected");
    db.update(
        "INSERT INTO eam_role_menu_target VALUES(?,?,'department',?,0)",
        tenant,
        grant,
        9007199254740993L);
    var old = roleReads.role(tenant, role);
    assertEquals(List.of(9007199254740993L), old.getOrganizationIds());
    assertEquals(List.of("equipment.view"), old.getLegacyActions());
    var menu = roleReads.menuPermissions(tenant, role).getFirst();
    assertEquals(List.of("查看"), menu.getActions());
    assertEquals(List.of("selected"), menu.getDataScopes());
    assertEquals(List.of(9007199254740993L), menu.getTargetIds());
    assertTrue(roleReads.menuPermissions(tenant + 1, role).isEmpty());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void failedGrantRelationsRollBackRoleAndPriorActions() {
    String code = "R-" + IdWorker.getId();
    try {
      doThrow(new IllegalStateException("授权范围写入失败"))
          .when(scopes)
          .insert(org.mockito.ArgumentMatchers.anyList());
      assertThrows(
          IllegalStateException.class,
          () ->
              roles.createRole(
                  new RoleForm(
                      code,
                      "回滚角色",
                      "",
                      1,
                      Map.of(
                          "equipment",
                          new MenuGrantForm(List.of("查看"), List.of("all"), List.of())))));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_sys_role WHERE tenant_id=? AND code=?",
              Integer.class,
              tenant,
              code));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_sys_role_menu_permission WHERE tenant_id=?",
              Integer.class,
              tenant));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_role_menu_action WHERE tenant_id=?",
              Integer.class,
              tenant));
    } finally {
      db.update("DELETE FROM eam_sys_role_menu_permission WHERE tenant_id=?", tenant);
      db.update("DELETE FROM eam_sys_role WHERE tenant_id=?", tenant);
    }
  }
}
