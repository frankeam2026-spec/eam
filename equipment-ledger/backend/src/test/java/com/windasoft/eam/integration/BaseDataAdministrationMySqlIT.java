package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.basedata.BaseItemForm;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.basedata.BaseDataReferenceService;
import com.windasoft.eam.service.basedata.BaseDataService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 基础数据和地点共用入口的隔离MySQL行为基线，测试数据由事务回滚。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class BaseDataAdministrationMySqlIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    SystemUserQueryMySqlIT.database(p);
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean NotificationScheduler notificationScheduler;
  @MockitoBean MaintenanceScheduler maintenanceScheduler;
  @MockitoBean InspectionScheduler inspectionScheduler;
  @MockitoBean InventoryReservationCleanup cleanup;
  @MockitoBean RepairPlanScheduler repairScheduler;
  @MockitoBean KnowledgeScheduler knowledgeScheduler;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;

  @Autowired BaseDataService base;
  @MockitoSpyBean BaseItemRepository items;

  @BeforeEach
  void identity() {
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(73001L);
  }

  private long create(String resource, String name, Long parent, Long company) {
    var row =
        json.valueToTree(
            base.create(
                resource,
                new BaseItemForm("Q-" + IdWorker.getId(), name, parent, 0, 1, 0, company)));
    assertTrue(row.path("id").isTextual());
    return Long.parseLong(row.path("id").asText());
  }

  @Test
  void listPreservesFieldsOrderingAndTenantBoundary() {
    long first = create("units", "单位甲", null, null), second = create("units", "单位乙", null, null);
    var rows = json.valueToTree(base.list("units", null));
    var selected = new ArrayList<Long>();
    for (var row : rows) {
      if (row.path("id").asText().equals("" + first)
          || row.path("id").asText().equals("" + second)) {
        selected.add(Long.valueOf(row.path("id").asText()));
        assertEquals("", row.path("parentId").asText());
        assertEquals("", row.path("companyId").asText());
        assertTrue(row.path("precision").isInt());
      }
    }
    assertEquals(List.of(first, second), selected);
    when(auth.tenantId()).thenReturn(10002L);
    assertThrows(
        IllegalArgumentException.class,
        () -> base.update("units", first, new BaseItemForm("x", "外租户", null, 0, 1, 0, null)));
  }

  @Test
  void locationKeepsCompanyAndParentRules() {
    long parent = create("locations", "父地点", null, 71002L);
    long child = create("locations", "子地点", parent, 71002L);
    assertThrows(IllegalArgumentException.class, () -> create("locations", "不同公司", parent, 71006L));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            base.update(
                "locations",
                child,
                new BaseItemForm("Q-" + child, "变更公司", parent, 0, 1, 0, 71006L)));
    assertThrows(IllegalArgumentException.class, () -> base.delete("locations", parent));
    base.delete("locations", child);
    assertEquals(
        1, db.queryForObject("SELECT deleted FROM eam_base_item WHERE id=?", Integer.class, child));
  }

  @Test
  void renamingUnitOnlyUpdatesMatchingMaterialsAndVersions() {
    long unit = create("units", "原单位", null, null);
    long local = IdWorker.getId(), foreign = IdWorker.getId();
    for (long id : new long[] {local, foreign})
      db.update(
          "INSERT INTO inv_material(id,tenant_id,code,name,unit,unit_id,row_version)"
              + " VALUES(?,?,?,'治理物料','原单位',?,3)",
          id,
          id == local ? 10001L : 10002L,
          "Q-" + id,
          unit);
    base.update("units", unit, new BaseItemForm("Q-" + unit, "新单位", null, 0, 1, 0, null));
    assertEquals(
        "新单位", db.queryForObject("SELECT unit FROM inv_material WHERE id=?", String.class, local));
    assertEquals(
        4,
        db.queryForObject("SELECT row_version FROM inv_material WHERE id=?", Integer.class, local));
    base.update("units", unit, new BaseItemForm("Q-" + unit, "新单位", null, 0, 1, 0, null));
    assertEquals(
        4,
        db.queryForObject("SELECT row_version FROM inv_material WHERE id=?", Integer.class, local));
    assertEquals(
        "原单位",
        db.queryForObject("SELECT unit FROM inv_material WHERE id=?", String.class, foreign));
    assertEquals(
        3,
        db.queryForObject(
            "SELECT row_version FROM inv_material WHERE id=?", Integer.class, foreign));
    assertThrows(IllegalArgumentException.class, () -> base.delete("units", unit));
  }

  @Test
  void fixedProcessingResultCannotBeChangedOrDeleted() {
    Long id =
        db.queryForObject(
            "SELECT id FROM eam_base_item WHERE tenant_id=10001 AND item_type='PROCESS_RESULT' AND"
                + " code=? AND deleted=0",
            Long.class,
            BaseDataReferenceService.RESOLVED_CODE);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            base.update(
                "processing-results", id, new BaseItemForm("x", "修改固定项", null, 0, 1, 0, null)));
    assertThrows(IllegalArgumentException.class, () -> base.delete("processing-results", id));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            base.create("processing-results", new BaseItemForm("new", "已解决", null, 0, 1, 0, null)));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void dictionaryFailureRollsBackMaterialNameAndVersion() {
    long unit = create("units", "回滚原单位", null, null), material = IdWorker.getId();
    try {
      db.update(
          "INSERT INTO inv_material(id,tenant_id,code,name,unit,unit_id,row_version)"
              + " VALUES(?,10001,?,'回滚物料','回滚原单位',?,7)",
          material,
          "Q-" + material,
          unit);
      doThrow(new IllegalStateException("模拟字典保存失败")).when(items).update(any());
      assertThrows(
          IllegalStateException.class,
          () ->
              base.update(
                  "units", unit, new BaseItemForm("Q-" + unit, "不应保存", null, 0, 1, 0, null)));
      assertEquals(
          "回滚原单位",
          db.queryForObject("SELECT unit FROM inv_material WHERE id=?", String.class, material));
      assertEquals(
          7,
          db.queryForObject(
              "SELECT row_version FROM inv_material WHERE id=?", Integer.class, material));
      assertEquals(
          "回滚原单位",
          db.queryForObject("SELECT name FROM eam_base_item WHERE id=?", String.class, unit));
    } finally {
      db.update("DELETE FROM inv_material WHERE id=?", material);
      db.update("DELETE FROM eam_base_item WHERE id=? AND tenant_id=10001", unit);
    }
  }
}
