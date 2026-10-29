package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_BUSINESS_TEST_URL", matches = ".+")
class AiBusinessQueryMySqlIT {
  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry p) {
    String url = System.getenv("EAM_BUSINESS_TEST_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:3308/eam_business_query_test_20261029(\\?.*)?"))
      throw new IllegalStateException("Requires isolated business query database");
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
  @Autowired AiBusinessQueryService queries;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;

  @BeforeEach
  void identity() {
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(73001L);
    when(permissions.allowed(anyString(), anyString())).thenReturn(true);
    var scope = new PermissionScope(true, null, Set.of(), false);
    when(permissions.scope(anyString(), anyString())).thenReturn(scope);
    when(permissions.maintenance(anyString(), anyString())).thenReturn(scope);
    when(permissions.inspection(anyString(), anyString())).thenReturn(scope);
  }

  Map<String, Object> run(String domain, String extra) throws Exception {
    return queries.query(
        json.readTree("{\"domain\":\"" + domain + "\",\"operation\":\"COUNT\"" + extra + "}"),
        null,
        1,
        20);
  }

  @Test
  void everyDomainExecutesItsRealPermissionScopedSql() throws Exception {
    for (String domain :
        List.of(
            "equipment",
            "repair",
            "maintenanceTask",
            "dailyTask",
            "periodicTask",
            "maintenancePlan",
            "dailyPlan",
            "periodicPlan",
            "stock")) {
      var result = run(domain, "");
      assertNotNull(result.get("value"), domain);
      var list =
          queries.query(
              json.readTree("{\"domain\":\"" + domain + "\",\"operation\":\"LIST\"}"), null, 1, 20);
      assertTrue(((List<?>) list.get("data")).size() <= 20, domain);
    }
    assertEquals(
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_equipment WHERE tenant_id=10001 AND deleted=0", Long.class),
        ((Number) run("equipment", "").get("value")).longValue());
  }

  @Test
  void groupsAndPaginationMatchSqlAndRetainTenantIsolation() throws Exception {
    var result =
        queries.query(
            json.readTree(
                "{\"domain\":\"equipment\",\"operation\":\"GROUP\",\"groupBy\":[\"category\"]}"),
            null,
            1,
            20);
    var rows = (List<Map<String, Object>>) result.get("data");
    long sum = rows.stream().mapToLong(row -> ((Number) row.get("count")).longValue()).sum();
    assertEquals(((Number) run("equipment", "").get("value")).longValue(), sum);
    when(auth.tenantId()).thenReturn(990999L);
    assertEquals(0L, ((Number) run("equipment", "").get("value")).longValue());
    assertEquals(0L, ((Number) run("dailyTask", "").get("value")).longValue());
  }

  @Test
  void dataScopeAndWarehouseRightsCannotBeBypassedByPlan() throws Exception {
    var none = new PermissionScope(false, null, Set.of(), false);
    when(permissions.inspection(anyString(), anyString())).thenReturn(none);
    assertEquals(0L, ((Number) run("dailyPlan", "").get("value")).longValue());
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
        .when(permissions)
        .checkInventoryWarehouse(anyString(), anyLong(), anyString());
    assertEquals(0L, ((Number) run("stock", "").get("value")).longValue());
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
        .when(permissions)
        .require("equipment", "查看");
    assertThrows(ResponseStatusException.class, () -> run("equipment", ""));
  }

  @Test
  void realTaskTrendsAndZeroAbnormalFiltersExecute() throws Exception {
    for (String domain : List.of("repair", "dailyTask", "periodicTask", "maintenanceTask")) {
      var result =
          queries.query(
              json.readTree(
                  "{\"domain\":\""
                      + domain
                      + "\",\"operation\":\"TREND\",\"period\":\"last30\",\"bucket\":\"day\"}"),
              null,
              1,
              20);
      assertNotNull(result.get("data"));
    }
    assertNotNull(
        run("dailyTask", ",\"where\":{\"field\":\"abnormal\",\"op\":\"eq\",\"values\":[\"0\"]}")
            .get("value"));
    assertNotNull(run("stock", ",\"aggregate\":\"sum\",\"measure\":\"available\"").get("data"));
  }

  @Test
  @org.springframework.transaction.annotation.Transactional
  void currentDictionaryNamesAndAdditionalEquipmentOwnersMatchTheLedger() throws Exception {
    long base =
        db.queryForObject(
            "SELECT use_status_id FROM eam_equipment WHERE tenant_id=10001 AND deleted=0 AND"
                + " use_status_id IS NOT NULL LIMIT 1",
            Long.class);
    db.update("UPDATE eam_base_item SET name='AI在用测试' WHERE tenant_id=10001 AND id=?", base);
    long expected =
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_equipment WHERE tenant_id=10001 AND deleted=0 AND"
                + " use_status_id=?",
            Long.class,
            base);
    assertEquals(
        expected,
        ((Number)
                run(
                        "equipment",
                        ",\"where\":{\"field\":\"useState\",\"op\":\"eq\",\"values\":[\"AI在用测试\"]}")
                    .get("value"))
            .longValue());
    long user =
        db.queryForObject("SELECT id FROM eam_sys_user WHERE tenant_id=10001 LIMIT 1", Long.class);
    long equipment =
        db.queryForObject(
            "SELECT id FROM eam_equipment WHERE tenant_id=10001 AND deleted=0 AND (owner_user_id IS"
                + " NULL OR owner_user_id<>?) LIMIT 1",
            Long.class,
            user);
    db.update(
        "INSERT IGNORE INTO eam_equipment_owner(tenant_id,equipment_id,user_id,owner_name,sort_no)"
            + " VALUES(10001,?,?,?,99)",
        equipment,
        user,
        "AI owner test");
    when(auth.userId()).thenReturn(user);
    var result =
        queries.query(
            json.readTree(
                "{\"domain\":\"equipment\",\"operation\":\"LIST\",\"where\":{\"field\":\"ownerId\",\"op\":\"eq\",\"values\":[\"$me\"]}}"),
            null,
            1,
            100);
    assertTrue(
        ((List<Map<String, Object>>) result.get("data"))
            .stream().anyMatch(row -> row.get("id").toString().equals(Long.toString(equipment))));
  }
}
