package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.common.*;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.common.*;
import com.windasoft.eam.repository.maintenance.MaintenanceListRepository;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_ENGINEERING_DB_URL", matches = ".+")
@Transactional
class EngineeringQueryMySqlIT {
  @org.springframework.beans.factory.annotation.Autowired
  com.windasoft.eam.repository.inspection.InspectionExecutionStorage inspectionExecution;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    String url = System.getenv("EAM_ENGINEERING_DB_URL");
    // Validate the target before Spring/Flyway can create or change any schema.
    if (url == null
        || !url.matches(
            "jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/eam_engineering_test(\\?.*)?"))
      throw new IllegalStateException(
          "Integration tests require the isolated eam_engineering_test database");
    properties.add("spring.datasource.url", () -> url);
    properties.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_USER", "eam"));
    properties.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_PASSWORD", ""));
    properties.add(
        "eam.upload.directory",
        () -> System.getProperty("java.io.tmpdir") + "/eam-engineering-uploads");
  }

  @Autowired com.windasoft.eam.repository.inspection.InspectionListRepository inspections;
  @Autowired com.windasoft.eam.repository.inventory.InventoryListRepository inventory;
  @Autowired com.windasoft.eam.repository.system.UserListRepository userLists;
  @Autowired com.windasoft.eam.repository.business.BusinessQueryRepository extensions;
  @Autowired com.windasoft.eam.service.maintenance.MaintenanceListService maintenanceLists;
  @Autowired com.windasoft.eam.service.maintenance.MaintenanceCsvService maintenanceCsv;
  @Autowired com.windasoft.eam.service.maintenance.MaintenanceTaskService maintenanceTasks;
  @Autowired com.windasoft.eam.service.maintenance.MaintenanceService maintenanceDefinitions;
  @Autowired com.windasoft.eam.service.inspection.InspectionService inspectionDefinitions;
  @Autowired com.windasoft.eam.service.inspection.InspectionTaskService inspectionTasks;
  @Autowired com.windasoft.eam.service.inventory.InventoryService inventoryMasters;
  @Autowired com.windasoft.eam.service.inventory.InventoryListService inventoryLists;
  @Autowired com.windasoft.eam.service.inventory.ReceiptService receiptCommands;
  @Autowired com.windasoft.eam.service.inventory.OutboundService outboundCommands;
  @Autowired com.windasoft.eam.service.equipment.EquipmentCommandService equipmentCommands;
  @Autowired com.windasoft.eam.service.repair.RepairOrderService repairCommands;
  @Autowired com.windasoft.eam.service.repair.RepairWorkflowService repairWorkflow;
  @Autowired com.windasoft.eam.service.equipment.EquipmentQueryService equipmentQueries;
  @Autowired com.windasoft.eam.service.repair.RepairQueryService repairQueries;
  @Autowired com.windasoft.eam.repository.experience.HomeRepository homeRepository;
  @Autowired com.windasoft.eam.repository.equipment.EquipmentMediaRepository equipmentMedia;
  @Autowired com.windasoft.eam.repository.equipment.EquipmentWriteRepository equipmentWrites;
  @Autowired ObjectMapper json;
  @Autowired com.windasoft.eam.service.inventory.InventoryFlowService inventoryFlows;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.windasoft.eam.repository.inventory.OutboundRepository outboundRepository;

  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  @Autowired JdbcTemplate db;
  @Autowired MaintenanceListRepository repository;

  @BeforeAll
  static void resetQueryReports() throws Exception {
    java.nio.file.Files.deleteIfExists(
        java.nio.file.Path.of("target/engineering-query-plans.jsonl"));
  }

  void recordPlan(String name, String from, SqlPredicate predicate, String order) {
    String sql =
        "SELECT t.id " + from + " WHERE " + predicate.sql() + " ORDER BY " + order + " LIMIT 10";
    String plan =
        db.queryForObject(
            "EXPLAIN FORMAT=JSON " + sql, String.class, predicate.arguments().toArray());
    try {
      String line =
          new ObjectMapper()
                  .writeValueAsString(
                      Map.of("query", name, "sql", sql, "plan", new ObjectMapper().readTree(plan)))
              + "\n";
      java.nio.file.Files.writeString(
          java.nio.file.Path.of("target/engineering-query-plans.jsonl"),
          line,
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.APPEND);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @BeforeEach
  void fixtures() {
    var rows = new ArrayList<Object[]>();
    for (int i = 0; i < 10005; i++)
      rows.add(
          new Object[] {
            860000000L + i,
            10001L,
            "plans",
            String.format("QA-ENG-%05d", i),
            "工程回归 " + i,
            71003L,
            73001L,
            "{\"enabled\":true,\"remark\":\"isolated\"}"
          });
    db.batchUpdate(
        "INSERT INTO"
            + " eam_maintenance_plan(id,tenant_id,code,name,department_id,creator_id,enabled,remark,calculation_mode,advance_days,remind_days,auto_fill,scan_start,review_mode,review_rule)"
            + " VALUES(?,?,?,?,?,?,1,'isolated','固定间隔',0,0,'无',0,'','')",
        rows.stream().map(r -> new Object[] {r[0], r[1], r[3], r[4], r[5], r[6]}).toList());
    db.update(
        "INSERT INTO"
            + " eam_maintenance_plan(id,tenant_id,code,name,department_id,creator_id,enabled,remark,calculation_mode,advance_days,remind_days,auto_fill,scan_start,review_mode,review_rule)"
            + " VALUES(870000001,10002,'QA-ENG-OTHER-TENANT','other',71003,73001,1,'','固定间隔',0,0,'无',0,'','')");
  }

  MaintenanceListRepository.Selection selection(ListQuery query, PermissionScope scope) {
    return repository.select(
        10001L, 73001L, "plans", scope, false, null, query, SqlPredicate.of("1=1"), null);
  }

  @Test
  void pagesBeyondTenThousandHaveNoTruncationOrDuplicates() {
    var q =
        ListQuery.from(Map.of("q", "QA-ENG-", "size", "200", "sortBy", "code", "direction", "asc"));
    var selection = selection(q, new PermissionScope(true, null, Set.of(), false));
    assertEquals(10005, repository.count(selection));
    var ids = new HashSet<String>();
    for (int page = 1; page <= 51; page++)
      for (var row : repository.page(selection, q.at(page, 200)))
        assertTrue(ids.add(row.get("id").toString()));
    assertEquals(10005, ids.size());
    assertEquals(5, repository.page(selection, q.at(51, 200)).size());
    assertFalse(ids.contains("870000001"));
  }

  @Test
  void conditionsAndExportBatchingHaveTheSameCohort() {
    var q =
        ListQuery.from(
            Map.of(
                "conditions",
                "[{\"field\":\"name\",\"operator\":\"contains\",\"values\":[\"工程回归 1000\"]}]"));
    var selected = selection(q, new PermissionScope(true, null, Set.of(), false));
    assertEquals(6, repository.count(selected));
    assertEquals(6, repository.page(selected, q.at(1, 200)).size());
    assertEquals(
        10005,
        repository.count(
            selection(
                ListQuery.from(Map.of("q", "QA-ENG-")),
                new PermissionScope(true, null, Set.of(), false))));
  }

  @Test
  void missingOrOtherDepartmentGrantDoesNotExpandScope() {
    var q = ListQuery.from(Map.of("q", "QA-ENG-"));
    assertEquals(
        0, repository.count(selection(q, new PermissionScope(false, null, Set.of(), false))));
    assertEquals(
        0, repository.count(selection(q, new PermissionScope(false, null, Set.of(71007L), false))));
    assertEquals(
        10005,
        repository.count(selection(q, new PermissionScope(false, null, Set.of(71003L), false))));
  }

  @Test
  void queryUsesTheRegisteredTenantKindIndex() {
    var explain =
        db.queryForList(
            "EXPLAIN SELECT t.id FROM eam_maintenance_plan t WHERE t.tenant_id=? AND ?='plans'"
                + " AND t.department_id=? ORDER BY t.created_at DESC,t.id DESC LIMIT 10",
            10001L,
            "plans",
            71003L);
    assertFalse(explain.isEmpty());
    assertNotNull(explain.getFirst().get("possible_keys"));
  }

  void inspectionFixtures(int count) {
    var tasks = new ArrayList<Object[]>();
    for (int i = 0; i < count; i++)
      tasks.add(
          new Object[] {
            880000000L + i,
            10001L,
            "daily",
            String.format("QA-INS-%05d", i),
            860000000L + i,
            71002L,
            i % 2 == 0 ? 71003L : 71007L,
            "2026-10-01 08:00:00",
            "2026-10-01 20:00:00",
            "待执行",
            "{\"planName\":\"工程巡检\",\"owners\":[\"73001\"],\"reviewers\":[],\"items\":[],\"equipment\":[]}"
          });
    db.batchUpdate(
        "INSERT INTO"
            + " eam_inspection_task(id,tenant_id,kind,code,plan_id,company_id,department_id,due_at,expires_at,status,snapshot)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON))",
        tasks);
    com.windasoft.eam.support.RelationalTaskFixtures.capture(db);
  }

  @Test
  void inspectionPagesAndParticipantsBeyondTenThousand() {
    inspectionFixtures(10005);
    var q =
        ListQuery.from(Map.of("q", "QA-INS-", "size", "200", "sortBy", "code", "direction", "asc"));
    var none = new PermissionScope(false, null, Set.of(), false);
    var selection =
        inspections.select(
            10001, 73001, "daily-tasks", none, none, false, null, q, SqlPredicate.of("1=1"), null);
    assertEquals(10005, inspections.count(selection));
    var ids = new HashSet<String>();
    for (int page = 1; page <= 51; page++)
      for (var row : inspections.page(selection, q.at(page, 200)))
        assertTrue(ids.add(row.get("id").toString()));
    assertEquals(10005, ids.size());
    var unrelated =
        inspections.select(
            10001, 99999, "daily-tasks", none, none, false, null, q, SqlPredicate.of("1=1"), null);
    assertEquals(0, inspections.count(unrelated));
    var export =
        inspections.select(
            10001, 73001, "daily-tasks", none, none, true, null, q, SqlPredicate.of("1=1"), null);
    assertEquals(0, inspections.count(export));
  }

  @Test
  void inspectionQueuesRangesAndOrganizationIntersect() {
    inspectionFixtures(4);
    var none = new PermissionScope(false, null, Set.of(), false);
    var q =
        ListQuery.from(
            Map.of(
                "queue",
                "我的待执行",
                "conditions",
                "[{\"field\":\"dueAt\",\"operator\":\"range\",\"values\":[\"2026-10-01\",\"2026-10-01\"]}]"));
    var selected =
        inspections.select(
            10001,
            73001,
            "daily-tasks",
            none,
            none,
            false,
            Set.of(71003L),
            q,
            SqlPredicate.of("1=1"),
            null);
    assertEquals(2, inspections.count(selected));
    assertEquals(2, inspections.page(selected, q).size());
    db.update("UPDATE eam_inspection_task SET status='挂起' WHERE id=880000000");
    assertEquals(1, inspections.count(selected));
  }

  @Test
  void inspectionDerivedMetricsKeepTheExistingMeaning() {
    inspectionFixtures(1);
    db.update(
        "UPDATE eam_inspection_task SET"
            + " snapshot=JSON_OBJECT('owners',JSON_ARRAY('73001'),'items',JSON_ARRAY(JSON_OBJECT('id','a','equipmentId','1'),JSON_OBJECT('id','b','equipmentId','2')),'equipment',JSON_ARRAY(JSON_OBJECT('id','1'),JSON_OBJECT('id','2')))"
            + " WHERE id=880000000");
    com.windasoft.eam.support.RelationalTaskFixtures.capture(db);
    inspectionExecution.save(
        10001L,
        880000000L,
        "{\"startedAt\":\"2026-10-01T08:00:00\",\"finishedAt\":\"2026-10-02T08:00:00\",\"items\":[{\"id\":\"a\",\"value\":\"0\",\"abnormal\":true}],\"skippedEquipmentIds\":[\"2\"]}");
    var q = ListQuery.from(Map.of("q", "QA-INS-"));
    var all = new PermissionScope(true, null, Set.of(), false);
    var selected =
        inspections.select(
            10001, 73001, "daily-tasks", all, all, false, null, q, SqlPredicate.of("1=1"), null);
    var row = inspections.page(selected, q).getFirst();
    assertEquals(2, ((Number) row.get("query_expected")).intValue());
    assertEquals(1, ((Number) row.get("query_checked")).intValue());
    assertEquals(1, ((Number) row.get("query_abnormal")).intValue());
    assertEquals(0, ((Number) row.get("query_missing")).intValue());
    assertEquals(1, ((Number) row.get("query_delayDays")).intValue());
    assertEquals(86400, ((Number) row.get("query_workSeconds")).intValue());
  }

  void warehouseFixtures() {
    db.update(
        "INSERT INTO inv_warehouse(id,tenant_id,company_id,code,name)"
            + " VALUES(890000001,10001,71002,'QA-WH-A','工程仓库A'),(890000002,10001,71006,'QA-WH-B','工程仓库B')");
  }

  @Test
  void inventoryMaterialAndReceiptQueriesPageBeyondTenThousand() {
    warehouseFixtures();
    var materials = new ArrayList<Object[]>();
    var receipts = new ArrayList<Object[]>();
    for (int i = 0; i < 10005; i++) {
      materials.add(
          new Object[] {900000000L + i, 10001, String.format("QA-MAT-%05d", i), "工程物料 " + i, "件"});
      receipts.add(
          new Object[] {
            910000000L + i, 10001, String.format("QA-RK-%05d", i), 71002, 890000001, 73001
          });
    }
    db.batchUpdate(
        "INSERT INTO inv_material(id,tenant_id,code,name,unit) VALUES(?,?,?,?,?)", materials);
    db.batchUpdate(
        "INSERT INTO inv_receipt(id,tenant_id,code,company_id,warehouse_id,created_by)"
            + " VALUES(?,?,?,?,?,?)",
        receipts);
    var q =
        ListQuery.from(Map.of("q", "QA-MAT-", "size", "200", "sortBy", "code", "direction", "asc"));
    var selected = inventory.masters(10001, "materials", q, Set.of(), false);
    assertEquals(10005, inventory.count(selected));
    recordPlan("inventory-list", selected.from(), selected.predicate(), selected.order());
    var ids = new HashSet<String>();
    for (int page = 1; page <= 51; page++)
      for (var row : inventory.page(selected, q.at(page, 200)))
        assertTrue(ids.add(row.get("id").toString()));
    assertEquals(10005, ids.size());
    q = ListQuery.from(Map.of("q", "QA-RK-", "page", "51", "size", "200"));
    selected = inventory.receipts(10001, q, Set.of(890000001L), SqlPredicate.of("1=1"), null);
    assertEquals(10005, inventory.count(selected));
    recordPlan("inventory-list", selected.from(), selected.predicate(), selected.order());
    assertEquals(5, inventory.page(selected, q).size());
    assertEquals(
        0,
        inventory.count(
            inventory.receipts(10001, q, Set.of(890000002L), SqlPredicate.of("1=1"), null)));
  }

  @Test
  void outboundUnionKeepsLegacyIdentityAndScope() {
    warehouseFixtures();
    db.update(
        "INSERT INTO"
            + " inv_issue_group(id,tenant_id,code,warehouse_id,confirmation_key,actor_id,actor_name)"
            + " VALUES(920000001,10001,'QA-CK-LEGACY',890000001,'qa-group-key',73001,'工程仓管')");
    db.update(
        "INSERT INTO"
            + " inv_issue(id,tenant_id,code,company_id,warehouse_id,confirmation_key,preview_fingerprint,total_amount,actor_id,actor_name,source_kind,status,group_id)"
            + " VALUES(921000001,10001,'QA-CK-ONE',71002,890000001,'qa-key-1','qa-fp',12.34,73001,'工程仓管','直接出库','已出库',NULL),(921000002,10001,'QA-CK-SUB',71002,890000001,'qa-key-2','qa-fp',5.67,73001,'工程仓管','历史领料','已出库',920000001),(921000003,10001,'QA-CK-OTHER',71006,890000002,'qa-key-3','qa-fp',88,73001,'工程仓管','直接出库','已出库',NULL)");
    var q = ListQuery.from(Map.of("q", "QA-CK-"));
    var selected =
        inventory.outbounds(10001, q, Set.of(890000001L), true, SqlPredicate.of("1=1"), null);
    assertEquals(2, inventory.count(selected));
    var rows = inventory.page(selected, q);
    assertEquals(
        Set.of("921000001", "g920000001"),
        rows.stream()
            .map(row -> row.get("id").toString())
            .collect(java.util.stream.Collectors.toSet()));
    assertEquals(
        new java.math.BigDecimal("5.67"),
        rows.stream()
            .filter(row -> row.get("id").toString().startsWith("g"))
            .findFirst()
            .orElseThrow()
            .get("total_amount"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            inventory.outbounds(
                10001,
                ListQuery.from(Map.of("filters", "{\"totalAmount\":\"5\"}")),
                Set.of(890000001L),
                false,
                SqlPredicate.of("1=1"),
                null));
  }

  @Test
  void requisitionPartialReservationAndOwnAccessAreComputedBeforePaging() {
    warehouseFixtures();
    db.update(
        "INSERT INTO"
            + " inv_requisition(id,tenant_id,code,company_id,warehouse_id,task_kind,task_id,task_code,applicant_id,applicant_name,status)"
            + " VALUES(930000001,10001,'QA-LL-ONE',71002,890000001,'维修',1,'QA-TASK',73001,'工程人员','部分发料'),(930000002,10001,'QA-LL-OTHER',71006,890000002,'维修',2,'QA-TASK2',73002,'其他人员','待预留')");
    db.update(
        "INSERT INTO"
            + " inv_requisition_line(id,tenant_id,requisition_id,line_no,material_id,quantity,issued_quantity)"
            + " VALUES(931000001,10001,930000001,1,1,10,3)");
    db.update(
        "INSERT INTO"
            + " inv_reservation(id,tenant_id,requisition_id,requisition_line_id,balance_id,quantity,remaining_quantity)"
            + " VALUES(932000001,10001,930000001,931000001,1,4,4)");
    var q =
        ListQuery.from(
            Map.of(
                "q",
                "QA-LL-",
                "conditions",
                "[{\"field\":\"remainingQuantity\",\"operator\":\"range\",\"values\":[\"7\",\"7\"]}]"));
    var selected = inventory.requisitions(10001, 73001, q, Set.of(), true, false);
    assertEquals(1, inventory.count(selected));
    var row = inventory.page(selected, q).getFirst();
    assertEquals("部分出库", row.get("business_status"));
    assertEquals("部分预留", row.get("reservation_status"));
    assertEquals(new java.math.BigDecimal("7.000000"), row.get("remaining_quantity"));
    assertEquals(
        0, inventory.count(inventory.requisitions(10001, 73001, q, Set.of(), false, true)));
  }

  @Test
  void userPagingDoesNotExposeSecretsOrOtherDepartments() {
    var rows = new ArrayList<Object[]>();
    for (int i = 0; i < 10005; i++)
      rows.add(
          new Object[] {
            940000000L + i, 10001, String.format("qa-engineering-%05d", i), "工程用户 " + i, 71003
          });
    db.batchUpdate(
        "INSERT INTO eam_sys_user(id,tenant_id,account,display_name,org_id) VALUES(?,?,?,?,?)",
        rows);
    var q = ListQuery.from(Map.of("q", "qa-engineering-", "size", "200"));
    var selected = userLists.select(10001, q, false, Set.of(71003L), null, true);
    assertEquals(10005, userLists.count(selected));
    recordPlan("user-authorized-list", selected.from(), selected.predicate(), selected.order());
    assertEquals(5, userLists.page(selected, q.at(51, 200)).size());
    assertFalse(userLists.page(selected, q).getFirst().containsKey("password_hash"));
    assertEquals(0, userLists.count(userLists.select(10001, q, false, Set.of(71007L), null, true)));
  }

  @Test
  void typedExtensionExistsDoesNotDuplicateMultiSelectAndPreservesZero() {
    String id = "da1992d6-1216-471e-bc1a-2ba876668787";
    db.update(
        "INSERT INTO eam_business_field(id,tenant_id,object_key,data_type)"
            + " VALUES(?,10001,'maintenance_plan','NUMBER')",
        id);
    db.update(
        "INSERT INTO eam_business_value(tenant_id,object_key,record_id,field_id,number_value)"
            + " VALUES(10001,'maintenance_plan',860000000,?,0),(10001,'maintenance_plan',860000001,?,10)",
        id,
        id);
    var term =
        new com.windasoft.eam.dto.business.BusinessFilterTerm(
            id,
            "number_value",
            com.windasoft.eam.dto.business.BusinessFilterTerm.Operator.RANGE,
            Arrays.asList(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO),
            false);
    var predicate = extensions.predicate(10001, "maintenance_plan", "t", List.of(term));
    var q = ListQuery.from(Map.of("q", "QA-ENG-"));
    var selected =
        repository.select(
            10001,
            73001,
            "plans",
            new PermissionScope(true, null, Set.of(), false),
            false,
            null,
            q,
            predicate,
            null);
    assertEquals(1, repository.count(selected));
    assertEquals("860000000", repository.page(selected, q).getFirst().get("id").toString());
  }

  @Test
  void maintenanceServiceAndCsvShareTheSameAuthorizedQuery() {
    var user = new com.windasoft.eam.entity.system.SystemUser();
    user.setId(73001L);
    user.setTenantId(10001L);
    user.setOrgId(71003L);
    user.setStatus(1);
    user.setDeleted(0);
    user.setDisplayName("工程管理员");
    com.windasoft.eam.service.system.SystemExecution.run(
        user,
        () -> {
          var q = ListQuery.from(Map.of("q", "QA-ENG-1000"));
          var result = maintenanceLists.query("plans", q);
          assertEquals(5, result.page().total());
          assertEquals(5, result.data().size());
          var text = new java.io.StringWriter();
          maintenanceCsv.write(
              "plans", q, "[\"code\",\"name\"]", () -> new java.io.PrintWriter(text));
          assertEquals(6, text.toString().lines().count());
          return null;
        });
  }

  @Test
  void maintenanceAcceptanceCountAndPageAgreeForStringAndNumericParticipants() {
    db.update(
        "INSERT INTO"
            + " eam_maintenance_task(id,tenant_id,code,plan_id,schedule_id,equipment_id,company_id,department_id,due_date,status,snapshot)"
            + " VALUES(950000001,10001,'QA-ACCEPT-STRING',860000000,1,1,71002,71003,'2026-10-17','待验收',JSON_OBJECT('reviewers',JSON_ARRAY('73001'))),(950000002,10001,'QA-ACCEPT-NUMBER',860000000,2,1,71002,71003,'2026-10-17','待验收',JSON_OBJECT('reviewers',JSON_ARRAY(73001)))");
    com.windasoft.eam.support.RelationalTaskFixtures.capture(db);
    var scope = new PermissionScope(true, null, Set.of(), false);
    var q = ListQuery.from(Map.of("queue", "待我验收", "q", "QA-ACCEPT-"));
    var selection =
        repository.select(
            10001, 73001, "tasks", scope, false, null, q, SqlPredicate.of("1=1"), null);
    selection = repository.restrict(selection, repository.acceptance(scope, 73001));
    assertEquals(2, repository.count(selection));
    assertEquals(2, repository.page(selection, q).size());
  }

  @Test
  void maintenanceExecuteSubmitReturnAndAcceptPreserveVersionsAndStock() {
    maintenanceWorkflowFixtures();
    var user = new com.windasoft.eam.entity.system.SystemUser();
    user.setId(73001L);
    user.setTenantId(10001L);
    user.setOrgId(71003L);
    user.setStatus(1);
    user.setDeleted(0);
    user.setDisplayName("工程管理员");
    var before =
        db.queryForObject(
            "SELECT COALESCE(SUM(quantity),0) FROM inv_balance WHERE tenant_id=10001",
            java.math.BigDecimal.class);
    com.windasoft.eam.service.system.SystemExecution.run(
        user,
        () -> {
          var started = maintenanceTasks.action(960000001, "start", Map.of("rowVersion", 0));
          assertEquals("执行中", started.get("status"));
          var submitted =
              maintenanceTasks.action(
                  960000001,
                  "process",
                  Map.of(
                      "rowVersion",
                      1,
                      "items",
                      List.of(),
                      "startAt",
                      "2026-10-17T08:00:00",
                      "endAt",
                      "2026-10-17T09:00:00",
                      "customSchemaVersion",
                      0,
                      "customFields",
                      Map.of()));
          assertEquals("待验收", submitted.get("status"));
          var returned =
              maintenanceTasks.action(
                  960000001, "accept", Map.of("rowVersion", 2, "decision", "打回", "score", 3));
          assertEquals("执行中", returned.get("status"));
          maintenanceTasks.action(
              960000001,
              "process",
              Map.of(
                  "rowVersion",
                  3,
                  "items",
                  List.of(),
                  "startAt",
                  "2026-10-17T08:00:00",
                  "endAt",
                  "2026-10-17T10:00:00",
                  "customSchemaVersion",
                  0,
                  "customFields",
                  Map.of()));
          var accepted =
              maintenanceTasks.action(
                  960000001, "accept", Map.of("rowVersion", 4, "decision", "通过", "score", 5));
          assertEquals("已完成", accepted.get("status"));
          assertEquals(5, ((Number) accepted.get("rowVersion")).intValue());
          var error =
              assertThrows(
                  org.springframework.web.server.ResponseStatusException.class,
                  () ->
                      maintenanceTasks.action(
                          960000001,
                          "accept",
                          Map.of("rowVersion", 4, "decision", "通过", "score", 5)));
          assertEquals(409, error.getStatusCode().value());
          return null;
        });
    assertEquals(
        before,
        db.queryForObject(
            "SELECT COALESCE(SUM(quantity),0) FROM inv_balance WHERE tenant_id=10001",
            java.math.BigDecimal.class));
    assertEquals(
        5,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_maintenance_event WHERE tenant_id=10001 AND"
                + " task_id=960000001",
            Integer.class));
  }

  void maintenanceWorkflowFixtures() {
    db.update(
        "INSERT INTO"
            + " eam_equipment(id,tenant_id,equipment_code,name,category,company,department,company_org_id,department_org_id)"
            + " VALUES(970000001,10001,'QA-FLOW-EQ','工程设备','工程类别','工程公司','机加工一部',71002,71003)");
    db.update(
        "INSERT INTO"
            + " eam_maintenance_task(id,tenant_id,code,plan_id,schedule_id,equipment_id,company_id,department_id,due_date,status,snapshot)"
            + " VALUES(960000001,10001,'QA-WORKFLOW',860000000,50,970000001,71002,71003,?,'待执行',JSON_OBJECT('owners',JSON_ARRAY('73001'),'reviewers',JSON_ARRAY('73001'),'items',JSON_ARRAY(),'equipmentName','工程设备','calculation','固定间隔'))",
        java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
    com.windasoft.eam.support.RelationalTaskFixtures.capture(db);
  }

  @Test
  void inspectionDraftSubmitReturnAndReviewPreserveTheSameTaskAndCost() {
    maintenanceWorkflowFixtures();
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    String itemId = "970000001/standard:item";
    var item =
        Map.of(
            "id",
            itemId,
            "equipmentId",
            "970000001",
            "name",
            "温度",
            "required",
            true,
            "valueType",
            "数值",
            "lower",
            "0",
            "upper",
            "100");
    var snapshot =
        Map.of(
            "owners",
            List.of("73001"),
            "reviewers",
            List.of("73001"),
            "items",
            List.of(item),
            "equipment",
            List.of(Map.of("id", "970000001", "code", "QA-FLOW-EQ", "name", "工程设备")),
            "reviewMode",
            "整体审核",
            "scanMode",
            "无需扫码",
            "planName",
            "工程巡检",
            "equipmentName",
            "工程设备");
    String encoded;
    try {
      encoded = json.writeValueAsString(snapshot);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
    db.update(
        "INSERT INTO"
            + " eam_inspection_task(id,tenant_id,kind,code,plan_id,equipment_id,company_id,department_id,due_at,expires_at,status,snapshot)"
            + " VALUES(980000001,10001,'periodic','QA-INS-WORKFLOW',860000000,0,71002,71003,?,?,'待执行',CAST(?"
            + " AS JSON))",
        now.minusHours(1),
        now.plusDays(1),
        encoded);
    com.windasoft.eam.support.RelationalTaskFixtures.capture(db);
    var user = new com.windasoft.eam.entity.system.SystemUser();
    user.setId(73001L);
    user.setTenantId(10001L);
    user.setOrgId(71003L);
    user.setStatus(1);
    user.setDeleted(0);
    user.setDisplayName("工程管理员");
    var before =
        db.queryForObject(
            "SELECT COALESCE(SUM(quantity),0) FROM inv_balance WHERE tenant_id=10001",
            java.math.BigDecimal.class);
    com.windasoft.eam.service.system.SystemExecution.run(
        user,
        () -> {
          assertEquals(
              "执行中",
              inspectionTasks.action(980000001, "start", Map.of("rowVersion", 0)).get("status"));
          var answers = List.of(Map.of("id", itemId, "value", "0"));
          assertEquals(
              "执行中",
              inspectionTasks
                  .action(
                      980000001,
                      "draft",
                      Map.of(
                          "rowVersion",
                          1,
                          "items",
                          answers,
                          "customFields",
                          Map.of(),
                          "customSchemaVersion",
                          0))
                  .get("status"));
          assertEquals(
              "待审核",
              inspectionTasks
                  .action(
                      980000001,
                      "submit",
                      Map.of(
                          "rowVersion",
                          2,
                          "items",
                          answers,
                          "customFields",
                          Map.of(),
                          "customSchemaVersion",
                          0))
                  .get("status"));
          assertEquals(
              "审核不通过",
              inspectionTasks
                  .action(
                      980000001,
                      "review",
                      Map.of("rowVersion", 3, "decision", "打回", "description", "补充检查"))
                  .get("status"));
          inspectionTasks.action(980000001, "start", Map.of("rowVersion", 4));
          inspectionTasks.action(
              980000001,
              "submit",
              Map.of(
                  "rowVersion",
                  5,
                  "items",
                  answers,
                  "customFields",
                  Map.of(),
                  "customSchemaVersion",
                  0));
          var complete =
              inspectionTasks.action(
                  980000001, "review", Map.of("rowVersion", 6, "decision", "通过"));
          assertEquals("已审核", complete.get("status"));
          assertEquals(1, ((Number) complete.get("checked")).intValue());
          assertEquals(0, ((Number) complete.get("abnormal")).intValue());
          assertEquals(7, ((Number) complete.get("rowVersion")).intValue());
          return null;
        });
    assertEquals(
        before,
        db.queryForObject(
            "SELECT COALESCE(SUM(quantity),0) FROM inv_balance WHERE tenant_id=10001",
            java.math.BigDecimal.class));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_equipment_history WHERE tenant_id=10001 AND"
                + " equipment_id=970000001 AND record_no='QA-INS-WORKFLOW'",
            Integer.class));
  }

  @Test
  void maintenanceAndInspectionQueryPlansAreRecordedForReview() {
    var q = ListQuery.from(Map.of());
    var scope = new PermissionScope(false, null, Set.of(71003L), false);
    var selected = selection(q, scope);
    recordPlan(
        "maintenance-plan-department", selected.from(), selected.predicate(), selected.order());
    inspectionFixtures(10005);
    var tasks =
        inspections.select(
            10001,
            99999,
            "daily-tasks",
            new PermissionScope(true, null, Set.of(), false),
            scope,
            false,
            null,
            q,
            SqlPredicate.of("1=1"),
            null);
    recordPlan("inspection-task-list", tasks.from(), tasks.predicate(), tasks.order());
  }

  @Test
  void receiptLayersPreviewAndIdempotentOutboundsReconcileActualCost() {
    warehouseFixtures();
    // The migration deliberately does not grant direct issues to legacy issue roles.
    // This isolated warehouse-operator fixture explicitly grants the actions under test.
    grantDirectOutbounds();
    var user = new com.windasoft.eam.entity.system.SystemUser();
    user.setId(73001L);
    user.setTenantId(10001L);
    user.setOrgId(71003L);
    user.setStatus(1);
    user.setDeleted(0);
    user.setDisplayName("工程管理员");
    com.windasoft.eam.service.system.SystemExecution.run(
        user,
        () -> {
          Long unit =
              db.queryForObject(
                  "SELECT id FROM eam_base_item WHERE tenant_id=10001 AND item_type='UNIT' AND"
                      + " deleted=0 AND status=1 ORDER BY id LIMIT 1",
                  Long.class);
          var material =
              inventoryMasters.save(
                  "materials",
                  null,
                  new HashMap<>(
                      Map.of(
                          "name",
                          "工程成本回归",
                          "code",
                          "QA-FIFO-COST",
                          "unitId",
                          unit.toString(),
                          "usageType",
                          "消耗型",
                          "enabled",
                          true,
                          "batchEnabled",
                          true,
                          "allocationRule",
                          "FIFO")));
          long materialId = Long.parseLong(material.get("id").toString());
          assertEquals(unit.toString(), material.get("unitId"));
          for (var line :
              List.of(
                  Map.of(
                      "materialId",
                      materialId,
                      "quantity",
                      "3",
                      "unitPrice",
                      "10.12345",
                      "allocatedFee",
                      "0.01",
                      "batchCode",
                      "QA-SAME-BATCH"),
                  Map.of(
                      "materialId",
                      materialId,
                      "quantity",
                      "2",
                      "unitPrice",
                      "11",
                      "allocatedFee",
                      "0.02",
                      "batchCode",
                      "QA-SAME-BATCH"))) {
            var receipt =
                receiptCommands.save(
                    null,
                    new HashMap<>(
                        Map.of(
                            "warehouseId",
                            "890000001",
                            "lines",
                            List.of(new HashMap<>(line)),
                            "customSchemaVersion",
                            0,
                            "customFields",
                            Map.of())));
            long receiptId = Long.parseLong(receipt.get("id").toString());
            receiptCommands.confirm(receiptId, Map.of("rowVersion", receipt.get("rowVersion")));
            receiptCommands.confirm(receiptId, Map.of("rowVersion", receipt.get("rowVersion")));
          }
          assertEquals(new java.math.BigDecimal("5.000000"), stockQuantity(materialId));
          assertEquals(
              2,
              db.queryForObject(
                  "SELECT COUNT(*) FROM inv_cost_layer c JOIN inv_balance b ON"
                      + " b.tenant_id=c.tenant_id AND b.id=c.balance_id WHERE b.tenant_id=10001 AND"
                      + " b.material_id=?",
                  Integer.class,
                  materialId));
          java.math.BigDecimal total = java.math.BigDecimal.ZERO;
          int index = 0;
          for (String quantity : List.of("4", "1")) {
            var saved =
                (Map<String, Object>)
                    outboundCommands.save(
                        null,
                        new HashMap<>(
                            Map.of(
                                "sourceKind",
                                "直接出库",
                                "warehouseId",
                                "890000001",
                                "departmentId",
                                "71003",
                                "recipientId",
                                "73001",
                                "purpose",
                                "工程测试",
                                "lines",
                                List.of(Map.of("materialId", materialId, "quantity", quantity)),
                                "customFields",
                                Map.of(),
                                "customSchemaVersion",
                                0)));
            long id = Long.parseLong(saved.get("id").toString());
            var before = stockQuantity(materialId);
            var preview =
                (Map<String, Object>)
                    outboundCommands.preview(id, Map.of("rowVersion", saved.get("rowVersion")));
            assertEquals(before, stockQuantity(materialId));
            var confirmation = new HashMap<>(preview);
            confirmation.put("confirmationKey", "qa-engineering-confirm-" + (index++));
            var issued = (Map<String, Object>) outboundCommands.confirm(id, confirmation);
            var after = stockQuantity(materialId);
            outboundCommands.confirm(id, confirmation);
            assertEquals(after, stockQuantity(materialId));
            var amount = new java.math.BigDecimal(issued.get("totalAmount").toString());
            assertEquals(
                new java.math.BigDecimal(quantity.equals("4") ? "41.39" : "11.01"), amount);
            total = total.add(amount);
          }
          assertEquals(new java.math.BigDecimal("52.40"), total);
          assertEquals(new java.math.BigDecimal("0.000000"), stockQuantity(materialId));
          assertEquals(
              0,
              db.queryForObject(
                  "SELECT COUNT(*) FROM inv_cost_layer c JOIN inv_balance b ON b.id=c.balance_id"
                      + " AND b.tenant_id=c.tenant_id WHERE b.tenant_id=10001 AND b.material_id=?"
                      + " AND (c.remaining_quantity<>0 OR c.remaining_cost<>0)",
                  Integer.class,
                  materialId));
          return null;
        });
  }

  @Test
  void equipmentCreateCopyAndRelationsPersistStableIdsAndTypedExtensionValues() {
    warehouseFixtures();
    db.update(
        "INSERT INTO eam_base_item(id,tenant_id,item_type,code,name,company_org_id)"
            + " VALUES(990000001,10001,'LOCATION','QA-LOCATION','工程位置',71002)");
    db.update(
        "INSERT INTO inv_material(id,tenant_id,code,name,unit,enabled)"
            + " VALUES(990000002,10001,'QA-BOM-MAT','工程BOM物料','个',1)");
    String field = "65de3d42-2065-4a0e-8aee-a3404366a211";
    db.update(
        "INSERT INTO eam_business_field(id,tenant_id,object_key,data_type,ever_published)"
            + " VALUES(?,10001,'equipment','NUMBER',1)",
        field);
    db.update(
        "UPDATE eam_business_schema SET"
            + " published_version=1,published=JSON_OBJECT('fields',JSON_ARRAY(JSON_OBJECT('id',?,'name','额定功率','type','NUMBER','enabled',true,'required',true)))"
            + " WHERE tenant_id=10001 AND object_key='equipment'",
        field);
    com.windasoft.eam.service.system.SystemExecution.run(
        engineeringUser(),
        () -> {
          var input = equipmentInput("QA-ENGINEERING-CHILD");
          input.put("customFields", Map.of(field, "0"));
          input.put("customSchemaVersion", 1);
          var child =
              equipmentCommands.create(
                  json.convertValue(input, com.windasoft.eam.dto.equipment.EquipmentForm.class));
          assertEquals("990000001", child.locationId());
          assertEquals(List.of("73001"), child.ownerIds());
          assertEquals("0.000000", child.customFields().get(field));
          input.put("code", "");
          input.put("name", "工程复制");
          var copied =
              equipmentCommands.create(
                  json.convertValue(input, com.windasoft.eam.dto.equipment.EquipmentForm.class));
          assertNotEquals(child.code(), copied.code());
          assertEquals(child.customFields(), copied.customFields());
          input.put("code", "QA-ENGINEERING-PARENT");
          input.put("children", List.of(Map.of("equipmentId", child.id())));
          input.put(
              "relatedSpares", List.of(Map.of("materialId", "990000002", "quantity", "2.00")));
          var parent =
              equipmentCommands.create(
                  json.convertValue(input, com.windasoft.eam.dto.equipment.EquipmentForm.class));
          assertEquals(child.id(), parent.children().getFirst().get("equipmentId"));
          assertEquals("990000002", parent.relatedSpares().getFirst().get("materialId"));
          input.put("name", "工程更新");
          var updated =
              equipmentCommands.update(
                  Long.parseLong(parent.id()),
                  json.convertValue(input, com.windasoft.eam.dto.equipment.EquipmentForm.class),
                  0);
          assertEquals(1, updated.rowVersion());
          assertEquals("工程更新", updated.name());
          assertEquals(1, updated.children().size());
          var error =
              assertThrows(
                  IllegalArgumentException.class,
                  () -> equipmentCommands.delete(Long.parseLong(child.id())));
          assertTrue(error.getMessage().contains("关联"));
          return null;
        });
  }

  Map<String, Object> equipmentInput(String code) {
    var input = new HashMap<String, Object>();
    input.put("code", code);
    input.put("name", "工程设备");
    input.put("state", "正常");
    input.put("departmentOrgId", 71003L);
    input.put("locationId", 990000001L);
    input.put("ownerIds", List.of(73001L));
    input.put("customFields", Map.of());
    input.put("customSchemaVersion", 0);
    for (var field :
        Map.of("categoryId", "CATEGORY", "unitId", "UNIT", "useStateId", "USE_STATUS").entrySet())
      input.put(field.getKey(), activeBase(field.getValue()));
    return input;
  }

  long activeBase(String type) {
    return db.queryForObject(
        "SELECT id FROM eam_base_item WHERE tenant_id=10001 AND item_type=? AND status=1 AND"
            + " deleted=0 ORDER BY id LIMIT 1",
        Long.class,
        type);
  }

  com.windasoft.eam.entity.system.SystemUser engineeringUser() {
    var user = new com.windasoft.eam.entity.system.SystemUser();
    user.setId(73001L);
    user.setTenantId(10001L);
    user.setOrgId(71003L);
    user.setStatus(1);
    user.setDeleted(0);
    user.setDisplayName("工程管理员");
    return user;
  }

  @Test
  void repairReportEditDispatchClaimProcessReturnAndAcceptanceKeepOneOrder() {
    maintenanceWorkflowFixtures();
    db.update(
        "INSERT INTO eam_base_item(id,tenant_id,item_type,code,name)"
            + " VALUES(990000003,10001,'FAULT_CAUSE','QA-FAULT-CAUSE','工程原因')");
    com.windasoft.eam.service.system.SystemExecution.run(
        engineeringUser(),
        () -> {
          var body = new HashMap<String, Object>();
          body.put("equipmentCode", "QA-FLOW-EQ");
          body.put("equipmentName", "伪造设备名称");
          body.put("faultTypeId", activeBase("FAULT_TYPE"));
          body.put("severity", "一般");
          body.put("description", "工程报修");
          body.put("reporter", "伪造报修人");
          body.put("customFields", Map.of());
          body.put("customSchemaVersion", 0);
          var report =
              (Map<String, Object>)
                  repairCommands
                      .create(
                          json.convertValue(body, com.windasoft.eam.dto.repair.RepairForm.class))
                      .get("data");
          long id = Long.parseLong(report.get("id").toString());
          assertEquals("工程设备", report.get("equipmentName"));
          assertEquals("工程管理员", report.get("reporter"));
          assertEquals("待派工", report.get("status"));
          body.put("description", "工程报修更新");
          repairCommands.update(
              id, json.convertValue(body, com.windasoft.eam.dto.repair.RepairForm.class), 0);
          assertEquals(
              "待接单",
              repairWorkflow
                  .action(
                      id,
                      1,
                      json.valueToTree(Map.of("action", "dispatch", "userIds", List.of(73001L))))
                  .getStatus());
          assertEquals(
              "处理中",
              repairWorkflow
                  .action(id, 2, json.valueToTree(Map.of("action", "claim")))
                  .getStatus());
          long resolved =
              db.queryForObject(
                  "SELECT id FROM eam_base_item WHERE tenant_id=10001 AND"
                      + " item_type='PROCESS_RESULT' AND code='SYS_RESOLVED' AND deleted=0",
                  Long.class);
          var process =
              Map.of(
                  "action",
                  "process",
                  "content",
                  "工程修复",
                  "repairMethod",
                  "自修",
                  "faultTypeId",
                  activeBase("FAULT_TYPE"),
                  "faultCauseId",
                  activeBase("FAULT_CAUSE"),
                  "processingResultId",
                  resolved,
                  "customFields",
                  Map.of(),
                  "customSchemaVersion",
                  0);
          assertEquals("待验收", repairWorkflow.action(id, 3, json.valueToTree(process)).getStatus());
          assertEquals(
              "处理中",
              repairWorkflow
                  .action(
                      id,
                      4,
                      json.valueToTree(Map.of("action", "return", "score", 3, "comment", "工程打回")))
                  .getStatus());
          repairWorkflow.action(id, 5, json.valueToTree(process));
          var complete =
              repairWorkflow.action(
                  id,
                  6,
                  json.valueToTree(Map.of("action", "accept", "score", 5, "comment", "工程验收通过")));
          assertEquals("已完成", complete.getStatus());
          assertNotNull(complete.getAcceptedAt());
          assertEquals(73001L, complete.getAcceptedOwnerId());
          var stale =
              assertThrows(
                  org.springframework.web.server.ResponseStatusException.class,
                  () ->
                      repairWorkflow.action(
                          id,
                          6,
                          json.valueToTree(
                              Map.of("action", "accept", "score", 5, "comment", "重复提交"))));
          assertEquals(409, stale.getStatusCode().value());
          return null;
        });
  }

  @Test
  void equipmentAndRepairSqlQueriesKeepTotalsBeyondTenThousandAndExportsMatch() {
    var equipmentRows = new ArrayList<Object[]>();
    var repairRows = new ArrayList<Object[]>();
    for (int i = 0; i < 10005; i++) {
      equipmentRows.add(
          new Object[] {
            991000000L + i,
            10001L,
            String.format("QA-PAGE-EQ-%05d", i),
            "工程分页设备",
            "工程分类",
            "工程公司",
            "工程部门",
            71002L,
            71003L,
            "正常"
          });
      repairRows.add(
          new Object[] {
            992000000L + i,
            10001L,
            String.format("QA-PAGE-REPAIR-%05d", i),
            String.format("QA-PAGE-EQ-%05d", i),
            "工程分页设备",
            "工程类型",
            "工程描述",
            "工程报修人",
            73001L,
            71002L,
            71003L
          });
    }
    db.batchUpdate(
        "INSERT INTO"
            + " eam_equipment(id,tenant_id,equipment_code,name,category,company,department,company_org_id,department_org_id,equipment_status)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
        equipmentRows);
    db.batchUpdate(
        "INSERT INTO"
            + " eam_repair_order(id,tenant_id,order_no,equipment_code,equipment_name,fault_type,description,reporter,reporter_user_id,company_org_id,department_org_id)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        repairRows);
    com.windasoft.eam.service.system.SystemExecution.run(
        engineeringUser(),
        () -> {
          var input =
              new HashMap<String, Object>(
                  Map.of(
                      "page",
                      1,
                      "size",
                      200,
                      "keyword",
                      "QA-PAGE-EQ-",
                      "sortBy",
                      "code",
                      "direction",
                      "asc"));
          var query =
              json.convertValue(input, com.windasoft.eam.dto.equipment.EquipmentListQuery.class);
          assertEquals(10005, equipmentQueries.list(query).page().total());
          var last = equipmentQueries.list(query.at(51, 200));
          assertEquals(5, last.data().size());
          assertEquals("QA-PAGE-EQ-10000", last.data().getFirst().code());
          // The first ten codes all share a timestamp except the highest code,
          // which is deliberately earlier. Sorting by "code" must not fall back to createdAt.
          db.update(
              "UPDATE eam_equipment SET created_at='2026-01-02 00:00:00'"
                  + " WHERE tenant_id=10001 AND equipment_code LIKE 'QA-PAGE-EQ-0000%'");
          db.update(
              "UPDATE eam_equipment SET created_at='2026-01-01 00:00:00'"
                  + " WHERE tenant_id=10001 AND equipment_code='QA-PAGE-EQ-00009'");
          input.put("keyword", "QA-PAGE-EQ-0000");
          var codeSorted =
              equipmentQueries.list(
                  json.convertValue(
                      input, com.windasoft.eam.dto.equipment.EquipmentListQuery.class));
          assertEquals(10, codeSorted.page().total());
          assertEquals("QA-PAGE-EQ-00000", codeSorted.data().getFirst().code());
          assertEquals("QA-PAGE-EQ-00009", codeSorted.data().getLast().code());
          input.put("keyword", "QA-PAGE-EQ-1000");
          var writer = new java.io.StringWriter();
          equipmentQueries.export(
              json.convertValue(input, com.windasoft.eam.dto.equipment.EquipmentListQuery.class),
              List.of("code", "name"),
              new java.io.PrintWriter(writer));
          assertEquals(6, writer.toString().lines().count());
          var parameters =
              new HashMap<String, String>(
                  Map.of(
                      "keyword",
                      "QA-PAGE-REPAIR-",
                      "page",
                      "51",
                      "size",
                      "200",
                      "sortBy",
                      "orderNo",
                      "direction",
                      "asc"));
          var repairs = repairQueries.list(parameters);
          assertEquals(10005, repairs.page().getTotal());
          assertEquals(5, repairs.page().getRecords().size());
          assertEquals(10005, repairs.counts().get("all"));
          parameters.put("keyword", "QA-PAGE-REPAIR-1000");
          parameters.put("columns", "orderNo,equipmentCode");
          var output = new java.io.StringWriter();
          repairQueries.export(parameters, new java.io.PrintWriter(output));
          assertEquals(6, output.toString().lines().count());
          return null;
        });
  }

  @Test
  void outboundFailureAfterLedgerWritesRollsBackStockCostDocumentAndNotice() {
    warehouseFixtures();
    grantDirectOutbounds();
    com.windasoft.eam.service.system.SystemExecution.run(
        engineeringUser(),
        () -> {
          var material =
              inventoryMasters.save(
                  "materials",
                  null,
                  new HashMap<>(
                      Map.of(
                          "name",
                          "工程事务回归",
                          "code",
                          "QA-ROLLBACK-COST",
                          "unitId",
                          Long.toString(activeBase("UNIT")),
                          "usageType",
                          "消耗型",
                          "enabled",
                          true,
                          "batchEnabled",
                          false)));
          long materialId = Long.parseLong(material.get("id").toString());
          var receipt =
              receiptCommands.save(
                  null,
                  new HashMap<>(
                      Map.of(
                          "warehouseId",
                          "890000001",
                          "lines",
                          List.of(
                              Map.of("materialId", materialId, "quantity", "2", "unitPrice", "5")),
                          "customFields",
                          Map.of(),
                          "customSchemaVersion",
                          0)));
          receiptCommands.confirm(
              Long.parseLong(receipt.get("id").toString()),
              Map.of("rowVersion", receipt.get("rowVersion")));
          var saved =
              (Map<String, Object>)
                  outboundCommands.save(
                      null,
                      new HashMap<>(
                          Map.of(
                              "sourceKind",
                              "直接出库",
                              "warehouseId",
                              "890000001",
                              "departmentId",
                              "71003",
                              "recipientId",
                              "73001",
                              "purpose",
                              "工程事务回归",
                              "lines",
                              List.of(Map.of("materialId", materialId, "quantity", "1")),
                              "customFields",
                              Map.of(),
                              "customSchemaVersion",
                              0)));
          long id = Long.parseLong(saved.get("id").toString());
          var body =
              new HashMap<>(
                  (Map<String, Object>)
                      outboundCommands.preview(id, Map.of("rowVersion", saved.get("rowVersion"))));
          body.put("confirmationKey", "qa-engineering-rollback-confirm");
          org.mockito.Mockito.doThrow(new IllegalStateException("工程故障注入：库存写入后失败"))
              .when(outboundRepository)
              .insertEvent(
                  org.mockito.ArgumentMatchers.anyLong(),
                  org.mockito.ArgumentMatchers.eq(10001L),
                  org.mockito.ArgumentMatchers.eq(id),
                  org.mockito.ArgumentMatchers.any(),
                  org.mockito.ArgumentMatchers.anyLong(),
                  org.mockito.ArgumentMatchers.any());
          var nested =
              new org.springframework.transaction.support.TransactionTemplate(transactions);
          nested.setPropagationBehavior(
              org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
          var failure =
              assertThrows(
                  IllegalStateException.class,
                  () -> nested.execute(status -> outboundCommands.confirm(id, body)));
          assertTrue(failure.getMessage().contains("工程故障注入"));
          assertEquals(
              0,
              db.queryForObject(
                  "SELECT COUNT(*) FROM inv_transaction WHERE tenant_id=10001 AND document_id=?",
                  Integer.class,
                  id));
          assertEquals(
              0,
              db.queryForObject(
                  "SELECT COUNT(*) FROM inv_inbox WHERE tenant_id=10001 AND route_kind='outbounds'"
                      + " AND document_id=?",
                  Integer.class,
                  id));
          assertEquals(new java.math.BigDecimal("2.000000"), stockQuantity(materialId));
          assertEquals(
              "草稿",
              db.queryForObject(
                  "SELECT status FROM inv_issue WHERE tenant_id=10001 AND id=?", String.class, id));
          assertEquals(
              0,
              db.queryForObject(
                  "SELECT COUNT(*) FROM inv_issue_allocation WHERE tenant_id=10001 AND issue_id=?",
                  Integer.class,
                  id));
          assertEquals(
              new java.math.BigDecimal("10.000000"),
              db.queryForObject(
                  "SELECT SUM(c.remaining_cost) FROM inv_cost_layer c JOIN inv_balance b ON"
                      + " b.id=c.balance_id AND b.tenant_id=c.tenant_id WHERE b.tenant_id=10001 AND"
                      + " b.material_id=?",
                  java.math.BigDecimal.class,
                  materialId));
          return null;
        });
  }

  @Test
  void inventoryServiceEnforcesCompanyAndHidesAmountsInListExportAndFilters() {
    warehouseFixtures();
    db.update(
        "INSERT INTO"
            + " inv_issue(id,tenant_id,code,company_id,warehouse_id,confirmation_key,preview_fingerprint,total_amount,actor_id,actor_name,source_kind,status)"
            + " VALUES(994000001,10001,'QA-PERM-OWN',71002,890000001,'qa-perm-1','qa-fp',12.34,73001,'工程仓管','直接出库','已出库'),(994000002,10001,'QA-PERM-OTHER',71006,890000002,'qa-perm-2','qa-fp',88,73001,'工程仓管','直接出库','已出库')");
    companyOutboundGrant(true);
    com.windasoft.eam.service.system.SystemExecution.run(
        engineeringUser(),
        () -> {
          var result = inventoryLists.outbounds("QA-PERM-", "{}", "[]", 1, 10, false);
          assertEquals(1L, ((Map<?, ?>) result.get("page")).get("total"));
          var data = (List<Map<String, Object>>) result.get("data");
          assertEquals("QA-PERM-OWN", data.getFirst().get("code"));
          assertFalse(data.getFirst().containsKey("totalAmount"));
          var exported = inventoryLists.exportOutbounds("QA-PERM-", "{}", "[]");
          assertEquals(1, exported.size());
          assertFalse(exported.getFirst().containsKey("totalAmount"));
          var denied =
              assertThrows(
                  org.springframework.web.server.ResponseStatusException.class,
                  () -> outboundCommands.detail("994000002"));
          assertEquals(403, denied.getStatusCode().value());
          var amountFilter =
              assertThrows(
                  org.springframework.web.server.ResponseStatusException.class,
                  () ->
                      inventoryLists.outbounds(
                          "QA-PERM-", "{\"totalAmount\":\"12.34\"}", "[]", 1, 10, false));
          assertEquals(403, amountFilter.getStatusCode().value());
          return null;
        });
  }

  @Test
  void savingDueMaintenanceAndInspectionPlansGeneratesOnceAndKeepsTypeIds() {
    maintenanceWorkflowFixtures();
    var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
    com.windasoft.eam.service.system.SystemExecution.run(
        engineeringUser(),
        () -> {
          var type =
              maintenanceDefinitions.save(
                  "types",
                  null,
                  mutable(Map.of("name", "工程每日保养", "period", 1, "unit", "日", "enabled", true)));
          var standard =
              maintenanceDefinitions.save(
                  "standards",
                  null,
                  mutable(
                      Map.of(
                          "name",
                          "工程保养标准",
                          "departmentId",
                          "71003",
                          "equipmentIds",
                          List.of("970000001"),
                          "items",
                          List.of(
                              Map.of(
                                  "id",
                                  "qa-maint-item",
                                  "name",
                                  "工程检查",
                                  "content",
                                  "工程内容",
                                  "typeId",
                                  type.get("id"),
                                  "valueType",
                                  "文本")))));
          var plan =
              new HashMap<String, Object>(
                  Map.of(
                      "name",
                      "工程到期保养计划",
                      "enabled",
                      true,
                      "departmentId",
                      "71003",
                      "owners",
                      List.of("73001"),
                      "reviewers",
                      List.of("73001"),
                      "calculation",
                      "固定间隔",
                      "advanceDays",
                      0,
                      "remindDays",
                      0,
                      "customFields",
                      Map.of(),
                      "customSchemaVersion",
                      0));
          plan.put(
              "details",
              List.of(
                  Map.of(
                      "equipmentId",
                      "970000001",
                      "typeId",
                      type.get("id"),
                      "standardId",
                      standard.get("id"),
                      "nextDate",
                      today.toString(),
                      "validDays",
                      1)));
          var saved = maintenanceDefinitions.save("plans", null, mutable(plan));
          long id = Long.parseLong(saved.get("id").toString());
          assertEquals(
              1,
              db.queryForObject(
                  "SELECT COUNT(*) FROM eam_maintenance_task WHERE tenant_id=10001 AND plan_id=?"
                      + " AND type_id=?",
                  Integer.class,
                  id,
                  Long.parseLong(type.get("id").toString())));
          assertEquals(0, maintenanceDefinitions.generate(10001, id));
          plan.put("name", "工程未来保养计划");
          plan.put(
              "details",
              List.of(
                  Map.of(
                      "equipmentId",
                      "970000001",
                      "typeId",
                      type.get("id"),
                      "standardId",
                      standard.get("id"),
                      "nextDate",
                      today.plusDays(1).toString(),
                      "validDays",
                      1)));
          var future = maintenanceDefinitions.save("plans", null, mutable(plan));
          assertEquals(
              0,
              db.queryForObject(
                  "SELECT COUNT(*) FROM eam_maintenance_task WHERE tenant_id=10001 AND plan_id=?",
                  Integer.class,
                  Long.parseLong(future.get("id").toString())));
          var inspectionType =
              inspectionDefinitions.save(
                  "types",
                  null,
                  mutable(Map.of("name", "工程每日巡检", "period", 1, "unit", "日", "enabled", true)));
          var inspectionStandard =
              inspectionDefinitions.save(
                  "standards",
                  null,
                  mutable(
                      Map.of(
                          "name",
                          "工程巡检标准",
                          "departmentId",
                          "71003",
                          "equipmentIds",
                          List.of("970000001"),
                          "items",
                          List.of(
                              Map.of(
                                  "id",
                                  "qa-ins-item",
                                  "name",
                                  "工程检查",
                                  "content",
                                  "工程基准",
                                  "typeId",
                                  inspectionType.get("id"),
                                  "valueType",
                                  "文本")))));
          for (String kind : List.of("daily-plans", "periodic-plans")) {
            var inspectionPlan = new HashMap<String, Object>();
            inspectionPlan.put("name", "工程" + kind);
            inspectionPlan.put("departmentId", "71003");
            inspectionPlan.put("enabled", true);
            inspectionPlan.put("reviewMode", "无需验收");
            inspectionPlan.put("owners", List.of("73001"));
            inspectionPlan.put("validHours", 24);
            inspectionPlan.put("scanMode", "无需扫码");
            inspectionPlan.put("startDate", today.toString());
            inspectionPlan.put("endDate", today.plusDays(1).toString());
            inspectionPlan.put("typeId", inspectionType.get("id"));
            inspectionPlan.put("nextAt", today.atStartOfDay().toString());
            inspectionPlan.put("personMode", "指定负责人");
            inspectionPlan.put("restCycle", "按周");
            inspectionPlan.put(
                "shifts",
                List.of(Map.of("id", "qa-shift", "name", "全天", "start", "00:00", "end", "23:59")));
            inspectionPlan.put(
                "details",
                List.of(
                    Map.of(
                        "equipmentId",
                        "970000001",
                        "projectRefs",
                        List.of(
                            Map.of(
                                "standardId",
                                inspectionStandard.get("id"),
                                "itemId",
                                "qa-ins-item")))));
            inspectionPlan.put("customFields", Map.of());
            inspectionPlan.put("customSchemaVersion", 0);
            var created = inspectionDefinitions.save(kind, null, mutable(inspectionPlan));
            long planId = Long.parseLong(created.get("id").toString());
            int count =
                db.queryForObject(
                    "SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=10001 AND plan_id=?"
                        + " AND type_id=?",
                    Integer.class,
                    planId,
                    Long.parseLong(inspectionType.get("id").toString()));
            assertEquals(1, count);
            inspectionDefinitions.generate(10001, planId);
            assertEquals(
                count,
                db.queryForObject(
                    "SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=10001 AND plan_id=?",
                    Integer.class,
                    planId));
          }
          return null;
        });
  }

  Map<String, Object> mutable(Map<String, Object> value) {
    return json.convertValue(
        value,
        new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
  }

  java.math.BigDecimal stockQuantity(long material) {
    return db.queryForObject(
        "SELECT COALESCE(SUM(quantity),0) FROM inv_balance WHERE tenant_id=10001 AND"
            + " warehouse_id=890000001 AND material_id=?",
        java.math.BigDecimal.class,
        material);
  }

  @Test
  void typedInventoryDocumentsKeepOpeningTransferCountReturnAndReversalCosts() {
    warehouseFixtures();
    grantDirectOutbounds();
    db.update(
        "INSERT INTO inv_warehouse(id,tenant_id,company_id,code,name)"
            + " VALUES(890000003,10001,71002,'QA-WH-C','工程同公司仓库')");
    db.update(
        "INSERT INTO inv_bin(id,tenant_id,warehouse_id,code,name)"
            + " VALUES(990100001,10001,890000001,'QA-BIN-A','工程库位A'),(990100003,10001,890000003,'QA-BIN-C','工程库位C')");
    com.windasoft.eam.service.system.SystemExecution.run(
        engineeringUser(),
        () -> {
          var material =
              inventoryMasters.save(
                  "materials",
                  null,
                  new HashMap<>(
                      Map.of(
                          "code",
                          "QA-RELATIONAL-FLOW",
                          "name",
                          "关系化流转回归",
                          "unitId",
                          Long.toString(activeBase("UNIT")),
                          "usageType",
                          "消耗型",
                          "enabled",
                          true,
                          "batchEnabled",
                          false)));
          long materialId = Long.parseLong(material.get("id").toString());
          var opening =
              inventoryFlows.save(
                  "openings",
                  null,
                  new HashMap<>(
                      Map.of(
                          "warehouseId",
                          "890000001",
                          "remark",
                          "隔离期初",
                          "lines",
                          List.of(
                              Map.of(
                                  "materialId",
                                  Long.toString(materialId),
                                  "quantity",
                                  "5",
                                  "unitPrice",
                                  "10.01",
                                  "binId",
                                  "990100001")))));
          long openingId = Long.parseLong(opening.get("id").toString());
          inventoryFlows.confirm(
              "openings", openingId, Map.of("rowVersion", opening.get("rowVersion")), false);
          assertEquals(new java.math.BigDecimal("5.000000"), allStockQuantity(materialId));
          assertEquals(
              1,
              db.queryForObject(
                  "SELECT COUNT(*) FROM inv_opening_line WHERE document_id=? AND material_id=?",
                  Integer.class,
                  openingId,
                  materialId));
          long balance =
              db.queryForObject(
                  "SELECT id FROM inv_balance WHERE tenant_id=10001 AND material_id=? AND"
                      + " warehouse_id=890000001",
                  Long.class,
                  materialId);
          var transfer =
              inventoryFlows.save(
                  "transfers",
                  null,
                  new HashMap<>(
                      Map.of(
                          "warehouseId",
                          "890000001",
                          "targetWarehouseId",
                          "890000003",
                          "lines",
                          List.of(
                              Map.of(
                                  "balanceId",
                                  Long.toString(balance),
                                  "quantity",
                                  "1.5",
                                  "targetBinId",
                                  "990100003")))));
          long transferId = Long.parseLong(transfer.get("id").toString());
          var sent =
              inventoryFlows.confirm(
                  "transfers", transferId, Map.of("rowVersion", transfer.get("rowVersion")), false);
          assertEquals("在途", sent.get("status"));
          inventoryFlows.confirm(
              "transfers", transferId, Map.of("rowVersion", sent.get("rowVersion")), true);
          assertEquals(new java.math.BigDecimal("5.000000"), allStockQuantity(materialId));
          var count =
              inventoryFlows.save(
                  "counts", null, new HashMap<>(Map.of("warehouseId", "890000001")));
          long countId = Long.parseLong(count.get("id").toString());
          var edited =
              inventoryFlows.save(
                  "counts",
                  countId,
                  new HashMap<>(
                      Map.of(
                          "warehouseId",
                          "890000001",
                          "rowVersion",
                          count.get("rowVersion"),
                          "lines",
                          List.of(
                              Map.of(
                                  "balanceId",
                                  Long.toString(balance),
                                  "countedQuantity",
                                  "2.5",
                                  "unitCost",
                                  "")))));
          inventoryFlows.confirm(
              "counts", countId, Map.of("rowVersion", edited.get("rowVersion")), false);
          assertEquals(new java.math.BigDecimal("4.000000"), allStockQuantity(materialId));
          var issue =
              (Map<String, Object>)
                  outboundCommands.save(
                      null,
                      new HashMap<>(
                          Map.of(
                              "sourceKind",
                              "直接出库",
                              "warehouseId",
                              "890000001",
                              "departmentId",
                              "71003",
                              "recipientId",
                              "73001",
                              "purpose",
                              "隔离领用",
                              "lines",
                              List.of(
                                  Map.of("materialId", Long.toString(materialId), "quantity", "1")),
                              "customSchemaVersion",
                              0,
                              "customFields",
                              Map.of())));
          long issueId = Long.parseLong(issue.get("id").toString());
          var preview =
              (Map<String, Object>)
                  outboundCommands.preview(issueId, Map.of("rowVersion", issue.get("rowVersion")));
          var confirmation = new HashMap<>(preview);
          confirmation.put("confirmationKey", "qa-relational-doc-flow");
          outboundCommands.confirm(issueId, confirmation);
          long allocation =
              db.queryForObject(
                  "SELECT id FROM inv_issue_allocation WHERE issue_id=? ORDER BY id LIMIT 1",
                  Long.class,
                  issueId);
          var returned =
              inventoryFlows.save(
                  "returns",
                  null,
                  new HashMap<>(
                      Map.of(
                          "warehouseId",
                          "890000001",
                          "lines",
                          List.of(
                              Map.of(
                                  "allocationId",
                                  Long.toString(allocation),
                                  "quantity",
                                  "0.5",
                                  "binId",
                                  "990100001")))));
          long returnId = Long.parseLong(returned.get("id").toString());
          inventoryFlows.confirm(
              "returns", returnId, Map.of("rowVersion", returned.get("rowVersion")), false);
          assertEquals(new java.math.BigDecimal("3.500000"), allStockQuantity(materialId));
          var reversal =
              inventoryFlows.save(
                  "reversals",
                  null,
                  new HashMap<>(
                      Map.of(
                          "warehouseId",
                          "890000001",
                          "sourceKind",
                          "returns",
                          "sourceId",
                          Long.toString(returnId),
                          "remark",
                          "隔离纠正")));
          long reversalId = Long.parseLong(reversal.get("id").toString());
          inventoryFlows.confirm(
              "reversals", reversalId, Map.of("rowVersion", reversal.get("rowVersion")), false);
          assertEquals(new java.math.BigDecimal("3.000000"), allStockQuantity(materialId));
          assertEquals("已冲销", inventoryFlows.detail("returns", returnId).get("status"));
          assertEquals(
              0,
              db.queryForObject(
                  "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()"
                      + " AND table_name IN"
                      + " ('inv_return','inv_transfer','inv_count','inv_opening','inv_reversal')"
                      + " AND data_type='json'",
                  Integer.class));
          return null;
        });
  }

  java.math.BigDecimal allStockQuantity(long material) {
    return db.queryForObject(
        "SELECT COALESCE(SUM(quantity),0) FROM inv_balance WHERE tenant_id=10001 AND material_id=?",
        java.math.BigDecimal.class,
        material);
  }

  @Test
  void homeTemplateDraftAndPublishedRoleRelationsStayIndependent() {
    homeRepository.insertTemplate(990300001L, 10001L, "QA首页", "{}", "[\"72001\"]", 3, 73001L);
    assertEquals(
        List.of("72001"),
        json.convertValue(
            decodeFixture(
                homeRepository.loadTemplate(10001L, 990300001L, true).getFirst().get("role_ids")),
            List.class));
    homeRepository.insertVersion(10001L, 990300001L, 1, "{}", "[\"72001\"]", 3, 73001L);
    homeRepository.publishVersion(1, 10001L, 990300001L);
    homeRepository.updateTemplate("QA首页改", "{}", "[]", 4, 10001L, 990300001L);
    assertTrue(
        decodeFixture(
                homeRepository.loadTemplate(10001L, 990300001L, false).getFirst().get("role_ids"))
            .isEmpty());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_home_template_version_role WHERE template_id=990300001 AND"
                + " role_id=72001",
            Integer.class));
  }

  @Test
  void equipmentImagesAndDocumentAttributesRoundTripAsRelations() {
    maintenanceWorkflowFixtures();
    var row = equipmentWrites.find(10001L, 970000001L);
    row.setImagesJson("[{\"id\":\"qa-image-id\",\"name\":\"演示图\"}]");
    row.setDocumentsJson(
        "[{\"id\":\"qa-document-id\",\"name\":\"操作规程\",\"code\":\"DOC-QA\",\"type\":\"pdf\",\"security\":\"内部\",\"quantity\":2,\"remark\":\"隔离资料\",\"size\":23,\"url\":\"/qa/doc\",\"originalName\":\"原名称.pdf\"}]");
    equipmentMedia.save(row);
    var loaded = equipmentWrites.find(10001L, 970000001L);
    assertEquals("qa-image-id", decodeFixture(loaded.getImagesJson()).get(0).path("id").asText());
    var document = decodeFixture(loaded.getDocumentsJson()).get(0);
    assertEquals("DOC-QA", document.path("code").asText());
    assertEquals("内部", document.path("security").asText());
    assertEquals(2, document.path("quantity").asInt());
    row.setImagesJson("[]");
    equipmentMedia.save(row);
    assertTrue(decodeFixture(equipmentWrites.find(10001L, 970000001L).getImagesJson()).isEmpty());
  }

  com.fasterxml.jackson.databind.JsonNode decodeFixture(Object content) {
    try {
      return json.readTree(content.toString());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  void grantDirectOutbounds() {
    for (String action : List.of("新增直接出库", "编辑草稿"))
      db.update(
          "INSERT IGNORE INTO eam_role_menu_action SELECT p.tenant_id,p.id,?,100 FROM"
              + " eam_sys_role_menu_permission p JOIN eam_sys_role r ON r.id=p.role_id AND"
              + " r.tenant_id=p.tenant_id WHERE p.tenant_id=10001 AND r.code='SYS_ADMIN' AND"
              + " p.menu_key='inventoryOutbound'",
          action);
  }

  void companyOutboundGrant(boolean export) {
    var ids =
        db.queryForList(
            "SELECT p.id FROM eam_sys_role_menu_permission p JOIN eam_sys_role r ON r.id=p.role_id"
                + " AND r.tenant_id=p.tenant_id WHERE p.tenant_id=10001 AND r.code='SYS_ADMIN' AND"
                + " p.menu_key='inventoryOutbound'",
            Long.class);
    for (long id : ids) {
      for (String table :
          List.of("eam_role_menu_action", "eam_role_menu_scope", "eam_role_menu_target"))
        db.update("DELETE FROM " + table + " WHERE tenant_id=10001 AND grant_id=?", id);
      db.update("INSERT INTO eam_role_menu_action VALUES(10001,?,'查看',0)", id);
      if (export) db.update("INSERT INTO eam_role_menu_action VALUES(10001,?,'导出',1)", id);
      db.update("INSERT INTO eam_role_menu_scope VALUES(10001,?,'company',0)", id);
    }
  }
}
