package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.SaveDraftRequest;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.business.BusinessConfigEventRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.business.BusinessConfigService;
import com.windasoft.eam.service.business.BusinessPublicationService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.UUID;
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

/** 配置保存、发布和历史恢复仅在本专项隔离库验证，保留旧字段身份与版本语义。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class BusinessConfigurationMySqlIT {
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
  @MockitoSpyBean BusinessConfigEventRepository audit;
  @Autowired BusinessPublicationService publication;

  @BeforeEach
  void identity() {
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(73001L);
    var user = new SystemUser();
    user.setDisplayName("配置管理员");
    when(auth.user()).thenReturn(user);
  }

  private JsonNode read() {
    return json.valueToTree(configuration.configuration("equipment"));
  }

  private void save(JsonNode draft, int version) {
    configuration.save("equipment", new SaveDraftRequest(draft, version));
  }

  @Test
  void draftPublishAndRestoreKeepVersionsAndHistoricalFields() {
    var initial = read();
    var draft = initial.path("draft").deepCopy();
    String first = UUID.randomUUID().toString(), second = UUID.randomUUID().toString();
    ((ArrayNode) draft.path("fields"))
        .add(
            json.createObjectNode()
                .put("id", first)
                .put("name", "治理字段甲")
                .put("type", "TEXT")
                .put("group", "扩展信息")
                .put("width", "half")
                .put("enabled", true));
    save(draft, initial.path("version").asInt());
    assertEquals(initial.path("publishedVersion"), read().path("publishedVersion"));
    var pub = json.valueToTree(publication.publish("equipment", read().path("version").asInt()));
    int firstVersion = pub.path("publishedVersion").asInt();
    assertTrue(
        json.valueToTree(configuration.runtime("equipment"))
            .path("configuration")
            .path("fields")
            .toString()
            .contains(first));
    var later = read().path("draft").deepCopy();
    ((ArrayNode) later.path("fields"))
        .add(
            json.createObjectNode()
                .put("id", second)
                .put("name", "治理字段乙")
                .put("type", "TEXT")
                .put("group", "扩展信息")
                .put("width", "half")
                .put("enabled", true));
    save(later, read().path("version").asInt());
    publication.publish("equipment", read().path("version").asInt());
    configuration.restore("equipment", firstVersion, read().path("version").asInt());
    boolean retained = false;
    for (var field : read().path("draft").path("fields")) {
      if (field.path("id").asText().equals(second)) {
        retained = true;
        assertEquals(false, field.path("enabled").asBoolean());
      }
    }
    assertTrue(retained);
    assertTrue(json.valueToTree(configuration.versions("equipment")).size() >= 2);
    assertEquals(
        json.valueToTree(
            db.queryForList(
                "SELECT version,actor_id,created_at FROM eam_business_schema_version WHERE"
                    + " tenant_id=10001 AND object_key='equipment' ORDER BY version DESC")),
        json.valueToTree(configuration.versions("equipment")));
    assertEquals(
        json.valueToTree(
            db.queryForList(
                "SELECT actor_name actor,action,details,created_at time FROM"
                    + " eam_business_config_event WHERE tenant_id=10001 AND object_key='equipment'"
                    + " AND record_id IS NULL ORDER BY created_at DESC,id DESC LIMIT 100")),
        json.valueToTree(configuration.events("equipment")));

    assertTrue(json.valueToTree(configuration.events("equipment")).toString().contains("恢复配置草稿"));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void failedPublicationAuditRollsBackVersionAndCurrentSchema() {
    long tenant = IdWorker.getId();
    when(auth.tenantId()).thenReturn(tenant);
    db.update(
        "INSERT INTO eam_business_schema(tenant_id,object_key,draft,published)"
            + " VALUES(?,'equipment',JSON_OBJECT('fields',JSON_ARRAY()),JSON_OBJECT('fields',JSON_ARRAY()))",
        tenant);
    try {
      doThrow(new IllegalStateException("模拟审计失败")).when(audit).insert(any());
      assertThrows(IllegalStateException.class, () -> publication.publish("equipment", 0));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT published_version FROM eam_business_schema WHERE tenant_id=? AND"
                  + " object_key='equipment'",
              Integer.class,
              tenant));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT row_version FROM eam_business_schema WHERE tenant_id=? AND"
                  + " object_key='equipment'",
              Integer.class,
              tenant));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_business_schema_version WHERE tenant_id=?",
              Integer.class,
              tenant));
    } finally {
      db.update(
          "DELETE FROM eam_business_schema WHERE tenant_id=? AND object_key='equipment'", tenant);
    }
  }

  @Test
  void staleVersionCannotReplaceDraftAndTenantCannotReadOtherSchema() {
    var initial = read();
    assertThrows(
        RuntimeException.class,
        () -> save(initial.path("draft"), initial.path("version").asInt() + 1));
    assertEquals(initial, read());
    when(auth.tenantId()).thenReturn(999999999L);
    assertThrows(IllegalArgumentException.class, () -> configuration.configuration("equipment"));
  }
}
