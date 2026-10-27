package com.windasoft.eam.integration;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.service.inspection.*;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_STAFFING_TEST_URL", matches = ".+")
class DailyStaffingMySqlIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    String url = System.getenv("EAM_STAFFING_TEST_URL");
    if (url == null
        || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:3307/eam_staffing_test_20261023(\\?.*)?"))
      throw new IllegalStateException("Only the isolated staffing MySQL instance is allowed");
    p.add("spring.datasource.url", () -> url);
    p.add("spring.datasource.username", () -> "root");
    p.add("spring.datasource.password", () -> "");
  }

  @MockitoBean com.windasoft.eam.scheduler.system.NotificationScheduler notificationScheduler;
  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler maintenanceScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inspection.InspectionScheduler inspectionScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup cleanup;
  @MockitoBean com.windasoft.eam.scheduler.experience.RepairPlanScheduler repairScheduler;
  @Autowired InspectionService plans;
  @Autowired InspectionTaskService tasks;
  @Autowired InspectionListService lists;
  @Autowired DailyArrangementRepository staffing;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  final ThreadLocal<Long> actor = ThreadLocal.withInitial(() -> 73001L);
  static final long OTHER = 99073002L;

  @BeforeEach
  void setup() {
    actor.set(73001L);
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenAnswer(i -> actor.get());
    when(auth.user())
        .thenAnswer(
            i -> {
              var u = new SystemUser();
              u.setId(actor.get());
              u.setTenantId(10001L);
              u.setOrgId(71003L);
              u.setDisplayName("staffing tester " + actor.get());
              return u;
            });
    when(permissions.inspection(anyString(), anyString()))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    when(permissions.companyOfOrganization(anyLong(), any())).thenReturn(71002L);
    when(permissions.companyOfOrganization(any())).thenReturn(71002L);
    db.update(
        "INSERT IGNORE INTO"
            + " eam_sys_user(id,tenant_id,account,display_name,password_hash,org_id,status,deleted)"
            + " SELECT ?,tenant_id,'qa-staffing-other','staffing other',password_hash,org_id,1,0"
            + " FROM eam_sys_user WHERE id=73001",
        OTHER);
    db.update(
        "INSERT IGNORE INTO"
            + " eam_equipment(id,tenant_id,equipment_code,name,company_org_id,department_org_id,owner_user_id)"
            + " VALUES(99090001,10001,'QA-STAFFING-EQ','staffing equipment',71002,71003,73001)");
  }

  Map<String, Object> mutable(Object v) {
    return json.convertValue(
        v, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
  }

  Map<String, Object> arrangement(Map<String, Object> p) {
    return rows(p.get("arrangements")).getFirst();
  }

  Map<String, Object> plan(String mode) {
    return plan(mode, "shift");
  }

  Map<String, Object> plan(String mode, String frequency) {
    return plan(mode, frequency, p -> {});
  }

  Map<String, Object> plan(String mode, String frequency, java.util.function.Consumer<Map<String, Object>> configure) {
    var type =
        plans.save(
            "types",
            null,
            mutable(
                Map.of(
                    "name",
                    "staffing type " + UUID.randomUUID(),
                    "period",
                    1,
                    "unit",
                    "日",
                    "enabled",
                    true)));
    var standard =
        plans.save(
            "standards",
            null,
            mutable(
                Map.of(
                    "name",
                    "staffing standard " + UUID.randomUUID(),
                    "departmentId",
                    "71003",
                    "equipmentIds",
                    List.of("99090001"),
                    "items",
                    List.of(
                        Map.of(
                            "id",
                            "check",
                            "name",
                            "check",
                            "content",
                            "condition",
                            "typeId",
                            type.get("id"),
                            "valueType",
                            "文本",
                            "required",
                            true)))));
    var a = new LinkedHashMap<String, Object>();
    a.put("name", "three-shift inspection");
    a.put("enabled", true);
    a.put("frequency", "shift");
    a.put("intervalCount", 1);
    a.put("anchorDate", now().toLocalDate().toString());
    a.put("startTime", "00:00:00");
    a.put("endTime", "23:59:59");
    a.put("windowMinutes", 30);
    a.put("personMode", mode);
    a.put("followUpUserId", mode.equals("open") ? "" : "73001");
    a.put("owners", List.of());
    a.put("equipmentIds", List.of());
    a.put("projectRefs", List.of());
    a.put("allowLate", true);
    a.put("regularRoster", List.of());
    a.put("dateRoster", List.of());
    a.put(
        "slots",
        List.of(Map.of("id", "day", "name", "白班", "start", "00:00:00", "end", "23:59:59")));
    a.put("frequency", frequency);
    a.put("eventType", "startup");
    var p = new LinkedHashMap<String, Object>();
    p.put("name", "staffing plan " + UUID.randomUUID());
    p.put("departmentId", "71003");
    p.put("enabled", true);
    p.put("reviewMode", "无需验收");
    p.put("owners", List.of());
    p.put("reviewers", List.of());
    p.put("scanMode", "无需扫码");
    p.put("startDate", now().toLocalDate().toString());
    p.put("endDate", now().toLocalDate().plusDays(31).toString());
    p.put("restCycle", "按周");
    p.put("restDays", List.of());
    p.put("holidays", List.of());
    p.put("workdays", List.of());
    p.put("advanceHours", 0);
    p.put("remindHours", 0);
    p.put("expireRemindHours", 0);
    p.put("randomPhotoCount", 0);
    p.put("customFields", Map.of());
    p.put("customSchemaVersion", 0);
    p.put("arrangements", List.of(a));
    p.put(
        "details",
        List.of(
            Map.of(
                "equipmentId",
                "99090001",
                "owners",
                List.of(),
                "reviewers",
                List.of(),
                "projectRefs",
                List.of(Map.of("standardId", standard.get("id"), "itemId", "check")))));
    configure.accept(p);
    return mutable(plans.save("daily-plans", null, p));
  }

  long task(Map<String, Object> p) {
    return db.queryForObject(
        "SELECT id FROM eam_inspection_task WHERE tenant_id=10001 AND plan_id=? ORDER BY due_at"
            + " LIMIT 1",
        Long.class,
        number(p.get("id")));
  }

  Map<String, Object> rule(long user) {
    return mutable(
        Map.of(
            "key",
            "regular",
            "weekday",
            0,
            "slotId",
            "day",
            "start",
            "00:00:00",
            "end",
            "00:00:00",
            "users",
            List.of(Long.toString(user)),
            "unassigned",
            false));
  }

  Map<String, Object> periodicDraft() {
    var source = plan("open");
    var p = mutable(source);
    p.remove("id"); p.remove("rowVersion"); p.put("code", "");
    p.put("name", "periodic-start-" + UUID.randomUUID());
    var type = plans.save("types", null, mutable(Map.of("name", "periodic-cycle-" + UUID.randomUUID(), "period", 1, "unit", "日", "enabled", true)));
    p.put("typeId", type.get("id")); p.put("owners", List.of("73001"));
    p.put("validHours", 1); p.put("validUnit", "日");
    p.remove("nextAt"); return p;
  }

  @Test
  @org.springframework.transaction.annotation.Transactional
  void periodicMigrationOnlyRealignsPlansWithoutAnyTaskHistory() {
    var live = plans.save("periodic-plans",null,periodicDraft()); long liveId=number(live.get("id"));
    var body=periodicDraft();body.put("enabled",false);
    var untouched=plans.save("periodic-plans",null,body);long id=number(untouched.get("id"));
    db.update("UPDATE eam_inspection_schedule SET next_at=DATE_ADD(anchor_at,INTERVAL 12 HOUR),anchor_at=DATE_ADD(anchor_at,INTERVAL 12 HOUR) WHERE tenant_id=10001 AND plan_id IN (?,?)",liveId,id);
    db.update("UPDATE eam_inspection_periodic_plan SET next_at=DATE_ADD(TIMESTAMP(start_date),INTERVAL 12 HOUR) WHERE tenant_id=10001 AND id IN (?,?)",liveId,id);
    var before=db.queryForMap("SELECT next_at,anchor_at,cycle_index FROM eam_inspection_schedule WHERE tenant_id=10001 AND plan_id=?",liveId);
    int version=db.queryForObject("SELECT row_version FROM eam_inspection_periodic_plan WHERE tenant_id=10001 AND id=?",Integer.class,id);
    db.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(con,new org.springframework.core.io.ClassPathResource("db/migration/V100__periodic_first_inspection_from_plan_start.sql"));return null;
    });
    assertEquals(before,db.queryForMap("SELECT next_at,anchor_at,cycle_index FROM eam_inspection_schedule WHERE tenant_id=10001 AND plan_id=?",liveId));
    assertEquals(LocalDate.parse(text(body,"startDate")).atStartOfDay(),db.queryForObject("SELECT anchor_at FROM eam_inspection_schedule WHERE tenant_id=10001 AND plan_id=?",LocalDateTime.class,id));
    assertEquals(version+1,db.queryForObject("SELECT row_version FROM eam_inspection_periodic_plan WHERE tenant_id=10001 AND id=?",Integer.class,id));
  }

  @Test
  void periodicCustomCycleIsSavedAndUsedWithoutChangingTypeDefaults() {
    var body=periodicDraft();body.put("period",2);body.put("unit","周");body.put("validHours",2);body.put("validUnit","周");
    long type=number(body.get("typeId"));
    var saved=mutable(plans.save("periodic-plans",null,body));long id=number(saved.get("id"));
    assertEquals(2,number(saved.get("period")));assertEquals("周",saved.get("unit"));
    assertEquals(LocalDate.parse(text(body,"startDate")).plusWeeks(2).atStartOfDay(),db.queryForObject("SELECT next_at FROM eam_inspection_schedule WHERE tenant_id=10001 AND plan_id=?",LocalDateTime.class,id));
    var defaultType=plans.definition("types",type);assertEquals(1,number(defaultType.get("period")));assertEquals("日",defaultType.get("unit"));
    saved.put("remark","keep custom cycle");var updated=plans.save("periodic-plans",id,saved);
    assertEquals(2,number(updated.get("period")));assertEquals("周",updated.get("unit"));
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=10001 AND plan_id=?",Integer.class,id));
    var changed=mutable(updated);changed.put("period",3);
    assertThrows(Exception.class,()->plans.save("periodic-plans",id,changed));
    assertEquals(2,db.queryForObject("SELECT period_count FROM eam_inspection_periodic_plan WHERE tenant_id=10001 AND id=?",Integer.class,id));
  }

  @Test
  void periodicCycleDefaultsOnlyWhenOmittedAndRejectsInvalidOverrides() {
    var body=periodicDraft();body.put("enabled",false);body.remove("period");body.remove("unit");
    var saved=mutable(plans.save("periodic-plans",null,body));long id=number(saved.get("id"));
    assertEquals(1,number(saved.get("period")));assertEquals("日",saved.get("unit"));
    for(Object invalid:List.of(0,1.5,10001)) {
      var draft=mutable(saved);draft.put("period",invalid);
      assertThrows(Exception.class,()->plans.save("periodic-plans",id,draft));
    }
    var invalidUnit=mutable(saved);invalidUnit.put("unit","时");assertThrows(Exception.class,()->plans.save("periodic-plans",id,invalidUnit));
    saved.put("period",3);saved.put("unit","月");var updated=plans.save("periodic-plans",id,saved);
    assertEquals(3,number(updated.get("period")));assertEquals("月",updated.get("unit"));
  }

  @Test
  void periodicFirstInspectionUsesPlanStartWithoutSeparateNextAt() {
    var body = periodicDraft();
    body.put("nextAt", "ignored-old-client-value");
    var saved = plans.save("periodic-plans", null, body);
    long id = number(saved.get("id"));
    var expected = LocalDate.parse(text(body, "startDate")).atStartOfDay();
    assertEquals(expected, db.queryForObject("SELECT anchor_at FROM eam_inspection_schedule WHERE tenant_id=10001 AND plan_id=?", LocalDateTime.class, id));
    assertEquals(expected, db.queryForObject("SELECT MIN(due_at) FROM eam_inspection_task WHERE tenant_id=10001 AND plan_id=?", LocalDateTime.class, id));
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=10001 AND plan_id=?", Integer.class, id));
    assertEquals(text(body, "startDate") + "T00:00:00", saved.get("startDate"));
    assertEquals(text(body, "endDate") + "T23:59:59", saved.get("endDate"));
  }

  @Test
  void periodicPlanPreservesSecondsAndHonorsExactEndBoundary() {
    var start = now().toLocalDate().minusDays(3).atTime(8, 17, 43);
    for (boolean inclusive : List.of(false, true)) {
      var body = periodicDraft();
      body.put("startDate", start.toString());
      var end = start.plusDays(1).minusSeconds(inclusive ? 0 : 1);
      body.put("endDate", end.toString());
      body.put("period", 1); body.put("unit", "日");
      var saved = mutable(plans.save("periodic-plans", null, body));
      long id = number(saved.get("id"));
      assertEquals(start.toString(), saved.get("startDate"));
      assertEquals(end.toString(), saved.get("endDate"));
      assertEquals(start, db.queryForObject("SELECT start_date FROM eam_inspection_periodic_plan WHERE id=?", LocalDateTime.class, id));
      assertEquals(end, db.queryForObject("SELECT end_date FROM eam_inspection_periodic_plan WHERE id=?", LocalDateTime.class, id));
      assertEquals(start, db.queryForObject("SELECT anchor_at FROM eam_inspection_schedule WHERE plan_id=?", LocalDateTime.class, id));
      assertEquals(start, db.queryForObject("SELECT MIN(due_at) FROM eam_inspection_task WHERE plan_id=?", LocalDateTime.class, id));
      assertEquals(inclusive ? 2 : 1, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE plan_id=?", Integer.class, id));
      var cursor = db.queryForMap("SELECT next_at,anchor_at,cycle_index FROM eam_inspection_schedule WHERE plan_id=?", id);
      saved.put("remark", "preserve seconds and cursor");
      var updated = plans.save("periodic-plans", id, saved);
      assertEquals(start.toString(), updated.get("startDate"));
      assertEquals(cursor, db.queryForMap("SELECT next_at,anchor_at,cycle_index FROM eam_inspection_schedule WHERE plan_id=?", id));
      assertEquals(inclusive ? 2 : 1, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE plan_id=?", Integer.class, id));
    }
  }

  @Test
  void periodicPlanRejectsInvertedTimesWithinSameDay() {
    var body = periodicDraft(); body.put("enabled", false);
    String day = now().toLocalDate().plusDays(2).toString();
    body.put("startDate", day + "T09:30:15");
    body.put("endDate", day + "T09:30:14");
    assertThrows(IllegalArgumentException.class, () -> plans.save("periodic-plans", null, body));
    body.put("endDate", day + "T09:30:15");
    var saved = plans.save("periodic-plans", null, body);
    assertEquals(body.get("startDate"), saved.get("startDate"));
    assertEquals(body.get("endDate"), saved.get("endDate"));
  }

  @Test
  void periodicOrdinarySavePreservesAdvancedCursorAndDoesNotDuplicateTasks() {
    var saved = mutable(plans.save("periodic-plans", null, periodicDraft()));
    long id = number(saved.get("id"));
    var before = db.queryForMap("SELECT next_at,anchor_at,cycle_index FROM eam_inspection_schedule WHERE tenant_id=10001 AND plan_id=?", id);
    int count = db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=10001 AND plan_id=?", Integer.class, id);
    saved.put("remark", "ordinary edit"); saved.remove("nextAt");
    var updated = plans.save("periodic-plans", id, saved);
    assertEquals("ordinary edit", updated.get("remark"));
    assertEquals(before, db.queryForMap("SELECT next_at,anchor_at,cycle_index FROM eam_inspection_schedule WHERE tenant_id=10001 AND plan_id=?", id));
    assertEquals(count, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=10001 AND plan_id=?", Integer.class, id));
    var invalid = mutable(updated); invalid.put("startDate", periodicBoundary(invalid.get("startDate"),false).minusDays(1).toString());
    assertThrows(Exception.class, () -> plans.save("periodic-plans", id, invalid));
    assertEquals(before, db.queryForMap("SELECT next_at,anchor_at,cycle_index FROM eam_inspection_schedule WHERE tenant_id=10001 AND plan_id=?", id));
  }

  @Test
  void periodicUntouchedPlanCanMoveStartWhileRemainingDisabled() {
    var body = periodicDraft();body.put("enabled",false);body.put("startDate",now().toLocalDate().plusDays(1).toString());
    var saved = mutable(plans.save("periodic-plans", null, body));long id=number(saved.get("id"));
    saved.put("startDate",now().toLocalDate().plusDays(2).toString());saved.remove("nextAt");
    plans.save("periodic-plans", id, saved);
    assertEquals(now().toLocalDate().plusDays(2).atStartOfDay(),db.queryForObject("SELECT next_at FROM eam_inspection_schedule WHERE tenant_id=10001 AND plan_id=?",LocalDateTime.class,id));
    assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=10001 AND plan_id=?",Integer.class,id));
  }

  @Test
  void openTasksAreUnassignedDiscoverableIdempotentAndRecordActualOperator() {
    var p = plan("open");
    long id = task(p);
    var t = tasks.task(id);
    assertEquals("", t.get("ownerId"));
    assertFalse((Boolean) t.get("assignmentMissing"));
    assertTrue(ids(t.get("owners")).isEmpty());
    assertTrue(
        lists.deviceTasks("99090001", "", 1, 20).data().stream()
            .anyMatch(x -> number(x.get("id")) == id));
    assertTrue(
        lists.deviceTasks("", "QA-STAFFING-EQ", 1, 20).data().stream()
            .anyMatch(x -> number(x.get("id")) == id));
    assertEquals(0, plans.generate(10001, number(p.get("id"))));
    var started = tasks.action(id, "start", Map.of("rowVersion", t.get("rowVersion")));
    assertEquals("73001", started.get("ownerId"));
    assertEquals("执行中", started.get("status"));
    assertThrows(
        Exception.class,
        () -> tasks.action(id, "start", Map.of("rowVersion", t.get("rowVersion"))));
    var body = new LinkedHashMap<String, Object>();
    body.put("rowVersion", started.get("rowVersion"));
    body.put(
        "items",
        rows(started.get("items")).stream()
            .map(i -> Map.of("id", i.get("id"), "value", "正常"))
            .toList());
    body.put("customFields", Map.of());
    body.put("customSchemaVersion", 0);
    var done = tasks.action(id, "submit", body);
    assertEquals("已完成", done.get("status"));
    assertEquals(
        73001L,
        db.queryForObject(
            "SELECT submitted_by FROM eam_inspection_daily_submission WHERE tenant_id=10001 AND"
                + " task_id=? ORDER BY submitted_at DESC LIMIT 1",
            Long.class,
            id));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=10001 AND plan_id=? AND"
                + " owner_id IS NULL AND status='已完成'",
            Integer.class,
            number(p.get("id"))));
  }

  @Test
  void arrangementsFollowThePlanAndIgnoreLegacyDisabledInput() {
    var enabled = plan("open", "shift", p -> arrangement(p).put("enabled", false));
    long planId = number(enabled.get("id"));
    assertTrue(yes(arrangement(enabled).get("enabled")));
    assertEquals(1, db.queryForObject("SELECT enabled FROM eam_inspection_daily_arrangement WHERE tenant_id=10001 AND plan_id=?", Integer.class, planId));
    assertTrue(db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=10001 AND plan_id=?", Integer.class, planId) > 0);
    var disabled = plan("open", "shift", p -> {p.put("enabled", false);arrangement(p).put("enabled", false);});
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=10001 AND plan_id=?", Integer.class, number(disabled.get("id"))));
    assertTrue(yes(arrangement(disabled).get("enabled")));
  }

  @Test
  void staffingRulesRoundTripRefreshOnlyUnstartedAndExplicitUnstaffedPersists() {
    var p = plan("roster");
    long id = task(p);
    var a = arrangement(p);
    a.put("regularRoster", List.of(rule(73001)));
    assertEquals(1, plans.assignmentImpact(p).get("count"));
    p = mutable(plans.save("daily-plans", number(p.get("id")), p));
    a = arrangement(p);
    assertEquals(List.of(73001L), ids(tasks.task(id).get("owners")));
    assertEquals(1, rows(a.get("regularRoster")).size());
    var override =
        mutable(
            Map.of(
                "key",
                "exception",
                "date",
                now().toLocalDate().toString(),
                "slotId",
                "day",
                "start",
                "00:00:00",
                "end",
                "00:00:00",
                "users",
                List.of(),
                "unassigned",
                true));
    a.put("dateRoster", List.of(override));
    p = mutable(plans.save("daily-plans", number(p.get("id")), p));
    assertTrue(ids(tasks.task(id).get("owners")).isEmpty());
    assertTrue((Boolean) tasks.task(id).get("assignmentMissing"));
    a = arrangement(p);
    override.put("unassigned", false);
    override.put("users", List.of(Long.toString(OTHER)));
    a.put("dateRoster", List.of(override));
    p = mutable(plans.save("daily-plans", number(p.get("id")), p));
    actor.set(OTHER);
    var t = tasks.task(id);
    tasks.action(id, "start", Map.of("rowVersion", t.get("rowVersion")));
    actor.set(73001L);
    arrangement(p).put("dateRoster", List.of());
    plans.save("daily-plans", number(p.get("id")), p);
    assertEquals(Long.toString(OTHER), tasks.task(id).get("ownerId"));
    assertEquals(List.of(OTHER), ids(tasks.task(id).get("owners")));
  }

  @Test
  void twoOperatorsCannotBothStartTheSameTask() throws Exception {
    var p = plan("open");
    long id = task(p);
    var t = tasks.task(id);
    var latch = new CountDownLatch(1);
    var success = new AtomicInteger();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var jobs = new ArrayList<Future<?>>();
      for (long user : List.of(73001L, OTHER))
        jobs.add(
            executor.submit(
                () -> {
                  actor.set(user);
                  try {
                    latch.await();
                    tasks.action(id, "start", Map.of("rowVersion", t.get("rowVersion")));
                    success.incrementAndGet();
                  } catch (Exception expected) {
                  } finally {
                    actor.remove();
                  }
                }));
      latch.countDown();
      for (var job : jobs) job.get(30, TimeUnit.SECONDS);
    }
    assertEquals(1, success.get());
    assertEquals("执行中", tasks.task(id).get("status"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_inspection_event WHERE tenant_id=10001 AND task_id=? AND"
                + " action='开始点巡检'",
            Integer.class,
            id));
  }

  @Test
  void retiredFrequencyAndManualEventCannotCreateTasks() {
    for (String f : List.of("daily", "weekly", "monthly", "interval", "event"))
      assertThrows(com.windasoft.eam.common.FieldValidationException.class, () -> plan("open", f));
    var p = plan("open");
    var body = new LinkedHashMap<String, Object>();
    body.put("rowVersion", p.get("rowVersion"));
    assertThrows(com.windasoft.eam.common.LocalizedArgumentException.class,
        () -> plans.dailyEvent(number(p.get("id")), body));
  }

  @Test
  void weeklyAndMonthlyRestRoundTripAndSuppressActualGenerationUntilBothCleared() {
    int weekday = now().getDayOfWeek().getValue(), day = now().getDayOfMonth();
    var p = plan("open", "shift", draft -> {
      draft.put("weeklyRestDays", List.of(weekday));
      draft.put("monthlyRestDays", List.of(day, day == 31 ? 15 : 31));
      arrangement(draft).put("slots", List.of(
          Map.of("id", "day", "name", "白班", "start", "00:00:00", "end", "23:59:59"),
          Map.of("id", "mid", "name", "中班", "start", "00:00:00", "end", "23:59:59"),
          Map.of("id", "night", "name", "晚班", "start", "00:00:00", "end", "23:59:59")));
    });
    long id = number(p.get("id"));
    assertEquals(List.of((long) weekday), ids(p.get("weeklyRestDays")));
    assertEquals(2, ids(p.get("monthlyRestDays")).size());
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE plan_id=?", Integer.class, id));
    p.put("weeklyRestDays", List.of());
    p = mutable(plans.save("daily-plans", id, p));
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE plan_id=?", Integer.class, id));
    p.put("monthlyRestDays", List.of());
    p = mutable(plans.save("daily-plans", id, p));
    assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE plan_id=?", Integer.class, id));
    plans.generate(10001, id);
    assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE plan_id=?", Integer.class, id));
    assertTrue(ids(p.get("weeklyRestDays")).isEmpty());
    assertTrue(ids(p.get("monthlyRestDays")).isEmpty());
  }

  @Test
  void noShiftsGeneratesNothingAndSpecialWorkdayStillRequiresShifts() {
    var p = plan("open", "shift", draft -> {
      draft.put("weeklyRestDays", List.of(now().getDayOfWeek().getValue()));
      draft.put("monthlyRestDays", List.of(now().getDayOfMonth()));
      draft.put("workdays", List.of(now().toLocalDate().toString()));
      arrangement(draft).put("slots", List.of());
    });
    long id = number(p.get("id"));
    plans.generate(10001, id);
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE plan_id=?", Integer.class, id));
    var working = plan("open", "shift", draft -> {
      draft.put("weeklyRestDays", List.of(now().getDayOfWeek().getValue()));
      draft.put("monthlyRestDays", List.of(now().getDayOfMonth()));
      draft.put("workdays", List.of(now().toLocalDate().toString()));
    });
    assertNotNull(task(working));
  }

  @Test
  void invalidStaffReferencesAndStaleVersionsDoNotChangeSavedRules() {
    var p = plan("roster");
    long planId = number(p.get("id"));
    var invalid = mutable(p);
    var bad = rule(73001);
    bad.put("slotId", "removed-slot");
    arrangement(invalid).put("regularRoster", List.of(bad));
    final var invalidSlot = invalid;
    assertThrows(Exception.class, () -> plans.save("daily-plans", planId, invalidSlot));
    assertTrue(rows(staffing.load(10001, planId).getFirst().get("regularRoster")).isEmpty());
    invalid = mutable(p);
    var people = rule(123456789);
    arrangement(invalid).put("regularRoster", List.of(people));
    final var invalidPeople = invalid;
    assertThrows(Exception.class, () -> plans.save("daily-plans", planId, invalidPeople));
    var old = mutable(p);
    old.put("rowVersion", -1);
    assertThrows(Exception.class, () -> plans.assignmentImpact(old));
  }

  @Test
  void deletePendingTaskKeepsGenerationIdentityAndHidesItFromLists() {
    var p = plan("open");
    long id = task(p), planId = number(p.get("id"));
    var t = tasks.task(id);
    assertEquals(true, t.get("canDelete"));
    assertThrows(Exception.class, () -> tasks.action(id, "delete", Map.of(
        "rowVersion", -1, "description", "cancel duplicate inspection")));
    assertThrows(Exception.class, () -> tasks.action(id, "delete", Map.of(
        "rowVersion", t.get("rowVersion"), "description", " ")));
    tasks.action(id, "delete", Map.of("rowVersion", t.get("rowVersion"), "description", "cancel duplicate inspection"));
    assertEquals(1, db.queryForObject("SELECT deleted FROM eam_inspection_task WHERE id=?", Integer.class, id));
    assertThrows(Exception.class, () -> tasks.task(id));
    plans.save("daily-plans", planId, p);
    plans.generate(10001, planId);
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE plan_id=?", Integer.class, planId));
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE plan_id=? AND deleted=0", Integer.class, planId));
  }

  @Test
  void deletedTasksDoNotBlockPlanDeletionButVisibleTasksStillDo() {
    var p = plan("open");
    long planId = number(p.get("id")), taskId = task(p);
    assertThrows(Exception.class, () -> plans.delete("daily-plans", planId));
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_daily_plan WHERE id=?", Integer.class, planId));
    var task = tasks.task(taskId);
    tasks.action(taskId, "delete", Map.of("rowVersion", task.get("rowVersion"), "description", "cancel test plan"));
    plans.delete("daily-plans", planId);
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_daily_plan WHERE id=?", Integer.class, planId));
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_daily_arrangement WHERE plan_id=?", Integer.class, planId));
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_schedule WHERE plan_id=?", Integer.class, planId));
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM eam_inspection_task WHERE id=? AND deleted=1", Integer.class, taskId));
  }

  @Test
  void planStartDateIsTheOnlyArrangementStartAndTaskExposesItsPlanId() {
    var p = plan("open", "shift", draft -> arrangement(draft).remove("anchorDate"));
    assertEquals(p.get("startDate"), arrangement(p).get("anchorDate"));
    var t = tasks.task(task(p));
    assertEquals(p.get("id").toString(), t.get("planId"));
    assertEquals("白班", t.get("shiftName"));
  }

  @Test
  void dailyAndPeriodicOrganizationTreesFilterByPlanInsteadOfEquipmentDepartment() {
    db.update("UPDATE eam_equipment SET department_org_id=71004 WHERE id=99090001");
    try {
      var daily = plan("open");
      long dailyTask = task(daily);
      assertEquals(71004L, db.queryForObject("SELECT department_id FROM eam_inspection_task WHERE id=?", Long.class, dailyTask));
      var periodic = mutable(daily);
      periodic.remove("id"); periodic.remove("rowVersion"); periodic.put("code", "");
      periodic.put("name", "periodic tree " + UUID.randomUUID());
      periodic.put("owners", List.of("73001"));
      periodic.put("typeId", db.queryForObject("SELECT id FROM eam_inspection_type WHERE tenant_id=10001 AND enabled=1 ORDER BY id LIMIT 1", Long.class).toString());
      periodic.put("nextAt", now().minusSeconds(1).toString());
      periodic.put("validHours", 1); periodic.put("validUnit", "时");
      var saved = plans.save("periodic-plans", null, periodic);
      for (var entry : Map.of("daily-tasks", number(daily.get("id")), "periodic-tasks", number(saved.get("id"))).entrySet()) {
        var inPlan = lists.query(entry.getKey(), com.windasoft.eam.dto.common.ListQuery.from(Map.of("organization", "71003", "size", "100")));
        assertTrue(inPlan.data().stream().anyMatch(row -> number(row.get("planId")) == entry.getValue()), entry.getKey());
        var inDevice = lists.query(entry.getKey(), com.windasoft.eam.dto.common.ListQuery.from(Map.of("organization", "71004", "size", "100")));
        assertTrue(inDevice.data().stream().noneMatch(row -> number(row.get("planId")) == entry.getValue()), entry.getKey());
      }
    } finally {
      db.update("UPDATE eam_equipment SET department_org_id=71003 WHERE id=99090001");
    }
  }

  @Test
  void deletionRejectsStartedExpiredTasksAndOutOfScopeOperators() {
    var p = plan("open");
    long id = task(p);
    var t = tasks.task(id);
    when(permissions.inspection("daily", "删除")).thenReturn(new PermissionScope(false, null, Set.of(), false));
    assertEquals(false, tasks.task(id).get("canDelete"));
    assertThrows(Exception.class, () -> tasks.action(id, "delete", Map.of("rowVersion", t.get("rowVersion"), "description", "not allowed")));
    when(permissions.inspection("daily", "删除")).thenReturn(new PermissionScope(true, null, Set.of(), false));
    tasks.action(id, "start", Map.of("rowVersion", t.get("rowVersion")));
    db.update("UPDATE eam_inspection_task SET status='已过期',row_version=row_version+1 WHERE id=?", id);
    var expired = tasks.task(id);
    assertEquals(false, expired.get("canDelete"));
    assertThrows(Exception.class, () -> tasks.action(id, "delete", Map.of("rowVersion", expired.get("rowVersion"), "description", "already started")));
    assertEquals(0, db.queryForObject("SELECT deleted FROM eam_inspection_task WHERE id=?", Integer.class, id));
  }

  @Test
  void migrationPreservesLegacyRowsAndMergesSameDateShiftPeople() throws Exception {
    var p = plan("roster");
    long planId = number(p.get("id")), arrangementId = number(arrangement(p).get("id"));
    String schema = "eam_staffing_upgrade_test_" + System.currentTimeMillis();
    db.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
    String url =
        System.getenv("EAM_STAFFING_TEST_URL").replace("eam_staffing_test_20261023", schema);
    org.flywaydb.core.Flyway.configure().dataSource(url, "root", "").target("60").load().migrate();
    var target =
        new JdbcTemplate(
            new org.springframework.jdbc.datasource.DriverManagerDataSource(url, "root", ""));
    // Historical V25/V26 allocate MAX(id)+n, while V61 uses UUID_SHORT.
    // Reserve the intervening IDs in this isolated baseline fixture only; never edit applied
    // migrations.
    long max =
        target.queryForObject("SELECT MAX(id) FROM eam_sys_role_menu_permission", Long.class);
    for (int i = 0; i < 10000; i++) {
      if (target.queryForObject("SELECT UUID_SHORT()", Long.class) > max) break;
      if (i == 9999) fail("legacy permission ID reservation exceeded");
    }
    org.flywaydb.core.Flyway.configure().dataSource(url, "root", "").target("92").load().migrate();
    // Copy only columns present in the old schema under test; later migrations add email/bindings.
    for (var table : List.of("eam_sys_user", "eam_inspection_daily_plan")) {
      var names = target.queryForList("SELECT COLUMN_NAME FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION", String.class, table);
      String columns = names.stream().map(n -> "`" + n + "`").collect(java.util.stream.Collectors.joining(","));
      target.update("INSERT IGNORE INTO " + table + "(" + columns + ") SELECT " + columns + " FROM eam_staffing_test_20261023." + table + " WHERE id=?", table.equals("eam_sys_user") ? OTHER : planId);
    }
    target.update(
        "INSERT INTO eam_inspection_daily_arrangement SELECT * FROM"
            + " eam_staffing_test_20261023.eam_inspection_daily_arrangement WHERE id=?",
        arrangementId);
    target.update(
        "INSERT INTO eam_inspection_daily_arrangement_slot SELECT * FROM"
            + " eam_staffing_test_20261023.eam_inspection_daily_arrangement_slot WHERE"
            + " arrangement_id=?",
        arrangementId);
    target.update("INSERT INTO eam_inspection_daily_rest_day VALUES(10001,?,0,1)", planId);
    String date = now().toLocalDate().toString();
    target.update(
        "INSERT INTO eam_inspection_daily_roster"
            + " VALUES(10001,?,0,'first',?,'day'),(10001,?,1,'second',?,'day'),(10001,?,2,'empty',DATE_ADD(?,INTERVAL"
            + " 1 DAY),'day')",
        planId,
        date,
        planId,
        date,
        planId,
        date);
    target.update(
        "INSERT INTO eam_inspection_daily_roster_user VALUES(10001,?,0,73001,0),(10001,?,1,?,0)",
        planId,
        planId,
        OTHER);
    org.flywaydb.core.Flyway.configure().dataSource(url, "root", "").load().migrate();
    assertEquals("weekly", target.queryForObject(
        "SELECT cycle_code FROM eam_inspection_daily_rest_day WHERE plan_id=?", String.class, planId));
    assertEquals("[1]", target.queryForObject(
        "SELECT JSON_EXTRACT(payload,'$.weeklyRestDays') FROM v_inspection_definition WHERE id=? AND kind='daily-plans'", String.class, planId));
    assertEquals(
        3,
        target.queryForObject(
            "SELECT COUNT(*) FROM eam_inspection_daily_roster WHERE plan_id=?",
            Integer.class,
            planId));
    assertEquals(
        2,
        target.queryForObject(
            "SELECT COUNT(*) FROM eam_inspection_daily_staff_override WHERE arrangement_id=?",
            Integer.class,
            arrangementId));
    assertEquals(
        2,
        target.queryForObject(
            "SELECT COUNT(*) FROM eam_inspection_daily_staff_override_person WHERE"
                + " arrangement_id=?",
            Integer.class,
            arrangementId));
    assertEquals(
        1,
        target.queryForObject(
            "SELECT COUNT(*) FROM eam_inspection_daily_staff_override WHERE arrangement_id=? AND"
                + " unassigned=1",
            Integer.class,
            arrangementId));
  }
}
