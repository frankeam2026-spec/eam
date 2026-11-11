package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.windasoft.eam.entity.maintenance.MaintenanceFile;
import com.windasoft.eam.repository.maintenance.MaintenanceFileRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** 仅在明确隔离库验证附件真实 CRUD、租户及字段映射；测试事务回滚，不清理业务租户。 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "EAM_IDEA_QUALITY_TEST_URL", matches = ".+")
class MaintenanceFileMySqlIT {
  private static final long TENANT = 9911116101L;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    String url = System.getenv("EAM_IDEA_QUALITY_TEST_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:(3308/eam_idea_quality_test_20261030|3306/eam_engineering_test)(\\?.*)?")) {
      throw new IllegalStateException("只允许连接明确隔离的保养质量检查库");
    }
    properties.add("spring.datasource.url", () -> url);
    properties.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_USER", "root"));
    properties.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_PASSWORD", ""));
  }

  @MockitoBean MaintenanceScheduler maintenanceScheduler;
  @MockitoBean InspectionScheduler inspectionScheduler;
  @MockitoBean NotificationScheduler notificationScheduler;
  @MockitoBean InventoryReservationCleanup reservationCleanup;
  @MockitoBean RepairPlanScheduler repairScheduler;
  @MockitoBean KnowledgeScheduler knowledgeScheduler;
  @Autowired MaintenanceFileRepository files;
  @Autowired JdbcTemplate db;

  @Test
  void mapperPreservesMetadataTenantAndSingleSelectBehavior() {
    assertEquals(
        0L,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_maintenance_file WHERE tenant_id=?", Long.class, TENANT));
    var record = new MaintenanceFile();
    record.setId(UUID.randomUUID().toString());
    record.setTenantId(TENANT);
    record.setUserId(9007199254740993L);
    record.setName("保养文档.pdf");
    record.setExtension(".pdf");
    record.setContentType("application/pdf");
    record.setSize(20971520L);
    files.insert(record);

    long before = selects();
    var original =
        db.queryForList(
            "SELECT * FROM eam_maintenance_file WHERE tenant_id=? AND id=?",
            TENANT,
            record.getId());
    assertEquals(1L, selects() - before);
    assertEquals("保养文档.pdf", original.getFirst().get("name"));
    before = selects();
    var row = files.find(TENANT, record.getId());
    assertEquals(1L, selects() - before);
    assertNotNull(row);
    assertEquals(record, row);
    assertNull(files.find(TENANT + 1, record.getId()));
    assertNull(files.find(TENANT, UUID.randomUUID().toString()));
    assertNotNull(
        db.queryForObject(
            "SELECT created_at FROM eam_maintenance_file WHERE tenant_id=? AND id=?",
            Timestamp.class,
            TENANT,
            record.getId()));
  }

  private long selects() {
    return db.queryForObject(
        "SHOW SESSION STATUS LIKE 'Com_select'", (row, index) -> Long.parseLong(row.getString(2)));
  }
}
