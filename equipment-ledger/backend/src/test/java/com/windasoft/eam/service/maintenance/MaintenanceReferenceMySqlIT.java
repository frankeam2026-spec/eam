package com.windasoft.eam.service.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.maintenance.MaintenanceDefinitionStorage;
import com.windasoft.eam.repository.maintenance.MaintenanceExecutionStorage;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.business.BusinessValueService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 真实读写保养与履历数据，固定身份和权限；不启动渠道发送，不使用外围测试事务掩盖回滚。 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "EAM_IDEA_QUALITY_TEST_URL", matches = ".+")
class MaintenanceReferenceMySqlIT {
  private static final long TENANT = 9911126201L;
  private static final long ORG = 9911126211L;
  private static final long USER = 9911126221L;
  private static final long INACTIVE = USER + 1;
  private static final long DELETED = USER + 2;
  private static final long FOREIGN = USER + 3;
  private static final long TASK = 9911126231L;
  private static final long EQUIPMENT = TASK + 1;
  private static final long PLAN = TASK + 2;
  private static final long TYPE = TASK + 3;
  private static final long SCHEDULE = TASK + 4;
  private static final String PDF = "99111262-0000-0000-0000-000000000001";
  private static final String IMAGE = "99111262-0000-0000-0000-000000000002";
  private static final String FOREIGN_FILE = "99111262-0000-0000-0000-000000000003";
  private static final List<String> TABLES =
      List.of(
          "eam_notify_message",
          "eam_maintenance_inbox",
          "eam_maintenance_event",
          "eam_equipment_history",
          "eam_maintenance_task",
          "eam_maintenance_plan_device",
          "eam_maintenance_plan",
          "eam_maintenance_type",
          "eam_maintenance_file",
          "eam_equipment",
          "eam_sys_user",
          "eam_org_unit",
          "inv_tenant_lock");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    String url = System.getenv("EAM_IDEA_QUALITY_TEST_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:(3308/eam_idea_quality_test_20261030|3306/eam_engineering_test)(\\?.*)?")) {
      throw new IllegalStateException("保养引用测试仅允许连接明确隔离的质量检查库");
    }
    properties.add("spring.datasource.url", () -> url);
    properties.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_USER", "root"));
    properties.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_PASSWORD", ""));
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean BusinessValueService extensions;
  @MockitoBean MaintenanceScheduler maintenanceScheduler;
  @MockitoBean InspectionScheduler inspectionScheduler;
  @MockitoBean NotificationScheduler notificationScheduler;
  @MockitoBean InventoryReservationCleanup reservationCleanup;
  @MockitoBean RepairPlanScheduler repairScheduler;
  @MockitoBean KnowledgeScheduler knowledgeScheduler;
  @MockitoSpyBean MaintenanceRepository repository;
  @Autowired MaintenanceValidationService validation;
  @Autowired MaintenanceService definitions;
  @Autowired MaintenanceTaskService tasks;
  @Autowired MaintenanceDefinitionStorage definitionStorage;
  @Autowired MaintenanceExecutionStorage executions;
  @Autowired JdbcTemplate db;
  @Autowired PlatformTransactionManager transactionManager;
  private boolean ownsFixture;

  @BeforeEach
  void setup() {
    for (String table : TABLES) assertEquals(0L, count(table), table);
    ownsFixture = true;
    when(auth.tenantId()).thenReturn(TENANT);
    when(auth.userId()).thenReturn(USER);
    var current = new SystemUser();
    current.setId(USER);
    current.setDisplayName("验收人");
    when(auth.user()).thenReturn(current);
    var all = new PermissionScope(true, null, Set.of(), false);
    when(permissions.maintenance(anyString())).thenReturn(all);
    when(permissions.maintenance(anyString(), anyString())).thenReturn(all);
    for (int i = 0; i < 4; i++) {
      db.update(
          "INSERT INTO eam_org_unit(id,tenant_id,name,code,org_type,status,deleted,parent_id)"
              + " VALUES(?,?,?,?,?,?,?,?)",
          ORG + i,
          i == 3 ? TENANT + 1 : TENANT,
          "归属组织",
          "MAINT-ORG-" + i,
          "公司",
          i == 0 ? 0 : 1,
          i == 2 ? 1 : 0,
          i == 0 || i == 3 ? null : ORG);
    }
    for (int i = 0; i < 4; i++) {
      db.update(
          "INSERT INTO eam_sys_user(id,tenant_id,account,display_name,org_id,status,deleted)"
              + " VALUES(?,?,?,?,?,?,?)",
          USER + i,
          i == 3 ? TENANT + 1 : TENANT,
          "maint-" + i,
          i == 2 ? "历史执行人" : "保养人员",
          ORG + (i == 3 ? 3 : 0),
          i == 1 ? 0 : 1,
          i == 2 ? 1 : 0);
    }
    for (String id : List.of(PDF, IMAGE, FOREIGN_FILE)) {
      db.update(
          "INSERT INTO eam_maintenance_file(id,tenant_id,user_id,name,extension,content_type,size)"
              + " VALUES(?,?,?,?,?,?,1)",
          id,
          id.equals(FOREIGN_FILE) ? TENANT + 1 : TENANT,
          USER,
          "附件",
          id.equals(IMAGE) ? ".JPG" : ".pdf",
          "application/octet-stream");
    }
  }

  @AfterEach
  void cleanup() {
    if (!ownsFixture) return;
    // 仅删除本用例证明为空后创建的两个专属租户；渠道子记录必须先于消息删除。
    db.update(
        "DELETE d FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id WHERE"
            + " m.tenant_id IN (?,?)",
        TENANT,
        TENANT + 1);
    for (String table : TABLES) {
      if (table.equals("eam_org_unit"))
        db.update(
            "DELETE FROM eam_org_unit WHERE tenant_id=? AND id IN (?,?)", TENANT, ORG + 1, ORG + 2);
      db.update("DELETE FROM " + table + " WHERE tenant_id IN (?,?)", TENANT, TENANT + 1);
    }
  }

  @Test
  void participantValidationPreservesActiveUserAndTenantRules() {
    selects(1, () -> validation.validUsers(List.of(Long.toString(USER)), true));
    validation.validUsers(List.of(), false);
    assertThrows(IllegalArgumentException.class, () -> validation.validUsers(List.of(), true));
    for (long id : List.of(INACTIVE, DELETED, FOREIGN, 1L)) {
      assertThrows(
          IllegalArgumentException.class,
          () -> validation.validUsers(List.of(Long.toString(id)), true));
    }
  }

  @Test
  void attachmentValidationPreservesExistenceImageAndDatabaseCollationRules() {
    selects(1, () -> validation.validAttachments(attachment(PDF), false));
    selects(2, () -> validation.validAttachments(attachment(IMAGE), true));
    assertThrows(
        IllegalArgumentException.class, () -> validation.validAttachments(attachment(PDF), true));
    assertThrows(
        IllegalArgumentException.class,
        () -> validation.validAttachments(attachment(FOREIGN_FILE), false));
    assertThrows(
        IllegalArgumentException.class,
        () -> validation.validAttachments(List.of(Map.of("url", "https://external/file")), false));
  }

  @Test
  void departmentExistenceDoesNotAddStatusOrOrganizationTypeRestrictions() {
    var body = Map.<String, Object>of("name", "标准", "departmentId", Long.toString(ORG));
    var itemsError =
        assertThrows(
            IllegalArgumentException.class, () -> definitions.save("standards", null, body));
    assertEquals(MessageContext.text("server.maintenance.message0023"), itemsError.getMessage());
    for (long org : List.of(ORG + 2, ORG + 3, 1L)) {
      var missing =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  definitions.save(
                      "standards", null, Map.of("name", "标准", "departmentId", Long.toString(org))));
      assertEquals(MessageContext.text("server.maintenance.message0021"), missing.getMessage());
    }
  }

  @Test
  void historicalNamesIncludeInactiveAndDeletedUsersButNeverOtherTenants() {
    selects(1, () -> assertEquals("历史执行人", tasks.userName(TENANT, DELETED)));
    assertEquals("保养人员", tasks.userName(TENANT, INACTIVE));
    assertEquals("", tasks.userName(TENANT, FOREIGN));
    assertEquals("", tasks.userName(TENANT, null));
  }

  @Test
  void acceptanceWritesOneHistoryAndCapturesNotificationWithinTheSameTransaction() {
    pendingAcceptance();
    Timestamp before = db.queryForObject("SELECT NOW()", Timestamp.class);
    var result = tasks.action(TASK, "accept", accept());
    Timestamp after = db.queryForObject("SELECT NOW()", Timestamp.class);
    assertEquals("已完成", result.get("status"));
    var history = db.queryForMap("SELECT * FROM eam_equipment_history WHERE tenant_id=?", TENANT);
    assertEquals(EQUIPMENT, ((Number) history.get("equipment_id")).longValue());
    assertEquals("MAINTENANCE", history.get("record_type"));
    assertEquals("MAINT-REFERENCE", history.get("record_no"));
    assertEquals("保养处理记录", history.get("description"));
    assertEquals("历史执行人", history.get("operator_name"));
    assertEquals("已完成", history.get("status"));
    assertEquals("验收说明", history.get("result"));
    var at =
        db.queryForObject(
            "SELECT occurred_at FROM eam_equipment_history WHERE tenant_id=?",
            Timestamp.class,
            TENANT);
    assertNotNull(at);
    assertTrue(!at.before(before) && !at.after(after));
    assertEquals(1, result.get("rowVersion"));
    assertEquals(1L, count("eam_notify_message"));
    assertEquals(
        3L,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_notify_delivery d JOIN eam_notify_message m ON"
                + " m.id=d.message_id WHERE m.tenant_id=?",
            Long.class,
            TENANT));
    assertEquals(1L, count("eam_maintenance_event"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            tasks.action(
                TASK, "accept", Map.of("rowVersion", 1, "decision", "accept", "score", 3)));
    assertEquals(1L, count("eam_equipment_history"));
  }

  @Test
  void laterTaskWriteFailureRollsBackHistoryStateAndNotification() {
    pendingAcceptance();
    doAnswer(
            call -> {
              assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
              call.callRealMethod();
              throw new IllegalStateException("任务保存后失败");
            })
        .when(repository)
        .updateTask(any(), any(), anyString(), anyString(), eq(TENANT), eq(TASK));
    assertThrows(IllegalStateException.class, () -> tasks.action(TASK, "accept", accept()));
    assertEquals(
        "待验收",
        db.queryForObject(
            "SELECT status FROM eam_maintenance_task WHERE tenant_id=? AND id=?",
            String.class,
            TENANT,
            TASK));
    assertEquals(0L, count("eam_equipment_history"));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT row_version FROM eam_maintenance_task WHERE tenant_id=? AND id=?",
            Integer.class,
            TENANT,
            TASK));
    assertEquals(0L, count("eam_notify_message"));
    assertEquals(0L, count("eam_maintenance_inbox"));
    assertEquals(0L, count("eam_maintenance_event"));
  }

  private void pendingAcceptance() {
    db.update(
        "INSERT INTO eam_equipment(id,tenant_id,equipment_code,name,category,company,department)"
            + " VALUES(?,?,?,?,'分类','公司','部门')",
        EQUIPMENT,
        TENANT,
        "MAINT-EQ",
        "保养设备");
    definitionStorage.insert(TYPE, TENANT, "types", "MAINT-TYPE", "保养类型", ORG, USER, "{}");
    definitionStorage.insert(PLAN, TENANT, "plans", "MAINT-PLAN", "保养计划", ORG, USER, "{}");
    definitionStorage.device(
        true,
        TENANT,
        SCHEDULE,
        PLAN,
        EQUIPMENT,
        TYPE,
        LocalDate.now(),
        LocalDate.now(),
        0,
        "{\"projectRefs\":[]}");
    db.update(
        "INSERT INTO"
            + " eam_maintenance_task(id,tenant_id,code,plan_id,schedule_id,equipment_id,type_id,due_date,owner_id,status,snapshot)"
            + " VALUES(?,?,?,?,?,?,?,CURRENT_DATE,?,'待验收',?)",
        TASK,
        TENANT,
        "MAINT-REFERENCE",
        PLAN,
        SCHEDULE,
        EQUIPMENT,
        TYPE,
        DELETED,
        "{\"reviewMode\":\"整机验收\",\"calculation\":\"固定间隔\",\"equipmentName\":\"保养设备\",\"items\":[]}");
    db.update(
        "INSERT INTO eam_maintenance_task_person VALUES(?,?,'owner',?,0),(?,?,'reviewer',?,0)",
        TENANT,
        TASK,
        DELETED,
        TENANT,
        TASK,
        USER);
    executions.save(
        TENANT,
        TASK,
        "{\"description\":\"保养处理记录\",\"startAt\":\"2026-11-01T08:00:00\",\"endAt\":\"2026-11-01T09:00:00\"}");
  }

  private static List<Map<String, Object>> attachment(String id) {
    return List.of(Map.of("url", "/api/v1/maintenance/files/" + id));
  }

  private static Map<String, Object> accept() {
    return Map.of("rowVersion", 0, "decision", "accept", "score", 3, "description", "验收说明");
  }

  private long count(String table) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE tenant_id IN (?,?)",
        Long.class,
        TENANT,
        TENANT + 1);
  }

  private void selects(long expected, Runnable action) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              long before = selectCount();
              action.run();
              assertEquals(expected, selectCount() - before);
            });
  }

  private long selectCount() {
    return db.queryForObject(
        "SHOW SESSION STATUS LIKE 'Com_select'", (row, index) -> Long.parseLong(row.getString(2)));
  }
}
