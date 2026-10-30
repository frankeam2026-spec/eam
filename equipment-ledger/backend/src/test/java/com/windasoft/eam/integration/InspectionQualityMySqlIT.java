package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.windasoft.eam.repository.experience.HomeRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.service.experience.HomeService;
import com.windasoft.eam.service.maintenance.MaintenanceService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "EAM_IDEA_QUALITY_TEST_URL", matches = ".+")
class InspectionQualityMySqlIT {
  private static final long TENANT = 992930001L;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry properties) {
    String url = System.getenv("EAM_IDEA_QUALITY_TEST_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:(3308/eam_idea_quality_test_20261030|3306/eam_engineering_test)(\\?.*)?")) {
      throw new IllegalStateException("仅允许连接明确隔离的质量检查数据库");
    }
    properties.add("spring.datasource.url", () -> url);
    properties.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_USER", "root"));
    properties.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_PASSWORD", ""));
    properties.add(
        "eam.notification.secret-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean com.windasoft.eam.scheduler.system.NotificationScheduler notificationScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inspection.InspectionScheduler inspectionScheduler;
  @MockitoBean com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler maintenanceScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup reservationCleanup;
  @MockitoBean com.windasoft.eam.scheduler.experience.RepairPlanScheduler repairScheduler;
  @MockitoBean com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler knowledgeScheduler;
  @MockitoSpyBean HomeRepository homeRepository;
  @MockitoSpyBean MaintenanceRepository maintenanceRepository;
  @Autowired HomeService home;
  @Autowired MaintenanceService maintenance;
  @Autowired JdbcTemplate db;

  @BeforeEach
  void identity() {
    when(auth.tenantId()).thenReturn(TENANT);
    when(auth.userId()).thenReturn(992930002L);
    assertEquals(0L, count("eam_home_template"));
  }

  @AfterEach
  void cleanup() {
    // 测试没有外围事务，以便真实验证业务入口提交和回滚；只清理本用例专属租户。
    for (String table :
        List.of(
            "eam_home_template_version_role",
            "eam_home_template_role",
            "eam_home_template_version",
            "eam_home_template")) {
      db.update("DELETE FROM " + table + " WHERE tenant_id=?", TENANT);
    }
  }

  private long count(String table) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=?", Long.class, TENANT);
  }

  @Test
  void templateInitializationRollsBackHeaderWhenVersionFails() {
    doThrow(new IllegalStateException("模拟版本保存失败"))
        .when(homeRepository)
        .insertPresetVersion(anyLong(), any(), any(), any(), any(), anyLong());
    assertThrows(IllegalStateException.class, home::templates);
    assertEquals(0L, count("eam_home_template"));
    assertEquals(0L, count("eam_home_template_version"));
    assertEquals(0L, count("eam_home_template_role"));
  }

  @Test
  void templatesRemainIdempotentAndPermissionDenialCannotSeed() {
    var denied = new ResponseStatusException(HttpStatus.FORBIDDEN);
    doThrow(denied).when(permissions).require("homeTemplate", "查看");
    assertThrows(ResponseStatusException.class, home::templates);
    assertEquals(0L, count("eam_home_template"));
    org.mockito.Mockito.doNothing().when(permissions).require("homeTemplate", "查看");
    var first = home.templates();
    long templates = count("eam_home_template");
    assertTrue(templates > 0);
    assertEquals(first.size(), home.templates().size());
    assertEquals(templates, count("eam_home_template"));
  }

  @Test
  void maintenanceExportPreservesPermissionsAndUsesReadOnlyTransaction() {
    doAnswer(
            call -> {
              assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
              assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
              return call.callRealMethod();
            })
        .when(maintenanceRepository)
        .listDefinitions(TENANT, "types");
    assertEquals(List.of(), maintenance.exportRows("types"));
    org.mockito.Mockito.verify(permissions).require("maintenanceType", "导出");
    org.mockito.Mockito.verify(permissions).require("maintenanceType", "查看");
  }
}
