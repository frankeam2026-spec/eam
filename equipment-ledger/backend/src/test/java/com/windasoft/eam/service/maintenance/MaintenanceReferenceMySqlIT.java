package com.windasoft.eam.service.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.ApiExceptionHandler;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.common.NotificationMessages;
import com.windasoft.eam.controller.maintenance.MaintenanceController;
import com.windasoft.eam.controller.maintenance.MaintenanceStandardController;
import com.windasoft.eam.controller.maintenance.MaintenanceTypeController;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.knowledge.AiBusinessPlan;
import com.windasoft.eam.dto.maintenance.MaintenanceAssistantRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceAssistantResult;
import com.windasoft.eam.dto.maintenance.MaintenanceLegacyStockOption;
import com.windasoft.eam.dto.maintenance.MaintenanceListContext;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardRow;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardView;
import com.windasoft.eam.dto.maintenance.MaintenanceTypeRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceTypeView;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.dto.system.UserNameOption;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceDefinitionStorage;
import com.windasoft.eam.repository.maintenance.MaintenanceExecutionStorage;
import com.windasoft.eam.repository.maintenance.MaintenanceItemSpareRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTypeRepository;
import com.windasoft.eam.repository.system.NotificationRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.business.BusinessValueService;
import com.windasoft.eam.service.knowledge.AiBusinessQueryService;
import com.windasoft.eam.service.planning.PlanningSelectorService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.support.ContractTypes;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

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
          "eam_maintenance_standard",
          "eam_maintenance_type",
          "eam_spare_stock",
          "eam_maintenance_file",
          "eam_equipment",
          "eam_base_item",
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
  @MockitoSpyBean MaintenanceTypeRepository typeFacts;
  @MockitoSpyBean MaintenanceItemSpareRepository standardSpares;
  @MockitoSpyBean EquipmentQueryRepository equipmentFacts;
  @MockitoSpyBean NotificationRepository notifications;
  @Autowired PlanningSelectorService selectors;
  @Autowired MaintenanceListService lists;
  @Autowired MaintenanceTypeService typeService;
  @Autowired MaintenanceController maintenanceController;
  @Autowired MaintenanceStandardService standardService;
  @Autowired MaintenanceViewAssembler viewAssembler;
  @Autowired AiBusinessQueryService aiQueries;
  @Autowired MaintenanceFileService fileService;
  @Autowired MaintenanceProjectService projects;
  @Autowired MaintenanceGenerationService generation;
  @Autowired MaintenanceTaskJournal journal;
  @Autowired ObjectMapper json;
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
    assertEquals(
        0L,
        db.queryForObject(
            "SELECT COUNT(*) FROM sys_number_counter WHERE rule_code IN (?,?)",
            Long.class,
            "maintenance-" + TENANT,
            "maintenance-" + (TENANT + 1)));
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
    // 生成会写入专属租户的编号规则；与业务夹具一同清理，不能留下测试运行的递增状态。
    db.update(
        "DELETE FROM sys_number_counter WHERE rule_code IN (?,?)",
        "maintenance-" + TENANT,
        "maintenance-" + (TENANT + 1));
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
        .updateTask(anyString(), any(), anyString(), eq(TENANT), eq(TASK));
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

  @Test
  void journalPreservesMetadataSystemActorAndEventResponse() {
    pendingAcceptance();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              long before = updates();
              journal.notify(TENANT, TASK, List.of(USER), "保养待执行", "保养设备", true);
              System.out.println("maintenance_notify_updates=" + (updates() - before));
              journal.event(TENANT, TASK, "生成任务", null, "系统", Map.of("计划", "保养计划"));
            });
    var inbox = db.queryForMap("SELECT * FROM eam_maintenance_inbox WHERE tenant_id=?", TENANT);
    assertEquals(NotificationMessages.key("保养待执行"), inbox.get("message_key"));
    assertEquals(
        "保养设备",
        db.queryForObject(
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(message_args,'$.title')) FROM eam_maintenance_inbox"
                + " WHERE tenant_id=?",
            String.class,
            TENANT));
    assertNotNull(inbox.get("created_at"));
    assertEquals(1L, count("eam_notify_message"));
    assertEquals(
        null,
        db.queryForObject(
            "SELECT actor_id FROM eam_maintenance_event WHERE tenant_id=?", Long.class, TENANT));
    var event = json.valueToTree(tasks.task(TASK)).path("events").get(0);
    assertEquals(4, event.size());
    assertEquals("生成任务", event.path("action").asText());
    assertEquals("系统", event.path("actor").asText());
    assertTrue(event.path("time").asText().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    assertEquals(
        db.queryForObject(
            "SELECT DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') FROM eam_maintenance_event WHERE"
                + " tenant_id=?",
            String.class, TENANT),
        event.path("time").asText());
    assertEquals("保养计划", event.path("details").path("计划").asText());
  }

  @Test
  void inboxCompletionReadAndNoticeLookupKeepTheirDistinctScopes() {
    pendingAcceptance();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              journal.notify(TENANT, TASK, List.of(USER), "保养待执行", "设备", true);
              journal.notify(TENANT, TASK, List.of(DELETED), "保养已完成", "设备", false);
            });
    long own =
        db.queryForObject(
            "SELECT id FROM eam_maintenance_inbox WHERE tenant_id=? AND user_id=?",
            Long.class,
            TENANT,
            USER);
    long otherUser =
        db.queryForObject(
            "SELECT id FROM eam_maintenance_inbox WHERE tenant_id=? AND user_id=?",
            Long.class,
            TENANT,
            DELETED);
    long foreign = TASK + 500, otherTask = TASK + 501;
    db.update(
        "INSERT INTO eam_maintenance_inbox(id,tenant_id,task_id,user_id,kind,title,is_todo)"
            + " VALUES(?,?,?,?,'保养待执行','隔离',1),(?,?,?,?,'保养待执行','其他任务',1)",
        foreign,
        TENANT + 1,
        TASK,
        USER,
        otherTask,
        TENANT,
        TASK + 1,
        USER);
    definitions.read(otherUser);
    definitions.read(foreign);
    definitions.read(own);
    assertEquals(
        1L,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_maintenance_inbox WHERE id IN (?,?,?) AND read_at IS NOT"
                + " NULL",
            Long.class,
            own,
            otherUser,
            foreign));
    journal.closeTodos(TENANT, TASK);
    assertEquals(
        1,
        db.queryForObject("SELECT done FROM eam_maintenance_inbox WHERE id=?", Integer.class, own));
    assertEquals(
        0L,
        db.queryForObject(
            "SELECT SUM(done) FROM eam_maintenance_inbox WHERE id IN (?,?,?)",
            Long.class,
            otherUser,
            foreign,
            otherTask));
    assertTrue(journal.hasExecutionNotice(TENANT, TASK));
    assertEquals(false, journal.hasExecutionNotice(TENANT, TASK + 2));
  }

  @Test
  void taskDeletionCompletesAllMessagesAndPreservesExistingReadTime() {
    pendingAcceptance();
    db.update(
        "DELETE FROM eam_maintenance_execution WHERE tenant_id=? AND task_id=?", TENANT, TASK);
    db.update(
        "UPDATE eam_maintenance_task SET status='待执行',owner_id=NULL WHERE tenant_id=? AND id=?",
        TENANT,
        TASK);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              journal.notify(TENANT, TASK, List.of(USER), "保养待执行", "设备", true);
              journal.notify(TENANT, TASK, List.of(DELETED), "保养已完成", "设备", false);
            });
    db.update(
        "UPDATE eam_maintenance_inbox SET read_at='2001-01-01 00:00:00' WHERE tenant_id=? AND"
            + " user_id=?",
        TENANT,
        USER);
    var originalReadTime =
        db.queryForObject(
            "SELECT read_at FROM eam_maintenance_inbox WHERE tenant_id=? AND user_id=?",
            Timestamp.class,
            TENANT,
            USER);
    tasks.batchDelete(List.of(Map.of("id", TASK, "rowVersion", 0)));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT deleted FROM eam_maintenance_task WHERE tenant_id=? AND id=?",
            Integer.class,
            TENANT,
            TASK));
    assertEquals(
        2L,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_maintenance_inbox WHERE tenant_id=? AND task_id=? AND done=1"
                + " AND read_at IS NOT NULL",
            Long.class,
            TENANT,
            TASK));
    assertEquals(
        originalReadTime,
        db.queryForObject(
            "SELECT read_at FROM eam_maintenance_inbox WHERE tenant_id=? AND user_id=?",
            Timestamp.class,
            TENANT,
            USER));
  }

  @Test
  void captureFailureRollsBackLegacyMessagePublicMessageAndTask() {
    pendingAcceptance();
    doAnswer(
            call -> {
              call.callRealMethod();
              throw new IllegalStateException("公共通知捕获失败");
            })
        .when(notifications)
        .captureSource(eq("eam_maintenance_inbox"), eq(TENANT), anyLong());
    assertThrows(IllegalStateException.class, () -> tasks.action(TASK, "accept", accept()));
    assertEquals(0L, count("eam_maintenance_inbox"));
    assertEquals(0L, count("eam_notify_message"));
    assertEquals(
        "待验收",
        db.queryForObject(
            "SELECT status FROM eam_maintenance_task WHERE tenant_id=? AND id=?",
            String.class,
            TENANT,
            TASK));
  }

  @Test
  void stockChoicesKeepCompanyUnionHttpNumbersAndPagedOmission() throws Exception {
    ContractTypes.assertExplicit(MaintenanceLegacyStockOption.class);
    stocks();
    when(permissions.anyMaintenance("查看")).thenReturn(true);
    stockView("standards", new PermissionScope(false, ORG, Set.of(ORG), false));
    stockView("plans", new PermissionScope(false, ORG + 1, Set.of(), false));
    var mvc =
        MockMvcBuilders.standaloneSetup(new MaintenanceController(definitions))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    var response =
        mvc.perform(get("/api/v1/maintenance/choices").param("paged", "false"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var root = json.readTree(response.getContentAsByteArray());
    assertEquals(6, root.size());
    var options = root.path("stocks");
    assertEquals(2, options.size());
    var first = options.get(0);
    assertEquals(5, first.size());
    assertEquals("9007199254741661", first.path("id").asText());
    assertTrue(first.path("id").isTextual());
    assertEquals(" 轴承 ", first.path("name").asText());
    assertEquals(" M-1 ", first.path("model").asText());
    assertEquals("", options.get(1).path("model").asText());
    assertTrue(first.path("quantity").isNumber());
    assertEquals(0, new BigDecimal("0.125").compareTo(first.path("quantity").decimalValue()));
    selects(6, () -> definitions.choices(false));
    selects(
        3,
        () -> assertEquals(0, json.valueToTree(definitions.choices(true)).path("stocks").size()));
    stockView("plans", new PermissionScope(true, null, Set.of(), false));
    assertEquals(3, json.valueToTree(definitions.choices(false)).path("stocks").size());
    stockView("standards", new PermissionScope(false, null, Set.of(), true));
    stockView("plans", new PermissionScope(false, null, Set.of(ORG), false));
    assertEquals(0, json.valueToTree(definitions.choices(false)).path("stocks").size());
  }

  @Test
  void spareRequirementUsesWriteScopeAndPreservesSummaryAndQuantity() {
    stocks();
    definitionStorage.insert(TYPE, TENANT, "types", "STOCK-TYPE", "保养类型", ORG, USER, "{}");
    stockView("standards", new PermissionScope(false, ORG, Set.of(ORG), false));
    when(permissions.maintenance("standards", "新增"))
        .thenReturn(new PermissionScope(false, ORG + 1, Set.of(ORG), false));
    var denied =
        assertThrows(
            ResponseStatusException.class,
            () -> definitions.save("standards", null, spareStandard(9007199254741661L)));
    assertEquals(403, denied.getStatusCode().value());
    assertEquals(0L, count("eam_maintenance_standard"));
    when(permissions.maintenance("standards", "新增"))
        .thenReturn(new PermissionScope(false, ORG, Set.of(ORG), false));
    assertThrows(
        IllegalArgumentException.class,
        () -> definitions.save("standards", null, spareStandard(9007199254741663L)));
    var saved = definitions.save("standards", null, spareStandard(9007199254741661L));
    assertEquals(
        "轴承 · M-1 × 1.5", json.valueToTree(saved).path("items").get(0).path("spares").asText());
    assertEquals(
        0,
        new BigDecimal("1.50")
            .compareTo(
                db.queryForObject(
                    "SELECT quantity FROM eam_maintenance_item_spare WHERE tenant_id=?",
                    BigDecimal.class,
                    TENANT)));
    assertEquals(
        0,
        new BigDecimal("0.125")
            .compareTo(
                db.queryForObject(
                    "SELECT quantity FROM eam_spare_stock WHERE id=?",
                    BigDecimal.class,
                    9007199254741661L)));
  }

  @Test
  void ordinaryTextCannotGrantFileDownloadOrReferenceRights() {
    pendingAcceptance();
    db.update("UPDATE eam_maintenance_file SET user_id=? WHERE tenant_id=?", DELETED, TENANT);
    definitionStorage.insert(
        TYPE + 10,
        TENANT,
        "standards",
        "FILE-STANDARD",
        "标准",
        ORG,
        USER,
        jsonText(Map.of("remark", PDF)));
    executions.save(TENANT, TASK, jsonText(Map.of("description", PDF)));
    assertFalse(lists.hasFile(PDF), "备注和处理说明中的UUID不是附件关联");
    assertEquals(
        403,
        assertThrows(ResponseStatusException.class, () -> fileService.download(PDF))
            .getStatusCode()
            .value());
    assertEquals(
        403,
        assertThrows(
                ResponseStatusException.class,
                () -> validation.validAttachments(attachment(PDF), false))
            .getStatusCode()
            .value());
  }

  @Test
  void referenceValidationRejectsUnownedFileAndMismatchedId() {
    db.update("UPDATE eam_maintenance_file SET user_id=? WHERE id=?", DELETED, PDF);
    assertEquals(
        403,
        assertThrows(
                ResponseStatusException.class,
                () -> validation.validAttachments(attachment(PDF), false))
            .getStatusCode()
            .value());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            validation.validAttachments(
                List.of(Map.of("id", PDF, "url", "/api/v1/maintenance/files/" + IMAGE)), false));
  }

  @Test
  void exactFileRelationsRespectVisibilityAndKeepHistoricalSnapshots() {
    pendingAcceptance();
    db.update("UPDATE eam_maintenance_file SET user_id=? WHERE tenant_id=?", DELETED, TENANT);
    var item =
        Map.of("id", "file-item", "typeId", Long.toString(TYPE), "references", fileReference(PDF));
    definitionStorage.insert(
        TYPE + 10,
        TENANT,
        "standards",
        "FILE-STANDARD",
        "标准",
        ORG,
        USER,
        jsonText(Map.of("items", List.of(item))));
    assertTrue(lists.hasFile(PDF));
    validation.validAttachments(fileReference(PDF), false);
    when(permissions.maintenance("standards", "查看"))
        .thenReturn(new PermissionScope(false, null, Set.of(), false));
    assertFalse(lists.hasFile(PDF));
    db.update(
        "UPDATE eam_maintenance_task SET snapshot=? WHERE tenant_id=? AND id=?",
        jsonText(Map.of("items", List.of(item))),
        TENANT,
        TASK);
    assertTrue(lists.hasFile(PDF), "任务历史资料不依赖当前标准可见性");
    db.update("DELETE FROM eam_maintenance_standard WHERE tenant_id=?", TENANT);
    assertTrue(lists.hasFile(PDF), "删除标准不能破坏任务历史附件");
    db.update(
        "UPDATE eam_maintenance_task SET snapshot='{}' WHERE tenant_id=? AND id=?", TENANT, TASK);
    for (var result :
        List.of(
            Map.of("attachments", fileReference(PDF)),
            Map.of("acceptance", Map.of("attachments", fileReference(PDF))),
            Map.of("items", List.of(Map.of("id", "image", "images", fileReference(PDF)))))) {
      executions.save(TENANT, TASK, jsonText(result));
      assertTrue(lists.hasFile(PDF));
    }
    executions.save(TENANT, TASK, jsonText(Map.of("attachments", fileReference(FOREIGN_FILE))));
    assertThrows(IllegalArgumentException.class, () -> fileService.download(FOREIGN_FILE));
    executions.save(TENANT, TASK, jsonText(Map.of("attachments", fileReference(PDF))));
    db.update("DELETE FROM eam_maintenance_task_person WHERE tenant_id=?", TENANT);
    when(permissions.maintenance("tasks", "查看"))
        .thenReturn(new PermissionScope(false, null, Set.of(), false));
    assertFalse(lists.hasFile(PDF), "不能借另一授权范围下载任务文件");
  }

  @Test
  void referenceUsesUrlAuthorityRatherThanForgedIdOrName() {
    pendingAcceptance();
    var forged = Map.of("id", PDF, "url", "/api/v1/maintenance/files/" + IMAGE, "name", PDF);
    executions.save(TENANT, TASK, jsonText(Map.of("attachments", List.of(forged))));
    assertFalse(lists.hasFile(PDF));
    assertTrue(lists.hasFile(IMAGE));
    db.update(
        "UPDATE eam_maintenance_task SET snapshot=? WHERE tenant_id=? AND id=?",
        jsonText(Map.of("items", List.of(Map.of("references", List.of(forged))))),
        TENANT,
        TASK);
    assertFalse(lists.hasFile(PDF));
    assertTrue(lists.hasFile(IMAGE));
    executions.save(TENANT, TASK, "{}");
    db.update(
        "UPDATE eam_maintenance_task SET snapshot=? WHERE tenant_id=? AND id=?",
        jsonText(Map.of("items", List.of(Map.of("references", List.of(Map.of("id", PDF)))))),
        TENANT,
        TASK);
    assertTrue(lists.hasFile(PDF), "历史快照仅有ID时仍按明确附件字段匹配");
  }

  @Test
  void projectResolutionReadsOneStandardOnceAndPreservesOrderAndLegacyKeys() {
    definitionStorage.insert(TYPE, TENANT, "types", "PROJECT-TYPE", "类型", ORG, USER, "{}");
    var items = new ArrayList<Map<String, Object>>();
    var refs = new ArrayList<Map<String, Object>>();
    long standard = TYPE + 10;
    for (int i = 0; i < 100; i++) {
      items.add(Map.of("id", "item-" + i, "typeId", Long.toString(TYPE), "name", "项目" + i));
      refs.add(Map.of("standardId", Long.toString(standard), "itemId", "item-" + (99 - i)));
    }
    definitionStorage.insert(
        standard,
        TENANT,
        "standards",
        "PROJECT-STANDARD",
        "标准",
        ORG,
        USER,
        jsonText(Map.of("items", items)));
    var cycle = Map.<String, Object>of("typeId", Long.toString(TYPE), "projectRefs", refs);
    selects(
        1,
        () -> {
          var resolved = projects.planProjects(cycle, TENANT, new Equipment(), true);
          assertEquals(100, resolved.size());
          assertEquals(standard + ":item-99", resolved.getFirst().get("id"));
          assertEquals(standard + ":item-0", resolved.getLast().get("id"));
        });
    selects(
        2,
        () -> {
          var legacy =
              projects.planProjects(
                  Map.of("standardId", Long.toString(standard), "typeId", Long.toString(TYPE)),
                  TENANT,
                  new Equipment(),
                  true);
          assertEquals("item-0", legacy.getFirst().get("id"));
          assertEquals(100, legacy.size());
        });
    assertThrows(
        ResponseStatusException.class,
        () -> projects.planProjects(cycle, TENANT + 1, new Equipment(), false));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            projects.planProjects(
                Map.of("typeId", "999", "projectRefs", refs), TENANT, new Equipment(), false));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            projects.planProjects(
                Map.of(
                    "typeId",
                    Long.toString(TYPE),
                    "projectRefs",
                    List.of(Map.of("standardId", Long.toString(standard), "itemId", "missing"))),
                TENANT,
                new Equipment(),
                false));
    refs.add(refs.getFirst());
    assertEquals(100, projects.planProjects(cycle, TENANT, new Equipment(), true).size());
    when(permissions.maintenance("standards", "查看"))
        .thenReturn(new PermissionScope(false, null, Set.of(), false));
    assertThrows(
        ResponseStatusException.class,
        () -> projects.planProjects(cycle, TENANT, new Equipment(), true));
  }

  @Test
  void catchUpReusesFactsButKeepsDatesSnapshotsAndIdempotency() {
    pendingAcceptance();
    db.update(
        "UPDATE eam_maintenance_task SET due_date=? WHERE tenant_id=? AND id=?",
        LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(10),
        TENANT,
        TASK);
    long standard = TYPE + 10;
    definitionStorage.insert(
        standard,
        TENANT,
        "standards",
        "CATCH-UP",
        "生成标准",
        ORG,
        USER,
        jsonText(
            Map.of(
                "items",
                List.of(
                    Map.of("id", "first", "typeId", Long.toString(TYPE), "name", "项目一"),
                    Map.of("id", "second", "typeId", Long.toString(TYPE), "name", "项目二")))));
    var start = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(2);
    definitionStorage.device(
        false,
        TENANT,
        SCHEDULE,
        PLAN,
        EQUIPMENT,
        TYPE,
        start,
        start,
        0,
        jsonText(
            Map.of(
                "typeId",
                Long.toString(TYPE),
                "period",
                1,
                "unit",
                "日",
                "projectRefs",
                List.of(
                    Map.of("standardId", Long.toString(standard), "itemId", "second"),
                    Map.of("standardId", Long.toString(standard), "itemId", "first")))));
    clearInvocations(repository, equipmentFacts, typeFacts);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              long before = selectCount();
              assertEquals(3, generation.generate(TENANT, PLAN));
              long queries = selectCount() - before;
              System.out.println("保养补生成3次、每次2项目：实际Com_select=" + queries);
              assertEquals(22, queries);
            });
    verify(repository, times(1)).loadDefinition(TENANT, "standards", standard, false);
    verify(equipmentFacts, times(1)).findFacts(TENANT, EQUIPMENT);
    verify(typeFacts, times(1)).find(TENANT, TYPE, false);
    var generated =
        db.queryForList(
            "SELECT due_date,snapshot FROM eam_maintenance_task WHERE tenant_id=? AND id<>? ORDER"
                + " BY due_date",
            TENANT,
            TASK);
    assertEquals(3, generated.size());
    for (int i = 0; i < generated.size(); i++) {
      assertEquals(start.plusDays(i), ((Date) generated.get(i).get("due_date")).toLocalDate());
      var snapshot = projects.decode(generated.get(i).get("snapshot"));
      assertEquals(
          standard + ":second",
          json.valueToTree(snapshot).path("items").get(0).path("id").asText());
      assertEquals("保养设备", snapshot.get("equipmentName"));
    }
    assertEquals(0, generation.generate(TENANT, PLAN));
    assertEquals(4L, count("eam_maintenance_task"));
  }

  @Test
  void equipmentFactsKeepTenantDeletionNullsAndSingleRead() {
    pendingAcceptance();
    selects(
        1,
        () -> {
          var device = projects.equipment(TENANT, EQUIPMENT);
          assertEquals(EQUIPMENT, device.getId());
          assertEquals("MAINT-EQ", device.getEquipmentCode());
          assertEquals(null, device.getCompanyOrgId());
        });
    assertEquals(0, equipmentFacts.scopedFacts(TENANT, USER, List.of()).size());
    db.update(
        "UPDATE eam_equipment SET company_org_id=?,department_org_id=?,owner_user_id=? WHERE"
            + " tenant_id=? AND id=?",
        ORG,
        ORG + 1,
        USER,
        TENANT,
        EQUIPMENT);
    assertEquals(
        1,
        equipmentFacts
            .scopedFacts(TENANT, USER, List.of(new PermissionScope(false, ORG, Set.of(), false)))
            .size());
    assertEquals(
        1,
        equipmentFacts
            .scopedFacts(
                TENANT, USER, List.of(new PermissionScope(false, null, Set.of(ORG + 1), false)))
            .size());
    assertEquals(
        1,
        equipmentFacts
            .scopedFacts(TENANT, USER, List.of(new PermissionScope(false, null, Set.of(), true)))
            .size());
    assertEquals(
        0,
        equipmentFacts
            .scopedFacts(
                TENANT, DELETED, List.of(new PermissionScope(false, ORG + 99, Set.of(), true)))
            .size());
    assertEquals(
        0,
        equipmentFacts
            .scopedFacts(
                TENANT + 1, USER, List.of(new PermissionScope(true, null, Set.of(), false)))
            .size());
    assertThrows(IllegalArgumentException.class, () -> projects.equipment(TENANT + 1, EQUIPMENT));
    db.update("UPDATE eam_equipment SET deleted=1 WHERE tenant_id=? AND id=?", TENANT, EQUIPMENT);
    assertThrows(IllegalArgumentException.class, () -> projects.equipment(TENANT, EQUIPMENT));
  }

  @Test
  void sharedStandardSelectorKeepsCategoryAncestryAfterRepositoryChange() {
    pendingAcceptance();
    // 无类别限制的标准仍适用于可选设备；实际Service授权和类别父链SQL均执行。
    definitionStorage.insert(
        TYPE + 10, TENANT, "standards", "SELECT-STANDARD", "选择标准", ORG, USER, "{}");
    var selected = selectors.standards("maintenance", ListQuery.from(Map.of()), EQUIPMENT, null);
    assertEquals(1L, selected.page().total());
    assertEquals(Long.toString(TYPE + 10), selected.data().getFirst().get("id"));
  }

  @Test
  void organizationContextAndChoiceUsersKeepExistingFieldsAndActiveRules() {
    when(permissions.anyMaintenance("查看")).thenReturn(true);
    var choices = json.valueToTree(definitions.choices(false));
    var orgs = choices.path("organizations");
    assertEquals(1, orgs.size());
    assertEquals(Long.toString(ORG + 1), orgs.get(0).path("id").asText());
    assertEquals(Long.toString(ORG), orgs.get(0).path("parentId").asText());
    assertEquals(4, orgs.get(0).size());
    assertEquals("公司", orgs.get(0).path("type").asText());
    var people = choices.path("users");
    assertEquals(1, people.size());
    assertEquals(Long.toString(USER), people.get(0).path("id").asText());
    assertEquals("归属组织", people.get(0).path("department").asText());
    assertEquals(4, people.get(0).size());
    var context = lists.context("standards");
    assertEquals(1, context.organizations().size());
    assertEquals(Long.toString(ORG + 1), context.organizations().getFirst().id());
    ContractTypes.assertExplicit(MaintenanceListContext.class);
    ContractTypes.assertExplicit(UserNameOption.class);
  }

  @Test
  void assistantUsesTheSameVisibilityAndKeepsLegacyResponse() {
    pendingAcceptance();
    for (String domain :
        List.of(MaintenanceAssistantRequest.TASK_DOMAIN, MaintenanceAssistantRequest.PLAN_DOMAIN)) {
      var input = json.valueToTree(Map.of("domain", domain, "operation", "COUNT", "period", "all"));
      var request =
          new MaintenanceAssistantRequest(
              AiBusinessPlan.parse(input), null, 1, 20, LocalDateTime.now(), null, null);
      var answer = (MaintenanceAssistantResult.Count) lists.queryAssistant(request);
      assertEquals(0, BigDecimal.ONE.compareTo(answer.value()));
      assertEquals(1L, answer.total());
      var response = aiQueries.query(input, null, 1, 20);
      assertEquals(0, BigDecimal.ONE.compareTo((BigDecimal) response.get("value")));
      assertEquals(domain, ((AiBusinessPlan) response.get("plan")).domain());
      assertTrue(response.containsKey("conditions"));
      when(auth.tenantId()).thenReturn(TENANT + 1);
      assertEquals(0L, ((MaintenanceAssistantResult.Count) lists.queryAssistant(request)).total());
      when(auth.tenantId()).thenReturn(TENANT);
    }
    var input =
        json.valueToTree(Map.of("domain", "maintenanceTask", "operation", "LIST", "period", "all"));
    var request =
        new MaintenanceAssistantRequest(
            AiBusinessPlan.parse(input), null, 1, 20, LocalDateTime.now(), null, null);
    var table = (MaintenanceAssistantResult.Table) lists.queryAssistant(request);
    assertEquals(1L, table.total());
    assertEquals(Long.toString(TASK), table.data().getFirst().get("id"));
    assertTrue(table.columns().contains("equipmentName"));
    var none = new PermissionScope(false, null, Set.of(), false);
    when(permissions.maintenance("tasks", "查看")).thenReturn(none);
    // 原列表允许参与人查看，即使菜单范围为空；不能把AI查询简单收紧为只看公司权限。
    assertEquals(1L, ((MaintenanceAssistantResult.Table) lists.queryAssistant(request)).total());
    db.update("DELETE FROM eam_maintenance_task_person WHERE tenant_id=?", TENANT);
    assertEquals(0L, ((MaintenanceAssistantResult.Table) lists.queryAssistant(request)).total());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MaintenanceAssistantRequest(
                AiBusinessPlan.parse(
                    json.valueToTree(
                        Map.of("domain", "repair", "operation", "COUNT", "period", "all"))),
                null,
                1,
                20,
                LocalDateTime.now(),
                null,
                null));
  }

  @Test
  void typedMaintenanceTypesKeepLifecycleAndRejectStaleVersions() throws Exception {
    ContractTypes.assertExplicit(MaintenanceTypeRequest.class);
    ContractTypes.assertExplicit(MaintenanceTypeView.class);
    var created =
        typeService.save(
            null,
            new MaintenanceTypeRequest("TYPE-CODE", "类型", "", 2L, "月", "A", true, "说明", null));
    assertEquals(0, created.rowVersion());
    assertEquals(2, created.period());
    assertEquals("", created.departmentId());
    assertFalse(created.system());
    assertEquals(21, json.valueToTree(created).size());
    assertEquals(
        json.valueToTree(
            viewAssembler.definition(
                repository
                    .loadDefinition(TENANT, "types", Long.parseLong(created.id()), false)
                    .getFirst())),
        json.valueToTree(created));
    var updated =
        typeService.save(
            Long.parseLong(created.id()),
            new MaintenanceTypeRequest("TYPE-CODE", "类型改", "", 3L, "周", "", false, "", 0));
    assertEquals(1, updated.rowVersion());
    assertEquals("类型改", updated.name());
    assertFalse(updated.enabled());
    assertEquals(
        409,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    typeService.save(
                        Long.parseLong(created.id()),
                        new MaintenanceTypeRequest(
                            "TYPE-CODE", "覆盖", "", 3L, "周", "", true, "", 0)))
            .getStatusCode()
            .value());
    var mvc =
        MockMvcBuilders.standaloneSetup(new MaintenanceTypeController(typeService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    var response =
        mvc.perform(get("/api/v1/maintenance/types/" + created.id()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertEquals(
        created.id(),
        json.readTree(response.getContentAsByteArray()).path("data").path("id").asText());
    for (String invalid : List.of("0.5", "\"0\"", "true")) {
      assertThrows(
          com.fasterxml.jackson.core.JsonProcessingException.class,
          () -> json.readValue("{\"rowVersion\":" + invalid + "}", MaintenanceTypeRequest.class));
    }
    when(auth.tenantId()).thenReturn(TENANT + 1);
    assertEquals(
        404,
        assertThrows(
                ResponseStatusException.class, () -> typeService.get(Long.parseLong(created.id())))
            .getStatusCode()
            .value());
    when(auth.tenantId()).thenReturn(TENANT);
    typeService.delete(Long.parseLong(created.id()));
    assertEquals(0L, count("eam_maintenance_type"));
  }

  @Test
  void typedStandardsPreserveDetailsListsAndReplaceRelationsAtomically() throws Exception {
    ContractTypes.assertExplicit(MaintenanceStandardRequest.class);
    ContractTypes.assertExplicit(MaintenanceStandardView.class);
    ContractTypes.assertExplicit(MaintenanceStandardRow.class);
    stocks();
    definitionStorage.insert(TYPE, TENANT, "types", "STANDARD-TYPE", "类型", ORG, USER, "{}");
    db.update(
        "INSERT INTO eam_base_item(id,tenant_id,item_type,code,name,status)"
            + " VALUES(?,?,'CATEGORY','STANDARD-CATEGORY','实际分类',1)",
        TYPE + 20,
        TENANT);
    var raw = spareStandard(9007199254741661L);
    raw.put("categoryIds", List.of(Long.toString(TYPE + 20)));
    var initialItem = (Map<String, Object>) ((List<?>) raw.get("items")).getFirst();
    initialItem.put("valueType", "数值");
    initialItem.put("lower", "1.25");
    initialItem.put("upper", "9.50");
    var input = json.convertValue(raw, MaintenanceStandardRequest.class);
    var saved = standardService.save(null, input);
    assertEquals(1, saved.items().size());
    assertEquals(List.of("实际分类"), saved.categoryNames());
    assertEquals("轴承 · M-1 × 1.5", saved.items().getFirst().spares());
    long id = Long.parseLong(saved.id());
    // 旧公开协议作为独立对照，数值按实际JSON解析比较，避免BigDecimal和Double内存类型差异。
    assertEquals(
        json.readTree(
            json.writeValueAsString(
                viewAssembler.definition(
                    repository.loadDefinition(TENANT, "standards", id, false).getFirst()))),
        json.readTree(json.writeValueAsString(saved)));
    var page = standardService.query(ListQuery.from(Map.of("page", "1", "size", "20")), false);
    assertEquals(1L, page.page().total());
    assertEquals(saved.id(), page.data().getFirst().detail().id());
    assertFalse(json.valueToTree(page.data().getFirst()).has("owner"));
    assertEquals(1, standardService.all("[]").size());
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new MaintenanceStandardController(standardService), maintenanceController)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    var response =
        mvc.perform(get("/api/v1/maintenance/standards").param("page", "1").param("size", "20"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var http = json.readTree(response.getContentAsByteArray());
    assertEquals(saved.id(), http.path("data").get(0).path("id").asText());
    assertTrue(http.has("page"));
    var csv =
        mvc.perform(
                get("/api/v1/maintenance/standards/export").param("columns", "[\"code\",\"name\"]"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertTrue(csv.contains("STOCK-STANDARD"));
    mvc.perform(get("/api/v1/maintenance/standards/export.csv"))
        .andExpect(status().isTemporaryRedirect());
    var update = json.convertValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
    update.put("rowVersion", 0);
    var item = (Map<String, Object>) ((List<?>) update.get("items")).get(0);
    item.put("lower", "");
    item.put("upper", "");
    item.put("references", fileReference(PDF));
    item.put("spareRequirements", List.of());
    // 故障发生在旧备件已清理后，必须一起回滚主体版本、附件和备件，不能因批次执行提前提交。
    doAnswer(
            call -> {
              call.callRealMethod();
              throw new IllegalStateException("标准明细写入失败");
            })
        .when(standardSpares)
        .insertAll(org.mockito.ArgumentMatchers.anyList());
    assertThrows(
        IllegalStateException.class,
        () ->
            standardService.save(id, json.convertValue(update, MaintenanceStandardRequest.class)));
    assertEquals(0, standardService.get(id).rowVersion());
    assertEquals(0, standardService.get(id).items().getFirst().references().size());
    assertEquals(1, standardService.get(id).items().getFirst().spareRequirements().size());
    org.mockito.Mockito.doCallRealMethod()
        .when(standardSpares)
        .insertAll(org.mockito.ArgumentMatchers.anyList());
    var changed =
        standardService.save(id, json.convertValue(update, MaintenanceStandardRequest.class));
    assertEquals(1, changed.rowVersion());
    assertEquals("", changed.items().getFirst().lower());
    assertEquals("", changed.items().getFirst().upper());
    assertEquals(1, changed.items().getFirst().references().size());
    assertEquals(0, changed.items().getFirst().spareRequirements().size());
    assertEquals(
        0L,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_maintenance_item_spare WHERE tenant_id=?",
            Long.class,
            TENANT));
    assertEquals(
        409,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    standardService.save(
                        id, json.convertValue(update, MaintenanceStandardRequest.class)))
            .getStatusCode()
            .value());
    when(auth.tenantId()).thenReturn(TENANT + 1);
    assertEquals(
        404,
        assertThrows(ResponseStatusException.class, () -> standardService.get(id))
            .getStatusCode()
            .value());
    when(auth.tenantId()).thenReturn(TENANT);
    standardService.delete(id);
    assertEquals(0L, count("eam_maintenance_standard"));
    assertEquals(
        0L,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_maintenance_item_file WHERE tenant_id=?",
            Long.class,
            TENANT));
  }

  @Test
  void standardReferencedProjectAndTargetDepartmentRemainProtected() {
    stocks();
    definitionStorage.insert(TYPE, TENANT, "types", "REFERENCE-TYPE", "类型", ORG, USER, "{}");
    var body = spareStandard(9007199254741661L);
    var created =
        standardService.save(null, json.convertValue(body, MaintenanceStandardRequest.class));
    long standard = Long.parseLong(created.id());
    definitionStorage.insert(PLAN, TENANT, "plans", "REFERENCE-PLAN", "计划", ORG, USER, "{}");
    var date = LocalDate.of(2026, 11, 16);
    definitionStorage.device(
        true,
        TENANT,
        SCHEDULE,
        PLAN,
        EQUIPMENT,
        TYPE,
        date,
        date,
        0,
        jsonText(
            Map.of(
                "projectRefs",
                List.of(Map.of("standardId", created.id(), "itemId", "stock-item")))));
    body.put("rowVersion", 0);
    var item = (Map<String, Object>) ((List<?>) body.get("items")).getFirst();
    item.put("id", "replacement");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            standardService.save(
                standard, json.convertValue(body, MaintenanceStandardRequest.class)));
    assertEquals(0, standardService.get(standard).rowVersion());
    assertEquals("stock-item", standardService.get(standard).items().getFirst().id());
    assertThrows(IllegalArgumentException.class, () -> standardService.delete(standard));
    item.put("id", "stock-item");
    body.put("departmentId", Long.toString(ORG + 1));
    when(permissions.maintenance("standards", "编辑"))
        .thenReturn(new PermissionScope(false, null, Set.of(ORG), false));
    assertEquals(
        403,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    standardService.save(
                        standard, json.convertValue(body, MaintenanceStandardRequest.class)))
            .getStatusCode()
            .value());
    assertEquals(Long.toString(ORG), standardService.get(standard).departmentId());
    db.update(
        "UPDATE eam_maintenance_plan_device SET enabled=0 WHERE tenant_id=? AND id=?",
        TENANT,
        SCHEDULE);
    standardService.delete(standard);
    assertEquals(0L, count("eam_maintenance_standard"));
  }

  @Test
  void repeatedProjectFactsAreReadOncePerStandardSave() {
    stocks();
    definitionStorage.insert(TYPE, TENANT, "types", "PROFILE-TYPE", "类型", ORG, USER, "{}");
    var body = spareStandard(9007199254741661L);
    var prepared = new ArrayList<Map<String, Object>>();
    var template = ((List<Map<String, Object>>) body.get("items")).getFirst();
    for (int i = 0; i < 50; i++) {
      var row = new LinkedHashMap<>(template);
      row.put("id", "profile-" + i);
      prepared.add(row);
    }
    // 旧实现103次基线已记录；删除重复规则后，仅约束新链路的真实数据库开销。
    body.put("items", prepared);
    body.put("code", "PROFILE-TYPED");
    clearInvocations(typeFacts);
    selects(
        15,
        () ->
            standardService.save(null, json.convertValue(body, MaintenanceStandardRequest.class)));
    verify(typeFacts, times(1)).find(TENANT, TYPE, true);
  }

  private String jsonText(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new AssertionError(e);
    }
  }

  private static List<Map<String, Object>> fileReference(String id) {
    return List.of(Map.of("id", id, "url", "/api/v1/maintenance/files/" + id));
  }

  private Map<String, Object> spareStandard(long stock) {
    return json.convertValue(
        Map.of(
            "name",
            "备件标准",
            "code",
            "STOCK-STANDARD",
            "departmentId",
            Long.toString(ORG),
            "items",
            List.of(
                Map.of(
                    "id",
                    "stock-item",
                    "code",
                    "stock-item",
                    "name",
                    "检查",
                    "content",
                    "检查部位",
                    "typeId",
                    Long.toString(TYPE),
                    "valueType",
                    "文本",
                    "spareRequirements",
                    List.of(Map.of("stockId", Long.toString(stock), "quantity", "1.500"))))),
        new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  private void stockView(String kind, PermissionScope scope) {
    String menu = PermissionService.maintenanceMenu(kind);
    when(permissions.allowed(menu, "查看")).thenReturn(true);
    when(permissions.maintenance(kind, "查看")).thenReturn(scope);
    when(permissions.companyAllowed(eq(menu), eq("查看"), anyLong()))
        .thenAnswer(call -> scope.all() || scope.companyIds().contains(call.getArgument(2)));
  }

  private void stocks() {
    db.update(
        "INSERT INTO eam_spare_stock(id,tenant_id,company_id,code,name,model,quantity)"
            + " VALUES(?,?,?,'STOCK-A',' 轴承 ',' M-1"
            + " ',0.125),(?,?,?,'STOCK-B','油杯','',2),(?,?,?,'STOCK-F','外租户','',1),(?,?,?,'STOCK-N','禁止','',0)",
        9007199254741661L,
        TENANT,
        ORG,
        9007199254741662L,
        TENANT,
        ORG + 1,
        9007199254741663L,
        TENANT + 1,
        ORG + 3,
        9007199254741664L,
        TENANT,
        ORG + 99);
  }

  private long updates() {
    return db.queryForObject(
        "SHOW SESSION STATUS LIKE 'Com_update'", (row, index) -> Long.parseLong(row.getString(2)));
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
