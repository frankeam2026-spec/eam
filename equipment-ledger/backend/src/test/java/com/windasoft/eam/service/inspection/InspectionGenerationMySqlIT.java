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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import com.windasoft.eam.dto.inspection.InspectionGenerationTarget;
import com.windasoft.eam.dto.system.PermissionScope;
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
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.web.server.ResponseStatusException;

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
  @Autowired InspectionTaskLifecycleService lifecycle;
  @Autowired InspectionDefinitionStorage definitions;
  @MockitoSpyBean DailyArrangementRepository storage;
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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void overnightSnapshotAndArrangementMetadataKeepTheirExistingMeaning(boolean arranged)
      throws Exception {
    fixture(arranged, 1, 1, Map.of("useShiftEnd", true, "onlyAbnormal", true));
    db.update(
        "UPDATE "
            + (arranged ? "eam_inspection_daily_arrangement_slot" : "eam_inspection_daily_shift")
            + " SET start_time='23:30:00',end_time='07:15:00' WHERE tenant_id=?",
        TENANT);
    assertEquals(1, generation.generate(TENANT, PLAN));
    var task = db.queryForMap("SELECT * FROM eam_inspection_task WHERE tenant_id=?", TENANT);
    verify(storage).exemptions(TENANT, PLAN);
    var snapshot =
        json.readTree(
            repository
                .loadTask(TENANT, ((Number) task.get("id")).longValue(), false)
                .getFirst()
                .get("snapshot")
                .toString());
    assertEquals("班次0", snapshot.path("shiftName").asText());
    assertEquals(day.toString(), snapshot.path("shiftDate").asText());
    assertEquals(arranged ? "现场直接点检" : "指定负责人", snapshot.path("personMode").asText());
    assertEquals(!arranged, snapshot.path("onlyAbnormal").asBoolean());
    assertEquals(false, snapshot.path("allowLate").asBoolean());
    assertEquals("测试设备", snapshot.path("equipmentName").asText());
    assertEquals("1/1:1", snapshot.path("items").get(0).path("id").asText());
    assertEquals("1", snapshot.path("equipment").get(0).path("id").asText());
    snapshot.fieldNames().forEachRemaining(key -> assertTrue(!key.startsWith("_"), key));
    assertTrue(
        !snapshot.has("arrangements") && !snapshot.has("details") && !snapshot.has("roster"));
    assertEquals(arranged ? ARRANGEMENT : null, task.get("arrangement_id"));
    assertEquals(arranged ? Date.valueOf(day) : null, task.get("business_date"));
    assertEquals(arranged ? ARRANGEMENT + ":0" : "0", task.get("shift_id"));
    assertEquals(
        day.atTime(23, 30).toString(),
        db.queryForObject(
            "SELECT DATE_FORMAT(due_at,'%Y-%m-%dT%H:%i') FROM eam_inspection_task WHERE"
                + " tenant_id=?",
            String.class, TENANT));
    assertEquals(
        day.plusDays(1).atTime(7, 15).toString(),
        db.queryForObject(
            "SELECT DATE_FORMAT(expires_at,'%Y-%m-%dT%H:%i') FROM eam_inspection_task WHERE"
                + " tenant_id=?",
            String.class, TENANT));
  }

  @Test
  void rosterUsesOriginalShiftKeyRatherThanPrefixedTaskKey() throws Exception {
    notificationUser();
    fixture(
        true,
        1,
        1,
        Map.of(
            "personMode",
            "roster",
            "allowLate",
            true,
            "dateRoster",
            List.of(
                Map.of(
                    "key",
                    "day-shift",
                    "date",
                    day.toString(),
                    "slotId",
                    "0",
                    "start",
                    "00:00",
                    "end",
                    "00:00",
                    "unassigned",
                    false,
                    "users",
                    List.of(Long.toString(USER))))));
    assertEquals(1, generation.generate(TENANT, PLAN));
    assertEquals(
        USER,
        db.queryForObject(
            "SELECT user_id FROM eam_inspection_task_person WHERE tenant_id=? AND"
                + " role_code='owner'",
            Long.class,
            TENANT));
    assertEquals(
        ARRANGEMENT + ":0",
        db.queryForObject(
            "SELECT shift_id FROM eam_inspection_task WHERE tenant_id=?", String.class, TENANT));
    assertEquals(1, inbox("点巡检待执行"));
  }

  @Test
  void previewRejectsAnotherCreatorsPlanEvenWhenRequestedEquipmentIsOwned() throws Exception {
    fixture(true, 1, 1);
    when(permissions.inspection("daily-plans", "编辑"))
        .thenReturn(new PermissionScope(false, null, Set.of(), true));
    var body = previewDraft();
    var previewService = previewService(TENANT);
    var error =
        assertThrows(ResponseStatusException.class, () -> previewService.dailyPreview(body));
    assertEquals(403, error.getStatusCode().value());
    assertCreated(0);
  }

  @Test
  void previewCannotResolvePlanFromAnotherTenant() throws Exception {
    fixture(true, 1, 1);
    when(permissions.inspection("daily-plans", "编辑"))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    var body = previewDraft();
    var previewService = previewService(TENANT + 1);
    var error =
        assertThrows(ResponseStatusException.class, () -> previewService.dailyPreview(body));
    assertEquals(404, error.getStatusCode().value());
    assertCreated(0);
  }

  @Test
  void permittedDraftPreviewIsReadOnlyAndDoesNotAdvanceSavedPlan() throws Exception {
    fixture(true, 1, 1);
    when(permissions.inspection("daily-plans", "编辑"))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    var body = previewDraft();
    String before = json.writeValueAsString(body);
    var previewService = previewService(TENANT);
    var tx = new TransactionTemplate(transactions);
    tx.setReadOnly(true);
    var result = tx.execute(status -> previewService.dailyPreview(body));
    assertEquals(7L, result.get("total"));
    verify(repository).loadDefinition(TENANT, "daily-plans", PLAN, false);
    assertEquals(0L, result.get("unstaffed"), "开放点检没有指定人员不属于缺人");
    assertEquals(before, json.writeValueAsString(body), "预览校验不能改写编辑中的请求对象");
    assertEquals(day.atStartOfDay(), cursor());
    assertCreated(0);
    assertEquals(0, count("eam_inspection_inbox"));
    assertEquals(0, count("eam_notify_message"));
  }

  private Map<String, Object> previewDraft() {
    var tomorrow = now().toLocalDate().plusDays(1);
    var body = new LinkedHashMap<String, Object>();
    body.put("id", Long.toString(PLAN));
    body.put("departmentId", "90002");
    body.put("startDate", tomorrow.toString());
    body.put("endDate", tomorrow.plusDays(6).toString());
    body.put(
        "details",
        List.of(
            Map.of(
                "equipmentId",
                "1",
                "projectRefs",
                List.of(Map.of("standardId", "1", "itemId", "1")))));
    body.put("arrangements", storage.load(TENANT, PLAN));
    return body;
  }

  /** 原计划查询与范围判断使用真实 Repository/PermissionScope，设备及标准资料沿用本类的受控替身。 */
  private DailyInspectionPlanService previewService(long tenant) {
    var caller = mock(AuthContext.class);
    when(caller.tenantId()).thenReturn(tenant);
    when(caller.userId()).thenReturn(USER);
    var actualProjects = new InspectionProjectService(repository, json, caller, permissions);
    when(projects.raw("daily-plans", tenant, PLAN, false))
        .thenAnswer(call -> actualProjects.raw("daily-plans", tenant, PLAN, false));
    when(projects.scoped(anyMap(), eq("编辑")))
        .thenAnswer(call -> actualProjects.scoped(call.getArgument(0), "编辑"));
    when(projects.equipment(tenant, 1L)).thenReturn(Map.of("id", 1L, "owner_user_id", USER));
    return new DailyInspectionPlanService(
        caller,
        permissions,
        projects,
        new InspectionValidationService(repository, projects, caller, permissions),
        new DailyArrangementService(
            storage,
            permissions,
            caller,
            new InspectionValidationService(repository, projects, caller, permissions)),
        storage,
        generation,
        mock(InspectionTaskAssignmentService.class),
        json);
  }

  @Test
  void schedulerMaintainsExistingTaskAfterCreationTransactionRollsBack() throws Exception {
    notificationUser();
    fixture(true, 1, 1);
    long existingTask = PLAN + 90;
    repository.insertTask(
        existingTask,
        TENANT,
        "daily",
        "EXISTING",
        PLAN,
        null,
        1L,
        "existing",
        90001L,
        90002L,
        now().minusMinutes(10),
        now().plusHours(1),
        InspectionTaskStatus.NOT_STARTED.code(),
        json.writeValueAsString(
            Map.of("owners", List.of(Long.toString(USER)), "equipmentName", "既有任务")));
    doReturn(List.of(new InspectionGenerationTarget(TENANT, PLAN)))
        .when(repository)
        .generationTargets();
    doAnswer(
            call -> {
              throw new IllegalStateException("模拟新任务创建事件失败");
            })
        .when(journal)
        .insertTaskEvent(anyLong(), eq(TENANT), anyLong(), any(), any(), eq("生成任务"), anyString());
    var scheduler = new InspectionScheduler(generation, lifecycle);
    scheduler.generate();
    scheduler.generate();
    assertEquals(1, count("eam_inspection_task"), "创建批次必须回滚，保留此前的任务");
    assertEquals(day.atStartOfDay(), cursor(), "失败批次不能推进游标");
    assertEquals(
        InspectionTaskStatus.PENDING.code(),
        db.queryForObject(
            "SELECT status FROM eam_inspection_task WHERE tenant_id=? AND id=?",
            String.class,
            TENANT,
            existingTask));
    assertEquals(
        1L,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_inspection_event WHERE tenant_id=? AND action='任务到期开始'",
            Long.class,
            TENANT),
        "重复失败不重复激活事件");
    assertEquals(1, inbox("点巡检待执行"), "两次回退只能产生一条待执行通知");
    assertEquals(1, count("eam_notify_message"));
  }

  @Test
  void generationTargetsPreserveFullTenantAndPlanIds() throws Exception {
    fixture(true, 1, 1);
    var ownTargets =
        repository.generationTargets().stream().filter(t -> t.tenantId() == TENANT).toList();
    assertEquals(List.of(new InspectionGenerationTarget(TENANT, PLAN)), ownTargets);
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "EAM_INSPECTION_LOAD_TEST", matches = "true")
  void concurrentGenerationLoad() throws Exception {
    // 仅显式开启时执行。使用隔离库、真实事务和8个并发连接，不冒充多JVM或生产容量验收。
    fixture(true, 250, 4);
    int workerCount = 8;
    var ready = new CountDownLatch(workerCount);
    var start = new CountDownLatch(1);
    var workers = Executors.newFixedThreadPool(workerCount);
    var calls = new ArrayList<Future<LoadObservation>>();
    long began = System.nanoTime();
    try {
      for (int i = 0; i < workerCount; i++) {
        calls.add(
            workers.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  long before = System.nanoTime();
                  int count = generation.generate(TENANT, PLAN);
                  return new LoadObservation(
                      count, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before));
                }));
      }
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      var observations = new ArrayList<LoadObservation>();
      for (var call : calls) observations.add(call.get(180, TimeUnit.SECONDS));
      long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began);
      assertEquals(1000, observations.stream().mapToInt(LoadObservation::generated).sum());
      assertCreated(1000);
      assertEquals(day.plusDays(1).atStartOfDay(), cursor());
      var latencies =
          observations.stream().mapToLong(LoadObservation::elapsedMs).sorted().toArray();
      System.out.printf(
          "INSPECTION_LOAD workers=%d devices=250 shifts=4 tasks=1000 errors=0 elapsedMs=%d"
              + " p50Ms=%d p95Ms=%d%n",
          workerCount, elapsedMs, latencies[3], latencies[7]);
    } finally {
      start.countDown();
      workers.shutdownNow();
      boolean stopped = workers.awaitTermination(30, TimeUnit.SECONDS);
      if (!stopped) {
        ownsFixture = false;
      }
      assertTrue(stopped, "连接未退出，保留专用租户数据，禁止并发清理");
    }
  }

  private record LoadObservation(int generated, long elapsedMs) {}

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
    assertEquals(
        1L,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_inspection_task t JOIN eam_inspection_daily_exemption e ON"
                + " e.tenant_id=t.tenant_id AND e.plan_id=t.plan_id AND"
                + " e.equipment_id=t.equipment_id WHERE t.tenant_id=? AND"
                + " t.exemption_reason=e.reason AND t.exempted_by=e.approved_by AND"
                + " t.exempted_at=e.approved_at",
            Long.class,
            TENANT));
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
