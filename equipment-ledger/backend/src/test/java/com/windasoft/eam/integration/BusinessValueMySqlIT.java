package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.business.BusinessConfigEventRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.business.BusinessConfigService;
import com.windasoft.eam.service.business.BusinessReferenceService;
import com.windasoft.eam.service.business.BusinessValueService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 配置保存、发布和历史恢复仅在本专项隔离库验证，保留旧字段身份与版本语义。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class BusinessValueMySqlIT {
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

  @Autowired BusinessConfigService configuration;
  @Autowired BusinessValueService values;
  @Autowired SqlSessionTemplate sqlSessions;
  @Autowired PlatformTransactionManager transactions;
  @MockitoBean BusinessReferenceService references;
  @MockitoSpyBean BusinessConfigEventRepository audit;

  private long tenant;

  @BeforeEach
  void identity() {
    tenant = IdWorker.getId();
    when(auth.tenantId()).thenReturn(tenant);
    when(auth.userId()).thenReturn(73001L);
    var user = new SystemUser();
    user.setDisplayName("动态值测试");
    when(auth.user()).thenReturn(user);
  }

  private String field(String type) {
    return UUID.randomUUID().toString();
  }

  private void schema(Map<String, String> fields) {
    var config = json.createObjectNode();
    var array = config.putArray("fields");
    fields.forEach(
        (id, type) -> {
          var f =
              array
                  .addObject()
                  .put("id", id)
                  .put("type", type)
                  .put("name", id)
                  .put("enabled", true)
                  .put("group", "扩展信息")
                  .put("width", "half");
          if (type.equals("USER")) f.put("source", "user");
          db.update(
              "INSERT INTO"
                  + " eam_business_field(id,tenant_id,object_key,data_type,source_key,ever_published)"
                  + " VALUES(?,?,'equipment',?,?,1)",
              id,
              tenant,
              type,
              type.equals("USER") ? "user" : "");
        });
    db.update(
        "INSERT INTO eam_business_schema(tenant_id,object_key,draft,published,published_version)"
            + " VALUES(?,'equipment',CAST(? AS JSON),CAST(? AS JSON),1)",
        tenant,
        config.toString(),
        config.toString());
  }

  private void save(long id, JsonNode body) {
    values.save("equipment", id, body, 1, "", false);
  }

  @Test
  void scalarTypesClearAndImportPreparationKeepExistingValues() {
    String text = field("TEXT"),
        num = field("NUMBER"),
        date = field("DATE"),
        time = field("DATETIME"),
        bool = field("BOOLEAN");
    schema(Map.of(text, "TEXT", num, "NUMBER", date, "DATE", time, "DATETIME", bool, "BOOLEAN"));
    var data =
        json.createObjectNode()
            .put(text, "测试")
            .put(num, "12.340000")
            .put(date, "2026-11-18")
            .put(time, "2026-11-18T08:00:00")
            .put(bool, false);
    save(810001L, data);
    var row = values.read("equipment", 810001L);
    assertEquals("测试", row.customFields().get(text));
    assertEquals("12.340000", row.customFields().get(num));
    assertEquals("2026-11-18", row.customFields().get(date));
    assertEquals("2026-11-18T08:00", row.customFields().get(time));
    assertEquals(false, row.customFields().get(bool));
    assertEquals("12.34", row.customFieldLabels().get(num));
    assertEquals("否", row.customFieldLabels().get(bool));
    var errors = new LinkedHashMap<String, String>();
    var prepared =
        values.prepareNew(
            "equipment",
            configuration.runtime("equipment").configuration(),
            data,
            "",
            Map.of(),
            errors);
    assertTrue(errors.isEmpty());
    values.savePreparedNew("equipment", 810002L, prepared);
    assertEquals(row.customFields(), values.read("equipment", 810002L).customFields());
    save(810001L, json.createObjectNode().putNull(text));
    assertEquals(false, values.read("equipment", 810001L).customFields().containsKey(text));
    assertThrows(
        RuntimeException.class, () -> values.save("equipment", 810001L, data, 0, "", false));
  }

  @Test
  void retainedReferenceKeepsOriginalNameAndOtherTenantCannotDeleteIt() {
    String f = field("USER");
    schema(Map.of(f, "USER"));
    db.update(
        "INSERT INTO"
            + " eam_business_value(tenant_id,object_key,record_id,field_id,ordinal_no,reference_id,reference_name)"
            + " VALUES(?,'equipment',810001,?,0,'73001','历史姓名')",
        tenant,
        f);
    save(810001L, json.createObjectNode().put(f, "73001"));
    assertEquals("历史姓名", values.read("equipment", 810001L).customFieldLabels().get(f));
    verifyNoInteractions(references);
    when(auth.tenantId()).thenReturn(tenant + 1);
    values.remove("equipment", 810001L);
    when(auth.tenantId()).thenReturn(tenant);
    assertEquals("历史姓名", values.read("equipment", 810001L).customFieldLabels().get(f));
  }

  private long selects() {
    return db.queryForObject(
        "SHOW SESSION STATUS LIKE 'Com_select'", (r, n) -> Long.parseLong(r.getString(2)));
  }

  private long batchQueries(List<Long> ids) {
    sqlSessions.clearCache();
    long before = selects();
    values.batch("equipment", ids);
    return selects() - before;
  }

  @Test
  void batchReadsRemainBounded() {
    String f = field("TEXT");
    schema(Map.of(f, "TEXT"));
    save(810001L, json.createObjectNode().put(f, "a"));
    long one = batchQueries(List.of(810001L));
    long many = batchQueries(java.util.stream.LongStream.range(810001, 810041).boxed().toList());
    System.out.println("business-value Com_select one=" + one + ", forty=" + many);
    assertEquals(2, one);
    assertEquals(one, many);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void auditFailureRollsBackReplacementInCallingTransaction() {
    String f = field("TEXT");
    schema(Map.of(f, "TEXT"));
    try {
      new TransactionTemplate(transactions)
          .executeWithoutResult(s -> save(810001L, json.createObjectNode().put(f, "原值")));
      doThrow(new IllegalStateException("模拟审计失败")).when(audit).insert(any());
      assertThrows(
          IllegalStateException.class,
          () ->
              new TransactionTemplate(transactions)
                  .executeWithoutResult(s -> save(810001L, json.createObjectNode().put(f, "新值"))));
      assertEquals(
          "原值",
          db.queryForObject(
              "SELECT text_value FROM eam_business_value WHERE tenant_id=? AND record_id=810001",
              String.class,
              tenant));
    } finally {
      db.update("DELETE FROM eam_business_value WHERE tenant_id=?", tenant);
      db.update("DELETE FROM eam_business_config_event WHERE tenant_id=?", tenant);
      db.update("DELETE FROM eam_business_field WHERE tenant_id=?", tenant);
      db.update("DELETE FROM eam_business_schema WHERE tenant_id=?", tenant);
    }
  }
}
