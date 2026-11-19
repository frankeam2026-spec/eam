package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardRow;
import com.windasoft.eam.dto.planning.PlanningCandidate.DefaultsRequest;
import com.windasoft.eam.dto.planning.PlanningCandidate.ResolveRequest;
import com.windasoft.eam.dto.planning.PlanningCandidate.Standard;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.common.SqlPredicate;
import com.windasoft.eam.repository.inspection.InspectionListRepository;
import com.windasoft.eam.repository.inventory.InventorySelectorRepository;
import com.windasoft.eam.repository.planning.PlanningSelectorRepository;
import com.windasoft.eam.service.inspection.InspectionViewAssembler;
import com.windasoft.eam.service.planning.PlanningSelectorService;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.service.system.SystemExecution;
import com.windasoft.eam.support.MaintenanceDefinitionFixture;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Import(MaintenanceDefinitionFixture.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_ENGINEERING_DB_URL", matches = ".+")
@Transactional
class SelectorMySqlIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    EngineeringQueryMySqlIT.database(properties);
  }

  @Autowired JdbcTemplate db;
  @Autowired PlanningSelectorRepository planning;

  @Autowired MaintenanceDefinitionFixture definitionStorage;

  @Autowired PlanningSelectorService selectors;
  @Autowired InspectionListRepository inspectionLists;
  @Autowired InspectionViewAssembler inspectionViews;
  @Autowired PermissionService permissions;
  @Autowired ObjectMapper json;
  @Autowired InventorySelectorRepository inventory;
  private static final PermissionScope NONE = new PermissionScope(false, null, Set.of(), false);
  private static final PermissionScope ALL = new PermissionScope(true, null, Set.of(), false);

  private ListQuery query(String keyword, int page, int size) {
    return ListQuery.from(
        Map.of("q", keyword, "page", Integer.toString(page), "size", Integer.toString(size)));
  }

  @BeforeAll
  static void clearSelectorPlans() throws Exception {
    java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("target/selector-query-plans.jsonl"));
  }

  void recordSelectorPlans(String name, JdbcTemplate probe) {
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    var recorded = new HashSet<String>();
    for (var call : org.mockito.Mockito.mockingDetails(probe).getInvocations()) {
      var raw = call.getRawArguments();
      if (raw.length < 2
          || !(raw[0] instanceof String sql)
          || !Set.of("queryForList", "queryForObject").contains(call.getMethod().getName()))
        continue;
      var bound = Arrays.stream(raw).filter(Object[].class::isInstance).findFirst();
      if (bound.isEmpty() || !recorded.add(sql)) continue;
      var args = (Object[]) bound.get();
      try {
        var plan = db.queryForObject("EXPLAIN FORMAT=JSON " + sql, String.class, args);
        java.nio.file.Files.writeString(
            java.nio.file.Path.of("target/selector-query-plans.jsonl"),
            json.writeValueAsString(Map.of("query", name, "sql", sql, "plan", json.readTree(plan)))
                + "\n",
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @Test
  void equipmentSearchAndSelectedIdsReachBeyondTenThousandWithoutCrossTenantLeak() {
    var rows = new ArrayList<Object[]>();
    for (int i = 0; i < 10005; i++)
      rows.add(
          new Object[] {
            980000000L + i, 10001L, String.format("QA-SELECT-%05d", i), "选择器设备", 71002L, 71003L
          });
    rows.add(new Object[] {980020000L, 10002L, "QA-SELECT-OTHER", "其他租户", 71002L, 71003L});
    db.batchUpdate(
        "INSERT INTO"
            + " eam_equipment(id,tenant_id,equipment_code,name,category,company,department,company_org_id,department_org_id)"
            + " VALUES(?,?,?,?,'测试','测试','测试',?,?)",
        rows);
    var ids = new HashSet<String>();
    for (int page = 1; page <= 51; page++) {
      var result =
          planning.equipment(
              10001,
              query("QA-SELECT-", page, 200),
              SqlPredicate.of("t.company_org_id=?", 71002L),
              null,
              "");
      assertEquals(10005, result.page().total());
      result.data().forEach(row -> assertTrue(ids.add(row.id())));
    }
    assertEquals(10005, ids.size());
    var probe = org.mockito.Mockito.spy(db);
    new PlanningSelectorRepository(
            new com.windasoft.eam.repository.common.JdbcListRepository(probe))
        .equipment(
            10001,
            query("QA-SELECT-", 1, 20),
            SqlPredicate.of("t.company_org_id=?", 71002L),
            null,
            "");
    recordSelectorPlans("equipment-selector", probe);

    assertEquals(
        "",
        planning
            .equipment(10001, query("QA-SELECT-", 1, 1), SqlPredicate.of("1=1"), null, "")
            .data()
            .getFirst()
            .functionLocation());
    assertEquals(
        "980010004",
        planning
            .equipment(10001, query("10004", 1, 20), SqlPredicate.of("1=1"), null, "")
            .data()
            .getFirst()
            .id());
    assertEquals(
        1,
        planning
            .equipment(
                10001,
                query("", 1, 200),
                SqlPredicate.of("1=1"),
                Set.of(980010004L, 980020000L),
                "")
            .data()
            .size());
    assertEquals(
        0,
        planning
            .equipment(
                10001,
                query("QA-SELECT-", 1, 20),
                SqlPredicate.of("t.company_org_id=?", 999L),
                null,
                "")
            .page()
            .total());
  }

  @Test
  void personnelSearchAndResolveUseStableTenantIds() {
    db.update(
        "INSERT INTO eam_sys_user(id,tenant_id,account,display_name,org_id,status,password_hash)"
            + " VALUES(981000001,10001,'qa-selector-a','分页人员甲',71003,1,'unused'),(981000002,10001,'qa-selector-b','分页人员乙',71003,1,'unused'),(981000003,10001,'qa-selector-disabled','分页人员停用',71003,0,'unused'),(981000004,10002,'qa-selector-foreign','分页人员越权',71003,1,'unused')");
    assertEquals(2, planning.users(10001, query("分页人员", 1, 1), null).page().total());
    assertEquals(
        1,
        planning
            .users(10001, query("", 1, 200), Set.of(981000002L, 981000003L, 981000004L))
            .data()
            .size());
    assertEquals(2, inventory.users(10001, 71002, query("分页人员", 1, 20), null).page().total());
    assertEquals(0, inventory.users(10001, 71006, query("分页人员", 1, 20), null).page().total());
  }

  @Test
  void applicableStandardPagesAndDefaultsKeepAllMatchingStandards() {
    db.update(
        "INSERT INTO"
            + " eam_equipment(id,tenant_id,equipment_code,name,category,company,department,company_org_id,department_org_id)"
            + " VALUES(982000001,10001,'QA-SELECT-DEFAULT','选择器默认设备','测试','测试','测试',71002,71003)");
    var rows = new ArrayList<Object[]>();
    for (int i = 0; i < 207; i++)
      rows.add(
          new Object[] {
            982100000L + i,
            10001L,
            "standards",
            "QA-STD-SELECT-" + i,
            "选择器标准",
            71003L,
            73001L,
            "{\"equipmentIds\":[\"982000001\"],\"items\":[]}"
          });
    rows.add(
        new Object[] {
          982200000L,
          10001L,
          "standards",
          "QA-STD-SELECT-OTHER",
          "不关联",
          71003L,
          73001L,
          "{\"equipmentIds\":[\"982999999\"],\"items\":[]}"
        });
    for (var row : rows)
      definitionStorage.insert(
          ((Number) row[0]).longValue(),
          ((Number) row[1]).longValue(),
          row[2].toString(),
          row[3],
          row[4],
          row[5],
          ((Number) row[6]).longValue(),
          row[7].toString());
    var user = new com.windasoft.eam.entity.system.SystemUser();
    user.setId(73001L);
    user.setTenantId(10001L);
    user.setOrgId(71003L);
    user.setStatus(1);
    user.setDeleted(0);
    SystemExecution.run(
        user,
        () -> {
          var result =
              selectors.standards("maintenance", query("QA-STD-SELECT-", 2, 20), 982000001L, null);
          assertEquals(207, result.page().total());
          assertEquals(20, result.data().size());
          var defaults =
              selectors.defaults("maintenance", new DefaultsRequest(List.of("982000001")));
          assertEquals(
              207,
              defaults.stream()
                  .filter(
                      row ->
                          ((MaintenanceStandardRow) row)
                              .detail()
                              .code()
                              .startsWith("QA-STD-SELECT-"))
                  .count());
          var resolved =
              selectors.resolve(
                  "maintenance",
                  new ResolveRequest(List.of("982000001"), null, List.of("982100206")));
          assertEquals(
              List.of("982100206"), resolved.standards().stream().map(Standard::id).toList());
          var wire = json.valueToTree(resolved).at("/standards/0");
          assertEquals("982100206", wire.path("id").asText());
          assertEquals("982000001", wire.at("/equipmentIds/0").asText());
          assertTrue(wire.path("items").isArray());
          return null;
        });
  }

  @Test
  void inspectionStandardChoicePreservesLegacyWireFieldsAndFiltersTenant() {
    db.update(
        """
        INSERT INTO eam_inspection_standard
            (id,tenant_id,code,name,department_id,creator_id,enabled,remark,requirement)
        VALUES
            (982300001,10001,'QA-INSPECT-CHOICE','点检标准',71003,73001,1,'标准备注','现场检查'),
            (982300002,10002,'QA-INSPECT-CHOICE-OTHER','其他租户',71003,73001,1,'','')
        """);
    db.update(
        """
        INSERT INTO eam_inspection_standard_item
            (tenant_id,standard_id,item_key,sort_no,code,name,content,value_type,lower_bound,
             upper_bound,required,photo_required,remark)
        VALUES (10001,982300001,'level',1,'L1','液位','看液位','数值',1.25,9,1,0,'')
        """);
    var user = new SystemUser();
    user.setId(73001L);
    user.setTenantId(10001L);
    user.setOrgId(71003L);
    user.setStatus(1);
    user.setDeleted(0);
    SystemExecution.run(
        user,
        () -> {
          var query = query("QA-INSPECT-CHOICE", 1, 20);
          var scope = permissions.inspection("standards", "查看");
          var selection =
              inspectionLists.select(
                  10001,
                  73001,
                  "standards",
                  scope,
                  scope,
                  false,
                  null,
                  query,
                  SqlPredicate.of("1=1"),
                  null);
          var legacy =
              json.valueToTree(
                  inspectionViews.definition(inspectionLists.page(selection, query).getFirst()));
          var result = selectors.standards("inspection", query, null, null);
          assertEquals(1, result.page().total());
          var actual = json.valueToTree(result.data().getFirst());
          assertEquals(legacy, actual);
          assertEquals("1.25000000", actual.at("/items/0/lower").asText());
          assertEquals("现场检查", actual.path("requirement").asText());

          var resolved =
              selectors.resolve(
                  "inspection", new ResolveRequest(null, null, List.of("982300001", "982300002")));
          assertEquals(1, resolved.standards().size());
          assertEquals(actual, json.valueToTree(resolved).at("/standards/0"));
          var denied =
              assertThrows(
                  ResponseStatusException.class,
                  () ->
                      selectors.defaults("inspection", new DefaultsRequest(List.of("982300002"))));
          assertEquals(403, denied.getStatusCode().value());
          return null;
        });
  }

  @Test
  void taskUnionCountsBeforePagingAndPreservesCompanyParticipantAndActiveRules() {
    var rows = new ArrayList<Object[]>();
    for (int i = 0; i < 10005; i++)
      rows.add(
          new Object[] {
            983000000L + i,
            10001L,
            String.format("QA-TASK-SELECT-%05d", i),
            1L,
            983000000L + i,
            1L,
            71002L,
            71003L,
            "2026-10-17",
            "执行中",
            "{\"owners\":[\"73001\"],\"equipmentName\":\"设备\"}"
          });
    rows.add(
        new Object[] {
          983020000L,
          10002L,
          "QA-TASK-SELECT-FOREIGN",
          1L,
          983020000L,
          1L,
          71002L,
          71003L,
          "2026-10-17",
          "执行中",
          "{\"owners\":[\"73001\"]}"
        });
    db.batchUpdate(
        "INSERT INTO"
            + " eam_maintenance_task(id,tenant_id,code,plan_id,schedule_id,equipment_id,company_id,department_id,due_date,status,snapshot)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON))",
        rows);
    com.windasoft.eam.support.RelationalTaskFixtures.capture(db);
    db.update(
        "INSERT INTO"
            + " eam_inspection_task(id,tenant_id,kind,code,plan_id,company_id,department_id,due_at,expires_at,status,snapshot)"
            + " VALUES(983030001,10001,'daily','QA-TASK-SELECT-DAILY',983030001,71002,71003,NOW(),NOW(),'执行中',JSON_OBJECT('owners',JSON_ARRAY(73001))),(983030002,10001,'periodic','QA-TASK-SELECT-CLOSED',983030002,71002,71003,NOW(),NOW(),'已完成',JSON_OBJECT('owners',JSON_ARRAY('73001'))),(983030003,10001,'periodic','QA-TASK-SELECT-PRIVATE',983030003,71006,71007,NOW(),NOW(),'执行中',JSON_OBJECT('owners',JSON_ARRAY('99999')))");
    com.windasoft.eam.support.RelationalTaskFixtures.capture(db);
    db.update(
        "INSERT INTO"
            + " eam_repair_order(id,tenant_id,order_no,equipment_code,equipment_name,fault_type,description,reporter,reporter_user_id,department_org_id,assignee_user_id,status)"
            + " VALUES(983040001,10001,'QA-TASK-SELECT-REPAIR','EQ-X','设备','测试','描述','测试',73001,71003,73001,'处理中')");
    var ids = new HashSet<String>();
    for (int page = 1; page <= 51; page++) {
      var result =
          inventory.tasks(
              10001,
              73001,
              query("QA-TASK-SELECT-", page, 200),
              NONE,
              NONE,
              NONE,
              NONE,
              true,
              71002L,
              "",
              null);
      assertEquals(10007, result.page().total());
      result.data().forEach(row -> assertTrue(ids.add(row.kind() + ":" + row.id())));
    }
    assertEquals(10007, ids.size());
    var probe = org.mockito.Mockito.spy(db);
    new InventorySelectorRepository(probe)
        .tasks(
            10001,
            73001,
            query("QA-TASK-SELECT-", 1, 20),
            NONE,
            NONE,
            NONE,
            NONE,
            true,
            71002L,
            "",
            null);
    recordSelectorPlans("inventory-task-selector", probe);

    assertEquals(
        0,
        inventory
            .tasks(
                10001,
                73001,
                query("QA-TASK-SELECT-", 1, 20),
                NONE,
                NONE,
                NONE,
                NONE,
                true,
                71006L,
                "",
                null)
            .page()
            .total());
    assertEquals(
        1,
        inventory
            .tasks(
                10001, 73001, query("PRIVATE", 1, 20), ALL, ALL, ALL, ALL, false, 71006L, "", null)
            .page()
            .total());
    assertEquals(
        0,
        inventory
            .tasks(10001, 73001, query("CLOSED", 1, 20), ALL, ALL, ALL, ALL, false, null, "", null)
            .page()
            .total());
  }

  @Autowired com.windasoft.eam.service.inventory.InventorySelectorService inventorySelectors;

  @Test
  void warehousePermissionIsCheckedBeforeTaskAndRecipientQueries() {
    db.update(
        "INSERT INTO inv_warehouse(id,tenant_id,company_id,code,name)"
            + " VALUES(984000001,10001,71002,'QA-SELECT-WH-A','选择器仓库A'),(984000002,10001,71006,'QA-SELECT-WH-B','选择器仓库B')");
    var fixture = new EngineeringQueryMySqlIT();
    fixture.db = db;
    fixture.companyOutboundGrant(false);
    var user = new com.windasoft.eam.entity.system.SystemUser();
    user.setId(73001L);
    user.setTenantId(10001L);
    user.setOrgId(71003L);
    user.setStatus(1);
    user.setDeleted(0);
    SystemExecution.run(
        user,
        () -> {
          assertDoesNotThrow(() -> inventorySelectors.users(query("", 1, 20), 984000001L, null));
          var denied =
              assertThrows(
                  org.springframework.web.server.ResponseStatusException.class,
                  () -> inventorySelectors.users(query("", 1, 20), 984000002L, null));
          assertEquals(403, denied.getStatusCode().value());
          assertThrows(
              org.springframework.web.server.ResponseStatusException.class,
              () -> inventorySelectors.tasks("outbound", query("", 1, 20), 984000002L, "", null));
          assertThrows(
              IllegalArgumentException.class,
              () -> inventorySelectors.tasks("unknown", query("", 1, 20), 984000001L, "", null));
          return null;
        });
  }

  @Test
  void selectedIdsAndPageInputsHaveExplicitBounds() {
    assertEquals(Set.of(123L), PlanningSelectorService.ids(List.of("123", 123)));
    assertThrows(IllegalArgumentException.class, () -> PlanningSelectorService.ids(List.of("0")));
    assertThrows(
        IllegalArgumentException.class,
        () -> PlanningSelectorService.ids(Collections.nCopies(201, "1")));
    assertThrows(IllegalArgumentException.class, () -> query("", 0, 20));
    assertThrows(IllegalArgumentException.class, () -> query("", 1, 201));
  }
}
