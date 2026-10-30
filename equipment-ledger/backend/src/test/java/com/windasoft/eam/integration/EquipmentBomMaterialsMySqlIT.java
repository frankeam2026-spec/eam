package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.ApiExceptionHandler;
import com.windasoft.eam.controller.equipment.EquipmentSelectionController;
import com.windasoft.eam.entity.inventory.Material;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.Base64;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "EAM_EQUIPMENT_BOM_TEST_URL", matches = ".+")
class EquipmentBomMaterialsMySqlIT {
  private static final long TENANT = 992960001L;
  private static final long FIRST = 9007199254740993L;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    String url = System.getenv("EAM_EQUIPMENT_BOM_TEST_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:(3308/eam_equipment_governance_test_20261030|3306/eam_engineering_test)(\\?.*)?")) {
      throw new IllegalStateException("只允许使用明确隔离的设备治理测试库");
    }
    p.add("spring.datasource.url", () -> url);
    p.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_USER", "root"));
    p.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_PASSWORD", ""));
    p.add("eam.notification.secret-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean com.windasoft.eam.scheduler.system.NotificationScheduler notifications;
  @MockitoBean com.windasoft.eam.scheduler.inspection.InspectionScheduler inspection;
  @MockitoBean com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler maintenance;
  @MockitoBean com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup reservations;
  @MockitoBean com.windasoft.eam.scheduler.experience.RepairPlanScheduler repairs;
  @MockitoBean com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler knowledge;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired EquipmentSelectionController controller;
  MockMvc mvc;

  @BeforeEach
  void fixture() {
    when(auth.tenantId()).thenReturn(TENANT);
    when(auth.userId()).thenReturn(73001L);
    when(permissions.allowed("equipment", "新增")).thenReturn(true);
    for (int i = 0; i < 5; i++) {
      db.update(
          "INSERT INTO inv_material(id,tenant_id,code,name,unit,model,category,enabled)"
              + " VALUES(?,?,?,?,?,?,?,?)",
          FIRST + i,
          i == 4 ? TENANT + 1 : TENANT,
          "QA-GOV-M-00" + i,
          "测试物料" + i,
          "个",
          i == 0 ? "" : "MODEL",
          "",
          i == 3 ? 0 : 1);
    }
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  private long selects() {
    return Long.parseLong(
        db.queryForList("SHOW SESSION STATUS LIKE 'Com_select'")
            .getFirst()
            .get("Value")
            .toString());
  }

  @Test
  void materialEntityCoversActualTableColumns() {
    var table = TableInfoHelper.getTableInfo(Material.class);
    var mapped =
        table.getFieldList().stream()
            .map(field -> field.getColumn())
            .collect(java.util.stream.Collectors.toSet());
    mapped.add(table.getKeyColumn());
    var actual =
        new java.util.HashSet<>(
            db.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
                    + " AND TABLE_NAME=?",
                String.class,
                "inv_material"));
    assertEquals(actual, mapped);
  }

  @Test
  void bomWireContractTenantEnabledAndSqlCountStayStable() throws Exception {
    long before = selects();
    var response =
        mvc.perform(get("/api/v1/equipment/bom-materials"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.total").value(3))
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].id").value(Long.toString(FIRST)))
            .andExpect(jsonPath("$.data[0].enabled").value(true))
            .andReturn()
            .getResponse();
    assertEquals(2L, selects() - before);
    var body = json.readTree(response.getContentAsByteArray());
    assertEquals(2, body.size());
    assertEquals(1, body.path("page").size());
    assertEquals(7, body.path("data").get(0).size());
    assertEquals("", body.path("data").get(0).path("model").asText());
    assertEquals("", body.path("data").get(0).path("category").asText());
    System.out.println("BOM_SQL_BASELINE selects=2, rows=3, total=3");
  }

  @Test
  void bomPaginationAndLegacyClampingStayCompatible() throws Exception {
    mvc.perform(get("/api/v1/equipment/bom-materials").param("page", "2").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(Long.toString(FIRST + 1)));
    mvc.perform(get("/api/v1/equipment/bom-materials").param("page", "0").param("size", "0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(Long.toString(FIRST)));
    mvc.perform(get("/api/v1/equipment/bom-materials").param("size", "999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(3));
  }

  @Test
  void largeValidIntegerPageMustNotOverflowIntoSqlError() throws Exception {
    long before = selects();
    mvc.perform(
            get("/api/v1/equipment/bom-materials")
                .param("page", Integer.toString(Integer.MAX_VALUE))
                .param("size", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.page.total").value(3));
    assertEquals(2L, selects() - before);
  }

  @Test
  void wildcardAndBoundValueSearchKeepTheirMeanings() throws Exception {
    mvc.perform(get("/api/v1/equipment/bom-materials").param("q", "M-00_"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.total").value(3));
    mvc.perform(get("/api/v1/equipment/bom-materials").param("q", "' OR 1=1 --"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.total").value(0));
  }

  @Test
  void malformedPaginationIsRejectedBeforeSql() throws Exception {
    long before = selects();
    mvc.perform(get("/api/v1/equipment/bom-materials").param("page", "2147483648"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/equipment/bom-materials").param("size", "not-a-number"))
        .andExpect(status().isBadRequest());
    assertEquals(0L, selects() - before);
  }

  @Test
  void editPermissionIsStillAnAlternativeAndDenialRunsNoSelect() throws Exception {
    when(permissions.allowed("equipment", "新增")).thenReturn(false);
    mvc.perform(get("/api/v1/equipment/bom-materials")).andExpect(status().isOk());
    verify(permissions).require("equipment", "编辑");
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
        .when(permissions)
        .require("equipment", "编辑");
    long before = selects();
    mvc.perform(get("/api/v1/equipment/bom-materials")).andExpect(status().isForbidden());
    assertEquals(0L, selects() - before);
  }
}
