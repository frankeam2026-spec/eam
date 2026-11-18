package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.experience.ExperienceSupport;
import com.windasoft.eam.dto.experience.HomeContracts;
import com.windasoft.eam.repository.experience.HomePreferenceRepository;
import com.windasoft.eam.repository.experience.HomeTemplateVersionRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.experience.HomeOverviewService;
import com.windasoft.eam.service.experience.HomeService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.support.SelectQueryLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 仅在本专项隔离库验证首页模板协议、角色批量读取、版本及回滚。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class HomeAdministrationMySqlIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    SystemUserQueryMySqlIT.database(p);
    p.add(
        "spring.datasource.url",
        () ->
            System.getenv("EAM_SYSTEM_TEST_URL")
                + "&profileSQL=true&logger=com.windasoft.eam.support.SelectQueryLogger");
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

  @Autowired HomeService home;
  @Autowired org.mybatis.spring.SqlSessionTemplate sqlSessions;
  @MockitoSpyBean HomeTemplateVersionRepository versions;
  @Autowired HomePreferenceRepository preferences;
  @Autowired HomeOverviewService overview;

  private long tenant;

  @BeforeEach
  void identity() {
    SelectQueryLogger.start();
    db.queryForObject("SELECT 1", Integer.class);
    assertEquals(1, SelectQueryLogger.stop(), "驱动计数器必须已接入");
    tenant = IdWorker.getId();
    when(auth.tenantId()).thenReturn(tenant);
    when(auth.userId()).thenReturn(73001L);
  }

  @Test
  void templateRoleValidationIsBatched() {
    var ids = new ArrayList<String>();
    for (int i = 0; i < 40; i++) {
      long role = IdWorker.getId();
      ids.add(Long.toString(role));
      db.update(
          "INSERT INTO eam_sys_role(id,tenant_id,code,name,status) VALUES(?,?,?,?,1)",
          role,
          tenant,
          "HOME-" + role,
          "首页角色" + i);
    }
    sqlSessions.clearCache();
    SelectQueryLogger.start();
    var saved =
        json.valueToTree(
            home.save(
                null,
                json.convertValue(
                    Map.of("name", "批量首页", "draft", Map.of("blocks", List.of()), "roleIds", ids),
                    HomeContracts.TemplateForm.class)));
    long count = SelectQueryLogger.stop();
    System.out.println("home-template SELECT role40=" + count);
    assertEquals(40, saved.path("roleIds").size());
    Assertions.assertTrue(count <= 5, "模板角色校验不得逐角色查询");
  }

  @Test
  void defaultTemplateLoadingDoesNotQueryEachPreset() {
    sqlSessions.clearCache();
    SelectQueryLogger.start();
    var list = home.templates();
    long count = SelectQueryLogger.stop();
    System.out.println("home-template SELECT presets=" + count);
    Assertions.assertTrue(list.size() >= 6);
    Assertions.assertTrue(count <= 4, "预置模板角色应一次读取，不逐预置查询");
  }

  private HomeContracts.TemplateForm form(String name, List<String> ids, Integer version) {
    var body = json.createObjectNode();
    body.put("name", name);
    body.set("roleIds", json.valueToTree(ids));
    body.set(
        "draft",
        json.valueToTree(
            Map.of(
                "blocks",
                List.of(
                    Map.of(
                        "id",
                        "todo",
                        "type",
                        "todo",
                        "title",
                        "事项",
                        "width",
                        6,
                        "limit",
                        5,
                        "scope",
                        "personal",
                        "module",
                        "")))));
    if (version != null) body.put("rowVersion", version);
    return json.convertValue(body, HomeContracts.TemplateForm.class);
  }

  private long role() {
    long role = IdWorker.getId();
    db.update(
        "INSERT INTO eam_sys_role(id,tenant_id,code,name,status) VALUES(?,?,?,?,1)",
        role,
        tenant,
        "ROLE-" + role,
        "模板角色");
    return role;
  }

  @Test
  void metadataWireAndPublishedRolesRemainCompatible() throws Exception {
    long role = role();
    var created = home.save(null, form("模板一", List.of(String.valueOf(role)), null));
    long id = Long.parseLong(created.id());
    var raw =
        db.queryForMap("SELECT * FROM v_home_template WHERE tenant_id=? AND id=?", tenant, id);
    var old = ExperienceSupport.view(raw);
    old.put("draft", json.readTree(raw.get("draft").toString()));
    old.put("roleIds", json.readTree(raw.get("role_ids").toString()));
    old.put("overviewDraft", null);
    assertEquals(json.valueToTree(old), json.valueToTree(created));
    var published = home.publish(id, new HomeContracts.Version(created.rowVersion()));
    home.save(id, form("模板二", List.of(), published.rowVersion()));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_home_template_version_role WHERE tenant_id=? AND"
                + " template_id=? AND role_id=?",
            Integer.class,
            tenant,
            id,
            role));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_home_template_role WHERE tenant_id=? AND template_id=?",
            Integer.class,
            tenant,
            id));
    when(auth.tenantId()).thenReturn(tenant + 1);
    assertThrows(
        IllegalArgumentException.class, () -> home.publish(id, new HomeContracts.Version(0)));
  }

  @Test
  void missingVersionCannotOverwriteInitialTemplate() {
    var created = home.save(null, form("初始模板", List.of(), null));
    long id = Long.parseLong(created.id());
    assertThrows(RuntimeException.class, () -> home.save(id, form("不应覆盖", List.of(), null)));
    assertEquals(
        "初始模板",
        db.queryForObject(
            "SELECT name FROM eam_home_template WHERE tenant_id=? AND id=?",
            String.class,
            tenant,
            id));
  }

  @Test
  void personalLayoutAndOverviewKeepVersionsAndIsolation() {
    var state = home.home(0L);
    assertEquals("0", state.selected().id());
    assertEquals(-1, state.rowVersion());
    var layout = json.valueToTree(Map.of("blocks", List.of()));
    var saved = home.layout(new HomeContracts.LayoutForm(0L, 1, -1, layout));
    assertEquals(0, saved.rowVersion());
    assertTrue(saved.layout().path("blocks").isArray());
    long user = 73002L;
    when(auth.userId()).thenReturn(user);
    assertEquals(-1, home.home(0L).rowVersion());
    var summary = overview.get(0L);
    assertEquals("0", summary.selected().id());
    assertEquals(-1, summary.rowVersion());
    assertTrue(summary.layout().has("schemaVersion"));
  }

  @Test
  void defaultAndRecentPreferenceDoNotOverwriteOneAnother() throws Exception {
    preferences.saveDefault(tenant, 73001L, 0L);
    preferences.lockRecent(tenant, 73001L);
    preferences.saveRecent(tenant, 73001L, "[{\"url\":\"/equipment\"}]");
    preferences.saveDefault(tenant, 73001L, 0L);
    var row = preferences.find(tenant, 73001L);
    assertEquals(0L, row.getDefaultTemplateId());
    assertEquals(json.readTree("[{\"url\":\"/equipment\"}]"), json.readTree(row.getRecentJson()));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void failedPresetVersionRollsBackAllHeaders() {
    try {
      Mockito.doThrow(new IllegalStateException("模拟发布版本失败"))
          .when(versions)
          .insert(ArgumentMatchers.any());
      assertThrows(IllegalStateException.class, home::templates);
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_home_template WHERE tenant_id=?", Integer.class, tenant));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_home_template_version WHERE tenant_id=?",
              Integer.class,
              tenant));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_home_template_role WHERE tenant_id=?",
              Integer.class,
              tenant));
    } finally {
      db.update("DELETE FROM eam_home_template_version WHERE tenant_id=?", tenant);
      db.update("DELETE FROM eam_home_template WHERE tenant_id=?", tenant);
    }
  }
}
