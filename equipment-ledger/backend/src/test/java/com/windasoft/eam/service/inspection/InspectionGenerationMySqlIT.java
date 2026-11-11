package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.repository.inspection.InspectionDefinitionStorage;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.repository.inspection.InspectionTaskJournalRepository;
import com.windasoft.eam.repository.system.NotificationRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 在真实事务、唯一约束及多连接下验证生成预算、续跑、事件幂等和失败恢复。 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "EAM_IDEA_QUALITY_TEST_URL", matches = ".+")
class InspectionGenerationMySqlIT {
  private static final long TENANT = 9911095501L;
  private static final long PLAN = 9911095502L;
  private static final long ARRANGEMENT = 9911095503L;
  private static final long USER = 9911095531L;
  private static final long ORGANIZATION = 9911095532L;
  private static final List<String> OWNED_TABLES =
      List.of(
          "eam_notify_message",
          "eam_inspection_inbox",
          "eam_inspection_event",
          "eam_inspection_task_person",
          "eam_inspection_task_equipment",
          "eam_inspection_task_project",
          "eam_inspection_task",
          "eam_inspection_schedule",
          "eam_inspection_daily_arrangement",
          "eam_inspection_daily_workday",
          "eam_inspection_daily_plan_person",
          "eam_inspection_daily_plan_device_person",
          "eam_inspection_daily_plan_device_project",
          "eam_inspection_daily_plan_device",
          "eam_inspection_daily_shift",
          "eam_inspection_daily_exemption",
          "eam_inspection_daily_plan",
          "eam_equipment",
          "eam_sys_user",
          "eam_org_unit");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    String url = System.getenv("EAM_IDEA_QUALITY_TEST_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:(3308/eam_idea_quality_test_20261030|3306/eam_engineering_test)(\\?.*)?")) {
      throw new IllegalStateException("任务生成测试只允许连接明确隔离的质量检查库");
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

  @MockitoBean InspectionProjectService projects;
  @MockitoBean InspectionNumbers numbers;
  @MockitoBean PermissionService permissions;
  @MockitoSpyBean NotificationRepository notifications;
  @MockitoBean NotificationScheduler notificationScheduler;
  @MockitoBean InspectionScheduler inspectionScheduler;
  @MockitoBean MaintenanceScheduler maintenanceScheduler;
  @MockitoBean InventoryReservationCleanup reservationCleanup;
  @MockitoBean RepairPlanScheduler repairScheduler;
  @MockitoBean KnowledgeScheduler knowledgeScheduler;
  @MockitoSpyBean InspectionRepository repository;
  @MockitoSpyBean InspectionTaskJournalRepository journal;
  @Autowired InspectionGenerationService generation;
  @Autowired InspectionDefinitionStorage definitions;
  @Autowired DailyArrangementRepository storage;
  @Autowired PlatformTransactionManager transactions;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  private final LocalDate day = now().toLocalDate().minusDays(2);
  private boolean ownsFixture;

  @BeforeEach
  void setup() {
    // 无外围测试事务：生成必须真正提交，两个线程才能看见同一计划并竞争实际行锁。
    // 清理前先证明专用租户为空，失败时不删除其他运行留下的记录。
    for (String table : OWNED_TABLES) assertEquals(0, count(table), table);
    ownsFixture = true;
    var sequence = new AtomicInteger();
    when(numbers.nextCode(eq(TENANT), anyString()))
        .thenAnswer(call -> "BUDGET-" + sequence.incrementAndGet());
    doReturn("测试部门").when(repository).findOrganizationName(eq(TENANT), any());
    when(permissions.companyOfOrganization(eq(TENANT), anyLong())).thenReturn(90001L);
    doReturn(true).when(notifications).replacesLegacy(eq(TENANT), anyString(), eq(PLAN));
    when(projects.equipment(eq(TENANT), anyLong()))
        .thenAnswer(
            call -> {
              long equipmentId = call.getArgument(1);
              return Map.of(
                  "id", equipmentId, "equipment_code", "EQ-" + equipmentId, "name", "测试设备");
            });
    when(projects.projects(eq(TENANT), anyMap(), eq(false)))
        .thenReturn(
            List.of(
                Map.of("id", "1:1", "name", "检查项目", "sourceStandardId", "1", "sourceItemId", "1")));
  }

  private void fixture(boolean arranged, int equipmentCount, int shiftCount) throws Exception {
    fixture(arranged, equipmentCount, shiftCount, Map.of());
  }

  private void fixture(
      boolean arranged, int equipmentCount, int shiftCount, Map<String, Object> options)
      throws Exception {
    var details = new ArrayList<Map<String, Object>>();
    for (int i = 1; i <= equipmentCount; i++)
      details.add(
          Map.of(
              "equipmentId",
              Integer.toString(i),
              "projectRefs",
              List.of(Map.of("standardId", "1", "itemId", "1"))));
    var shifts = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < shiftCount; i++)
      shifts.add(
          Map.of("id", Integer.toString(i), "name", "班次" + i, "start", "08:00", "end", "16:00"));
    var data = new LinkedHashMap<String, Object>();
    data.put("enabled", true);
    data.put("startDate", day.toString());
    data.put("endDate", day.toString());
    data.put("details", details);
    if (!arranged) data.put("shifts", shifts);
    data.putAll(options);
    String payload = json.writeValueAsString(data);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              definitions.insert(
                  PLAN, TENANT, "daily-plans", "BUDGET-PLAN", "预算计划", 90002L, 90003L, payload);
              repository.insertSchedule(
                  PLAN + 10, TENANT, PLAN, day.atStartOfDay(), day.atStartOfDay());
              if (arranged) {
                var group = new LinkedHashMap<String, Object>();
                group.put("id", Long.toString(ARRANGEMENT));
                group.put("name", "开放点检安排");
                group.put("frequency", "shift");
                group.put("personMode", "open");
                group.put("anchorDate", day.toString());
                group.put("startTime", "08:00");
                group.put("endTime", "16:00");
                group.put("slots", shifts);
                group.putAll(options);
                if (options.containsKey("owners")) group.put("personMode", "specified");
                storage.save(TENANT, PLAN, List.of(group), List.of());
                // 模拟已生效计划在服务恢复后的追赶，不把新建安排的当前生效时间当成历史生效时间。
                db.update(
                    "UPDATE eam_inspection_daily_arrangement SET effective_from=? WHERE tenant_id=?"
                        + " AND id=?",
                    day.atStartOfDay(),
                    TENANT,
                    ARRANGEMENT);
              }
            });
  }

  private long count(String table) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=?", Long.class, TENANT);
  }

  private LocalDateTime cursor() {
    return LocalDateTime.parse(
        db.queryForObject(
            "SELECT DATE_FORMAT(next_at,'%Y-%m-%dT%H:%i:%s') FROM eam_inspection_schedule WHERE"
                + " tenant_id=? AND plan_id=?",
            String.class, TENANT, PLAN));
  }

  private void assertCreated(long total) {
    assertEquals(total, count("eam_inspection_task"));
    assertEquals(total, count("eam_inspection_task_equipment"));
    assertEquals(total, count("eam_inspection_task_project"));
    assertEquals(
        total,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_inspection_event WHERE tenant_id=? AND action='生成任务'",
            Long.class,
            TENANT));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void boundedBatchesResumeAndDeletedOccurrencesRemainReserved(boolean arranged) throws Exception {
    fixture(arranged, 499, 5);
    assertEquals(2000, generation.generate(TENANT, PLAN));
    assertCreated(2000);
    assertEquals(day.atStartOfDay(), cursor());
    db.update(
        "UPDATE eam_inspection_task SET deleted=1 WHERE tenant_id=? ORDER BY id LIMIT 1", TENANT);
    assertEquals(495, generation.generate(TENANT, PLAN));
    assertCreated(2495);
    assertEquals(day.plusDays(1).atStartOfDay(), cursor());
    assertEquals(0, generation.generate(TENANT, PLAN));
    assertCreated(2495);
    assertEquals(
        arranged ? 2495L : 0L,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=? AND arrangement_id=?",
            Long.class,
            TENANT,
            ARRANGEMENT));
    assertEquals(0, generation.generate(TENANT + 1, PLAN), "不同租户不能推进此计划");
  }

  @Test
  void concurrentGenerationAndRetryDoNotDuplicateTasksOrEvents() throws Exception {
    fixture(true, 2, 1);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    var workers = Executors.newFixedThreadPool(2);
    try {
      Callable<Integer> run =
          () -> {
            ready.countDown();
            start.await();
            return generation.generate(TENANT, PLAN);
          };
      var first = workers.submit(run);
      var second = workers.submit(run);
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      assertEquals(2, first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS));
    } finally {
      // 即使就绪断言失败，也先释放门闩，避免测试线程永久等待而掩盖真实失败。
      start.countDown();
      workers.shutdownNow();
    }
    assertCreated(2);
    assertEquals(0, generation.generate(TENANT, PLAN));
    assertCreated(2);
  }

  @Test
  void eventFailureRollsBackBatchAndCursorThenRetrySucceeds() throws Exception {
    notificationUser();
    fixture(
        true,
        2,
        1,
        Map.of(
            "owners",
            List.of(Long.toString(USER)),
            "followUpUserId",
            Long.toString(USER),
            "allowLate",
            true));
    var events = new AtomicInteger();
    doAnswer(
            call -> {
              Object value = call.callRealMethod();
              if (events.incrementAndGet() == 2) throw new IllegalStateException("第二条任务的事件写入失败");
              return value;
            })
        .when(journal)
        .insertTaskEvent(anyLong(), eq(TENANT), anyLong(), any(), any(), any(), anyString());
    assertThrows(IllegalStateException.class, () -> generation.generate(TENANT, PLAN));
    for (String table :
        List.of(
            "eam_inspection_inbox",
            "eam_inspection_event",
            "eam_inspection_task_person",
            "eam_inspection_task_equipment",
            "eam_inspection_task_project",
            "eam_inspection_task")) assertEquals(0, count(table), table);
    assertEquals(day.atStartOfDay(), cursor());
    assertEquals(0, count("eam_notify_message"));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_notify_delivery d JOIN eam_notify_message m ON"
                + " m.id=d.message_id WHERE m.tenant_id=?",
            Long.class,
            TENANT));
    doAnswer(call -> call.callRealMethod())
        .when(journal)
        .insertTaskEvent(anyLong(), eq(TENANT), anyLong(), any(), any(), any(), anyString());
    assertEquals(2, generation.generate(TENANT, PLAN));
    assertCreated(2);
    assertEquals(2, count("eam_notify_message"));
  }

  private void notificationUser() {
    db.update(
        "INSERT INTO eam_org_unit(id,tenant_id,code,name,org_type)"
            + " VALUES(?,?,'NOTICE-ORG','通知测试部门','部门')",
        ORGANIZATION,
        TENANT);
    db.update(
        "INSERT INTO eam_sys_user(id,tenant_id,account,display_name,org_id)"
            + " VALUES(?,?,'notice-test','通知测试人员',?)",
        USER,
        TENANT,
        ORGANIZATION);
  }

  private long inbox(String kind) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_inspection_inbox WHERE tenant_id=? AND kind=?",
        Long.class,
        TENANT,
        kind);
  }

  @ParameterizedTest
  @CsvSource({"true,false", "true,true", "false,false", "false,true"})
  void initialNoticeOnlyQueuesExecutableTasks(boolean arranged, boolean allowLate)
      throws Exception {
    notificationUser();
    fixture(
        arranged,
        1,
        1,
        Map.of(
            "owners",
            List.of(Long.toString(USER)),
            "allowLate",
            allowLate,
            "followUpUserId",
            Long.toString(USER)));
    assertEquals(1, generation.generate(TENANT, PLAN));
    assertEquals(allowLate ? 1 : 0, inbox("点巡检待执行"));
    assertEquals(allowLate ? 1 : 0, count("eam_notify_message"));
    assertEquals(
        allowLate ? 3 : 0,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_notify_delivery d JOIN eam_notify_message m ON"
                + " m.id=d.message_id WHERE m.tenant_id=?",
            Long.class,
            TENANT));
    assertEquals(0, generation.generate(TENANT, PLAN));
    assertEquals(allowLate ? 1 : 0, inbox("点巡检待执行"));
  }

  @Test
  void openTaskWithoutFollowUpKeepsNullAndDoesNotNotifyUserZero() throws Exception {
    fixture(true, 1, 1);
    doReturn(false).when(notifications).replacesLegacy(eq(TENANT), anyString(), eq(PLAN));
    assertEquals(1, generation.generate(TENANT, PLAN));
    assertNull(
        db.queryForObject(
            "SELECT follow_up_user_id FROM eam_inspection_task WHERE tenant_id=?",
            Long.class,
            TENANT));
    assertEquals(0, count("eam_inspection_inbox"));
    assertEquals(0, count("eam_notify_message"));
  }

  @Test
  void historicalZeroFollowUpDoesNotBreakMaintenance() throws Exception {
    fixture(true, 1, 1);
    assertEquals(1, generation.generate(TENANT, PLAN));
    db.update("UPDATE eam_inspection_task SET follow_up_user_id=0 WHERE tenant_id=?", TENANT);
    doReturn(false).when(notifications).replacesLegacy(eq(TENANT), anyString(), eq(PLAN));
    assertEquals(0, generation.generate(TENANT, PLAN));
    assertEquals(0, count("eam_inspection_inbox"));
    assertEquals(0, count("eam_notify_message"));
  }

  @Test
  void validFollowUpStillReceivesOneOverdueNotice() throws Exception {
    notificationUser();
    fixture(true, 1, 1, Map.of("followUpUserId", Long.toString(USER)));
    doReturn(false).when(notifications).replacesLegacy(eq(TENANT), anyString(), eq(PLAN));
    assertEquals(1, generation.generate(TENANT, PLAN));
    assertEquals(1, inbox("点巡检超期提醒"));
    assertEquals(1, count("eam_notify_message"));
    assertEquals(0, generation.generate(TENANT, PLAN));
    assertEquals(1, inbox("点巡检超期提醒"));
  }

  @Test
  void exemptTaskNeverQueuesExecutionNotice() throws Exception {
    notificationUser();
    long equipment = PLAN + 60;
    db.update(
        "INSERT INTO eam_equipment(id,tenant_id,equipment_code,name,category,company,department)"
            + " VALUES(?,?,'NOTICE-EQ','通知测试设备','分类','公司','部门')",
        equipment,
        TENANT);
    fixture(
        true,
        1,
        1,
        Map.of(
            "owners",
            List.of(Long.toString(USER)),
            "followUpUserId",
            Long.toString(USER),
            "allowLate",
            true,
            "details",
            List.of(
                Map.of(
                    "equipmentId",
                    Long.toString(equipment),
                    "projectRefs",
                    List.of(Map.of("standardId", "1", "itemId", "1"))))));
    storage.addExemption(
        TENANT,
        PLAN + 40,
        PLAN,
        equipment,
        day.atStartOfDay().toString(),
        day.plusDays(1).atStartOfDay().toString(),
        "计划免检",
        USER);
    assertEquals(1, generation.generate(TENANT, PLAN));
    assertEquals(
        InspectionTaskStatus.EXEMPT.code(),
        db.queryForObject(
            "SELECT status FROM eam_inspection_task WHERE tenant_id=?", String.class, TENANT));
    assertEquals(0, count("eam_inspection_inbox"));
    assertEquals(0, count("eam_notify_message"));
  }

  @Test
  void executablePendingTaskStillQueuesOneExecutionNotice() throws Exception {
    notificationUser();
    fixture(
        false,
        1,
        1,
        Map.of("owners", List.of(Long.toString(USER)), "validHours", 7, "validUnit", "日"));
    assertEquals(1, generation.generate(TENANT, PLAN));
    assertEquals(1, inbox("点巡检待执行"));
    assertEquals(1, count("eam_notify_message"));
    assertEquals(0, generation.generate(TENANT, PLAN));
    assertEquals(1, inbox("点巡检待执行"));
  }

  @Test
  void futureTaskDoesNotQueueExecutionBeforeStart() throws Exception {
    notificationUser();
    var future = now().toLocalDate().plusDays(1).toString();
    fixture(
        false,
        1,
        1,
        Map.of(
            "owners",
            List.of(Long.toString(USER)),
            "startDate",
            future,
            "endDate",
            future,
            "advanceHours",
            72));
    assertEquals(1, generation.generate(TENANT, PLAN));
    assertEquals(
        InspectionTaskStatus.NOT_STARTED.code(),
        db.queryForObject(
            "SELECT status FROM eam_inspection_task WHERE tenant_id=?", String.class, TENANT));
    assertEquals(0, count("eam_inspection_inbox"));
    assertEquals(0, count("eam_notify_message"));
  }

  @AfterEach
  void cleanup() {
    if (!ownsFixture) return;
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              db.update(
                  "DELETE d FROM eam_notify_delivery d JOIN eam_notify_message m ON"
                      + " m.id=d.message_id WHERE m.tenant_id=?",
                  TENANT);
              for (String table : OWNED_TABLES)
                db.update("DELETE FROM " + table + " WHERE tenant_id=?", TENANT);
            });
    for (String table : OWNED_TABLES) assertEquals(0, count(table), table);
  }
}
