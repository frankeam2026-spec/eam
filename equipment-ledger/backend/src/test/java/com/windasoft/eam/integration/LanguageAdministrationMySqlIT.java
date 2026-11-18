package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.BuiltinMessages;
import com.windasoft.eam.repository.i18n.LanguageEventRepository;
import com.windasoft.eam.repository.system.UserPreferenceRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.i18n.LanguageService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
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

/** 仅在本专项隔离库验证品牌配置的版本、素材访问、事务回滚及并发引用一致性。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class LanguageAdministrationMySqlIT {
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

  @Autowired LanguageService languages;
  @Autowired UserPreferenceRepository preferences;
  @MockitoSpyBean LanguageEventRepository events;

  private long tenant;

  @BeforeEach
  void identity() {
    tenant = IdWorker.getId();
    when(auth.tenantId()).thenReturn(tenant);
    when(auth.userId()).thenReturn(73001L);
  }

  @Test
  void preferencesPreserveOtherSettingAndTenant() {
    preferences.saveTheme(tenant, 73001L, "pink");
    preferences.saveLocale(tenant, 73001L, "en-US");
    assertEquals("pink", preferences.theme(tenant, 73001L));
    assertEquals("en-US", preferences.locale(tenant, 73001L));
    preferences.saveTheme(tenant, 73001L, "navy");
    assertEquals("en-US", preferences.locale(tenant, 73001L));
    assertEquals("blue", preferences.theme(tenant + 1, 73001L));
    assertEquals("zh-CN", preferences.locale(tenant + 1, 73001L));
  }

  private com.fasterxml.jackson.databind.node.ObjectNode pack() {
    var pack = json.createObjectNode();
    pack.put("formatVersion", 1);
    pack.put("locale", "fr-FR");
    pack.put("nativeName", "Français");
    pack.put("direction", "ltr");
    var key =
        BuiltinMessages.get("zh-CN").entrySet().stream()
            .filter(
                e ->
                    !e.getValue().matches(".*[{}|@<].*")
                        && !e.getValue().isBlank()
                        && !e.getKey().startsWith("calendar."))
            .findFirst()
            .orElseThrow()
            .getKey();
    pack.putObject("messages").put(key, "Texte");
    return pack;
  }

  @Test
  void publicationStateMessagesAndVersionRemainCompatible() {
    var pack = pack();
    assertTrue(languages.publish("fr-FR", 0, pack).valid());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT row_version FROM eam_language WHERE tenant_id=? AND locale='fr-FR'",
            Integer.class,
            tenant));
    assertFalse(languages.enabled("fr-FR"));
    languages.state("fr-FR", 1, true);
    assertEquals("fr-FR", languages.savePreference("fr-FR").effectiveLocale());
    assertTrue(languages.resource("fr-FR").messages().containsValue("Texte"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_language_message WHERE tenant_id=?", Integer.class, tenant));
    assertThrows(RuntimeException.class, () -> languages.publish("fr-FR", 0, pack));
    assertEquals(
        2,
        db.queryForObject(
            "SELECT row_version FROM eam_language WHERE tenant_id=? AND locale='fr-FR'",
            Integer.class,
            tenant));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void auditFailureRollsBackEntireLanguagePublication() {
    try {
      doThrow(new IllegalStateException("审计写入失败"))
          .when(events)
          .insert(
              org.mockito.ArgumentMatchers.eq(tenant),
              org.mockito.ArgumentMatchers.eq("fr-FR"),
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.anyLong(),
              org.mockito.ArgumentMatchers.anyInt(),
              org.mockito.ArgumentMatchers.anyInt());
      assertThrows(IllegalStateException.class, () -> languages.publish("fr-FR", 0, pack()));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_language WHERE tenant_id=?", Integer.class, tenant));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_language_message WHERE tenant_id=?",
              Integer.class,
              tenant));
    } finally {
      db.update("DELETE FROM eam_language_event WHERE tenant_id=?", tenant);
      db.update("DELETE FROM eam_language_message WHERE tenant_id=?", tenant);
      db.update("DELETE FROM eam_language WHERE tenant_id=?", tenant);
    }
  }
}
