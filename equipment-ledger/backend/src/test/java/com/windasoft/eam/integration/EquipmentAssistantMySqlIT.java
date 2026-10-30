package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.windasoft.eam.dto.equipment.EquipmentListQuery;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.Condition;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.Direction;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.Operator;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.Scope;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.Sort;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.equipment.EquipmentMapper;
import com.windasoft.eam.repository.knowledge.AiBusinessQueryRepository;
import com.windasoft.eam.repository.system.AuthorizationQueryPredicates;
import com.windasoft.eam.service.equipment.EquipmentQueryService;
import com.windasoft.eam.service.knowledge.AiBusinessQueryService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "EAM_EQUIPMENT_AI_TEST_URL", matches = ".+")
class EquipmentAssistantMySqlIT {
  static final long FIRST = 992001000L, CATEGORY_A = 992010001L, CATEGORY_B = 992010002L;
  static final String PREFIX = "QA-AI-DEVICE-";

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    String url = System.getenv("EAM_EQUIPMENT_AI_TEST_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:3308/eam_equipment_ai_test_20261030(\\?.*)?")) {
      throw new IllegalStateException("必须使用专用设备AI隔离数据库");
    }
    p.add("spring.datasource.url", () -> url);
    p.add("spring.datasource.username", () -> "root");
    p.add("spring.datasource.password", () -> "");
    p.add("eam.notification.secret-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean com.windasoft.eam.scheduler.system.NotificationScheduler notificationScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inspection.InspectionScheduler inspectionScheduler;
  @MockitoBean com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler maintenanceScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup cleanup;
  @MockitoBean com.windasoft.eam.scheduler.experience.RepairPlanScheduler repairScheduler;
  @MockitoBean com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler knowledgeScheduler;
  @Autowired AiBusinessQueryService assistant;
  @Autowired EquipmentQueryService ledger;
  @Autowired EquipmentMapper mapper;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.web.context.WebApplicationContext web;
  @MockitoSpyBean AiBusinessQueryRepository generic;
  PermissionScope scope;
  long user;

  @BeforeEach
  void fixture() {
    user = 73002L;
    scope = new PermissionScope(true, null, Set.of(), false);
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenAnswer(c -> user);
    when(permissions.equipment("查看")).thenAnswer(c -> scope);
    doAnswer(
            c -> {
              AuthorizationQueryPredicates.equipment(
                  c.getArgument(0), scope, auth.tenantId(), user);
              return null;
            })
        .when(permissions)
        .filterEquipment(any());
    doAnswer(
            c -> {
              Equipment e = c.getArgument(0);
              var q =
                  new LambdaQueryWrapper<Equipment>()
                      .eq(Equipment::getTenantId, auth.tenantId())
                      .eq(Equipment::getId, e.getId());
              AuthorizationQueryPredicates.equipment(q, scope, auth.tenantId(), user);
              if (mapper.selectCount(q) == 0) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
              }
              return null;
            })
        .when(permissions)
        .checkEquipment(any(), eq("查看"));
    doThrow(new AssertionError("设备查询不得进入AI通用SQL执行器"))
        .when(generic)
        .execute(any(), any(), any(), anyInt(), anyInt());
    db.update(
        "INSERT INTO eam_base_item(id,tenant_id,item_type,code,name)"
            + " VALUES(?,10001,'CATEGORY','QA-AI-CAT-A','同名分类'),(?,10001,'CATEGORY','QA-AI-CAT-B','同名分类')",
        CATEGORY_A,
        CATEGORY_B);
    for (int i = 0; i < 30; i++) {
      db.update(
          "INSERT INTO"
              + " eam_equipment(id,tenant_id,equipment_code,name,category,category_id,company,company_org_id,department,department_org_id,model,owner_user_id,purchase_date,acquired_at,created_at)"
              + " VALUES(?,10001,?,?,'旧分类',?,'同名公司',?,'同名部门',?,?,? ,?,?,'2026-10-01 08:00:00')",
          FIRST + i,
          PREFIX + String.format("%02d", i),
          i == 0 ? "含%_字面" : i < 3 ? "重名设备" : "设备" + i,
          i < 15 ? CATEGORY_A : CATEGORY_B,
          i < 15 ? 71002L : 71006L,
          i < 15 ? 71003L : 71007L,
          i % 2 == 0 ? null : "M",
          i == 0 ? 73001L : null,
          i < 15 ? "2026-10-02" : null,
          "2025-01-01");
    }
    db.update(
        "INSERT INTO eam_equipment_owner(tenant_id,equipment_id,user_id,owner_name,sort_no)"
            + " VALUES(10001,?,73002,'额外负责人',1),(10001,?,73002,'额外负责人',1)",
        FIRST + 1,
        FIRST + 29);
    db.update(
        "INSERT INTO"
            + " eam_equipment(id,tenant_id,equipment_code,name,category,company,department,deleted)"
            + " VALUES(?,10002,?,'其他租户','','','',0),(?,10001,?,'已删除','','','',1)",
        FIRST + 100,
        PREFIX + "OTHER",
        FIRST + 101,
        PREFIX + "DELETED");
  }

  ObjectNode plan(String operation, String extra) throws Exception {
    var plan =
        (ObjectNode)
            json.readTree(
                "{\"domain\":\"equipment\",\"operation\":\"" + operation + "\"" + extra + "}");
    var prefix = json.createObjectNode().put("field", "code").put("op", "contains");
    prefix.putArray("values").add(PREFIX);
    if (plan.has("where")) {
      var root = json.createObjectNode().put("op", "AND");
      root.putArray("children").add(prefix).add(plan.get("where"));
      plan.set("where", root);
    } else {
      plan.set("where", prefix);
    }
    return plan;
  }

  Map<String, Object> run(String operation, String extra, int page, int size) throws Exception {
    return assistant.query(plan(operation, extra), null, page, size);
  }

  long count(String extra) throws Exception {
    return ((Number) run("COUNT", extra, 1, 20).get("value")).longValue();
  }

  List<Map<String, Object>> rows(Map<String, Object> result) {
    return (List<Map<String, Object>>) result.get("data");
  }

  List<String> ids(Map<String, Object> result) {
    return rows(result).stream().map(row -> row.get("id").toString()).toList();
  }

  List<String> ledgerIds(List<Condition> extra) {
    var conditions = new ArrayList<Condition>();
    conditions.add(new Condition("code", Operator.CONTAINS, List.of(PREFIX)));
    conditions.addAll(extra);
    return ledger
        .list(
            new EquipmentListQuery(
                1, 100, "", conditions, new Sort("code", Direction.ASC), Scope.none()))
        .data()
        .stream()
        .map(row -> row.id())
        .toList();
  }

  @Test
  void authorizedCountsAndListsMatchLedgerForAllScopeShapes() throws Exception {
    var scopes =
        List.of(
            new PermissionScope(true, null, Set.of(), false),
            new PermissionScope(false, 71002L, Set.of(), false),
            new PermissionScope(false, null, Set.of(71007L), false),
            new PermissionScope(false, null, Set.of(), true),
            new PermissionScope(false, 71002L, Set.of(), true),
            new PermissionScope(false, null, Set.of(), false));
    for (var current : scopes) {
      scope = current;
      var expected = ledgerIds(List.of());
      assertEquals(expected.size(), count(""));
      assertEquals(
          expected, ids(run("LIST", ",\"sortBy\":\"code\",\"direction\":\"asc\"", 1, 100)));
    }
    verify(generic, never()).execute(any(), any(), any(), anyInt(), anyInt());
  }

  @Test
  void filteringCanOnlyNarrowAuthorizationAndTenantBoundaryIsEnforced() throws Exception {
    scope = new PermissionScope(false, 71002L, Set.of(), false);
    assertEquals(
        0, count(",\"where\":{\"field\":\"companyId\",\"op\":\"eq\",\"values\":[\"71006\"]}"));
    scope = new PermissionScope(true, null, Set.of(), false);
    assertEquals(30, count(""));
    when(auth.tenantId()).thenReturn(10002L);
    assertEquals(1, count(""));
  }

  @Test
  void currentDictionaryAndLiteralLikeFiltersShareLedgerSemantics() throws Exception {
    db.update("UPDATE eam_base_item SET name='更新分类' WHERE tenant_id=10001 AND id=?", CATEGORY_A);
    var result =
        run(
            "LIST",
            ",\"sortBy\":\"code\",\"direction\":\"asc\",\"where\":{\"field\":\"category\",\"op\":\"eq\",\"values\":[\"更新分类\"]}",
            1,
            100);
    assertEquals(
        ledgerIds(List.of(new Condition("category", Operator.IN, List.of("更新分类")))), ids(result));
    assertTrue(rows(result).stream().allMatch(row -> row.get("category").equals("更新分类")));
    var literal =
        run(
            "LIST",
            ",\"where\":{\"field\":\"name\",\"op\":\"contains\",\"values\":[\"%_\"]}",
            1,
            100);
    assertEquals(
        ledgerIds(List.of(new Condition("name", Operator.CONTAINS, List.of("%_")))), ids(literal));
    assertEquals(1, rows(literal).size());
    assertEquals(
        0, count(",\"where\":{\"field\":\"name\",\"op\":\"eq\",\"values\":[\"' OR 1=1 --\"]}"));
  }

  @Test
  void secondaryOwnersAndNotEqualRetainUnassignedEquipment() throws Exception {
    assertEquals(2, count(",\"where\":{\"field\":\"ownerId\",\"op\":\"eq\",\"values\":[\"$me\"]}"));
    assertEquals(
        28, count(",\"where\":{\"field\":\"ownerId\",\"op\":\"ne\",\"values\":[\"$me\"]}"));
    scope = new PermissionScope(false, null, Set.of(), true);
    assertEquals(2, ledgerIds(List.of()).size());
    assertEquals(2, count(""));
  }

  @Test
  void groupedNamesKeepIdentitiesAndAmbiguousEqualityNeedsMoreContext() throws Exception {
    var grouped = rows(run("GROUP", ",\"groupBy\":[\"category\"]", 1, 20));
    assertEquals(2, grouped.size());
    assertNotEquals(grouped.get(0).get("categoryId"), grouped.get(1).get("categoryId"));
    assertTrue(grouped.stream().allMatch(row -> ((Number) row.get("count")).longValue() == 15));
    assertThrows(
        IllegalArgumentException.class,
        () -> count(",\"where\":{\"field\":\"category\",\"op\":\"eq\",\"values\":[\"同名分类\"]}"));
    scope = new PermissionScope(false, 71002L, Set.of(), false);
    assertEquals(
        15, count(",\"where\":{\"field\":\"category\",\"op\":\"eq\",\"values\":[\"同名分类\"]}"));
  }

  @Test
  void stableRankingSupportsPartialAndBeyondLastPages() throws Exception {
    String options = ",\"groupBy\":[\"name\"],\"topN\":25";
    var first = run("GROUP", options, 1, 20);
    var second = run("GROUP", options, 2, 20);
    var third = run("GROUP", options, 3, 20);
    assertEquals(25L, first.get("total"));
    assertEquals(30L, first.get("records"));
    assertEquals(20, rows(first).size());
    assertEquals(5, rows(second).size());
    assertEquals(0, rows(third).size());
    var ids = rows(first).stream().map(row -> row.get("id")).toList();
    assertTrue(Collections.disjoint(ids, rows(second).stream().map(row -> row.get("id")).toList()));
    assertEquals(rows(first), rows(run("GROUP", options, 1, 20)));
    assertEquals(5, rows(run("LIST", ",\"topN\":25", 2, 20)).size());
    assertEquals(0, rows(run("LIST", ",\"topN\":25", 3, 20)).size());
  }

  @Test
  void datesUsePurchaseFallbackAndTrendSupportsNullPeriods() throws Exception {
    String range =
        ",\"timeField\":\"acquiredAt\",\"period\":\"custom\",\"from\":\"2026-10-01\",\"to\":\"2026-10-02\"";
    assertEquals(15, count(range));
    assertEquals(
        ledgerIds(
            List.of(
                new Condition("acquiredAt", Operator.RANGE, List.of("2026-10-01", "2026-10-02")))),
        ids(run("LIST", range + ",\"sortBy\":\"code\",\"direction\":\"asc\"", 1, 100)));
    var trend = rows(run("TREND", ",\"timeField\":\"acquiredAt\",\"bucket\":\"year\"", 1, 20));
    assertEquals(List.of("2025", "2026"), trend.stream().map(row -> row.get("period")).toList());
    var nullPeriod = rows(run("TREND", ",\"timeField\":\"warrantyUntil\"", 1, 20));
    assertEquals(1, nullPeriod.size());
    assertTrue(nullPeriod.getFirst().containsKey("period"));
    assertNull(nullPeriod.getFirst().get("period"));
  }

  @Test
  void distinctCountsUseIdentityAndSelectedEquipmentChecksItsOwnPermission() throws Exception {
    assertEquals(2, count(",\"aggregate\":\"distinct\",\"measure\":\"category\""));
    assertEquals(30, count(",\"aggregate\":\"distinct\",\"measure\":\"name\""));
    scope = new PermissionScope(false, 71002L, Set.of(), false);
    assertEquals(
        403,
        assertThrows(
                ResponseStatusException.class,
                () -> assistant.query(plan("COUNT", ""), FIRST + 29, 1, 20))
            .getStatusCode()
            .value());
    assertEquals(1L, assistant.query(plan("COUNT", ""), FIRST, 1, 20).get("value"));
  }

  @Test
  void realMvcPreservesTableMetadataPlansAndLinks() throws Exception {
    var mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).build();
    var body = json.createObjectNode();
    body.set("plan", plan("LIST", ",\"sortBy\":\"code\",\"direction\":\"asc\""));
    body.put("page", 1);
    body.put("size", 20);
    var result =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/knowledge/assistant/business-query")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn();
    var data = json.readTree(result.getResponse().getContentAsString());
    assertEquals("/equipment/" + FIRST + "/history", data.path("data").get(0).path("url").asText());
    assertEquals("code", data.path("columns").get(0).path("key").asText());
    assertEquals("equipment", data.path("plan").path("domain").asText());
    assertEquals("", data.path("equipmentId").asText());
    assertTrue(data.has("asOf"));
    assertTrue(data.has("identities"));
  }

  @Test
  void rootOrCannotBypassTenantOrAuthorization() throws Exception {
    scope = new PermissionScope(false, null, Set.of(), false);
    var root =
        json.readTree(
            "{\"domain\":\"equipment\",\"operation\":\"COUNT\",\"where\":{\"op\":\"OR\",\"children\":[{\"field\":\"code\",\"op\":\"eq\",\"values\":[\""
                + PREFIX
                + "00\"]},{\"field\":\"code\",\"op\":\"contains\",\"values\":[\""
                + PREFIX
                + "\"]}]}}");
    assertEquals(0L, assistant.query(root, null, 1, 20).get("value"));
    scope = new PermissionScope(false, 71002L, Set.of(), false);
    assertEquals(15L, assistant.query(root, null, 1, 20).get("value"));
    scope = new PermissionScope(true, null, Set.of(), false);
    assertEquals(30L, assistant.query(root, null, 1, 20).get("value"));
  }

  @Test
  void ledgerKeywordCannotRevealAnotherTenantThroughItsLastOrBranch() {
    db.update(
        "UPDATE eam_equipment SET owner_name='越权关键词' WHERE tenant_id=10002 AND id=?", FIRST + 100);
    var query =
        new EquipmentListQuery(
            1, 20, "越权关键词", List.of(), new Sort("code", Direction.ASC), Scope.none());
    assertEquals(0, ledger.list(query).page().total());
    var csv = new java.io.StringWriter();
    ledger.export(query, List.of("code", "name"), new java.io.PrintWriter(csv));
    assertFalse(csv.toString().contains(PREFIX + "OTHER"));
  }

  @Test
  void twoDimensionsDistinctAggregatesAndPercentagesRemainConsistent() throws Exception {
    var result =
        run(
            "GROUP",
            ",\"groupBy\":[\"category\",\"model\"],\"sortBy\":\"count\",\"direction\":\"asc\"",
            1,
            20);
    assertEquals(4L, result.get("total"));
    assertEquals(
        30L, rows(result).stream().mapToLong(row -> ((Number) row.get("count")).longValue()).sum());
    assertEquals(new java.math.BigDecimal("23.33"), rows(result).getFirst().get("percent"));
    var distinct =
        run(
            "GROUP",
            ",\"groupBy\":[\"category\"],\"aggregate\":\"distinct\",\"measure\":\"name\"",
            1,
            20);
    assertTrue(
        rows(distinct).stream().allMatch(row -> ((Number) row.get("value")).longValue() == 15));
    assertTrue(
        ((List<Map<String, Object>>) distinct.get("columns"))
            .stream().anyMatch(column -> column.get("key").equals("value")));
  }

  @Test
  void emptyNotAndMixedBooleanFiltersUseTheSameBoundedValues() throws Exception {
    assertEquals(15, count(",\"where\":{\"field\":\"model\",\"op\":\"empty\",\"values\":[]}"));
    assertEquals(15, count(",\"where\":{\"field\":\"model\",\"op\":\"notEmpty\",\"values\":[]}"));
    assertEquals(
        15,
        count(
            ",\"where\":{\"op\":\"NOT\",\"children\":[{\"field\":\"model\",\"op\":\"empty\",\"values\":[]}]"
                + " }"));
    assertEquals(
        2,
        count(
            ",\"where\":{\"op\":\"OR\",\"children\":[{\"field\":\"ownerId\",\"op\":\"in\",\"values\":[\"$me\"]},{\"field\":\"name\",\"op\":\"eq\",\"values\":[\"不存在\"]}]}"));
  }

  @Test
  void historicalIdentifierLengthsKeepTheLegacyTieOrder() throws Exception {
    long mixed = FIRST + 2_000_000_000L;
    db.update(
        "INSERT INTO"
            + " eam_equipment(id,tenant_id,equipment_code,name,category,company,department,created_at)"
            + " VALUES(?,10001,?,'重名设备','','','','2026-10-01 08:00:00')",
        mixed,
        PREFIX + "MIXED");
    var list = ids(run("LIST", "", 1, 100));
    assertEquals(Long.toString(FIRST + 29), list.getFirst());
    var sameNames =
        rows(run("GROUP", ",\"groupBy\":[\"name\"]", 1, 100)).stream()
            .filter(row -> row.get("name").equals("重名设备"))
            .map(row -> row.get("id"))
            .toList();
    assertEquals(
        List.of(Long.toString(mixed), Long.toString(FIRST + 1), Long.toString(FIRST + 2)),
        sameNames);
  }
}
