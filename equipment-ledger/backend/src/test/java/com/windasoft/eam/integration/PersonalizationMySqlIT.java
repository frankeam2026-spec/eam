package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.BrandingUpload;
import com.windasoft.eam.dto.system.PersonalizationForm;
import com.windasoft.eam.repository.system.BrandingAssetRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.service.system.PersonalizationService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.imageio.ImageIO;
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
class PersonalizationMySqlIT {
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
  @MockitoSpyBean ObjectMapper json;
  @Autowired JdbcTemplate db;

  @Autowired PersonalizationService service;
  @MockitoSpyBean BrandingAssetRepository assets;

  private long tenant;

  @BeforeEach
  void identity() {
    tenant = IdWorker.getId();
    when(auth.tenantId()).thenReturn(tenant);
    when(auth.userId()).thenReturn(73001L);
  }

  private byte[] png() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  private String upload() throws Exception {
    var bytes = png();
    return json.valueToTree(service.upload("logo", new BrandingUpload(bytes, bytes.length, false)))
        .path("id")
        .asText();
  }

  private PersonalizationForm form(String name, String logo, int version) {
    return new PersonalizationForm(name, logo, null, "center", version);
  }

  private void initial() {
    db.update("INSERT INTO eam_tenant_branding(tenant_id) VALUES(?)", tenant);
  }

  private void cleanup() {
    db.update("DELETE FROM eam_branding_event WHERE tenant_id=?", tenant);
    db.update("DELETE FROM eam_tenant_branding WHERE tenant_id=?", tenant);
    db.update("DELETE FROM eam_branding_asset WHERE tenant_id=?", tenant);
  }

  @Test
  void defaultsVersionsAndPublicProjectionKeepOriginalShape() throws Exception {
    var initial = json.valueToTree(service.get());
    assertEquals("设备运维平台", initial.path("systemName").asText());
    assertEquals("", initial.path("updatedAt").asText());
    assertEquals("", initial.path("logoFileId").asText());
    var saved = json.valueToTree(service.save(form("测试系统", null, 0)));
    assertEquals(1, saved.path("rowVersion").asInt());
    assertEquals("测试系统", saved.path("systemName").asText());
    assertFalse(saved.path("updatedAt").asText().isEmpty());
    var before =
        json.readTree(
            db.queryForObject(
                "SELECT before_json FROM eam_branding_event WHERE tenant_id=?",
                String.class,
                tenant));
    assertEquals("", before.path("updatedAt").asText());
    db.update(
        "UPDATE eam_tenant_branding SET updated_at='2026-11-18 08:00:00' WHERE tenant_id=?",
        tenant);
    assertEquals("2026-11-18T08:00", service.get().updatedAt());
    var pub = json.valueToTree(service.publicConfig(tenant));
    assertEquals(4, pub.size());
    assertFalse(pub.has("rowVersion"));
    assertEquals("测试系统", pub.path("systemName").asText());
    assertThrows(RuntimeException.class, () -> service.save(form("旧版本不应保存", null, 0)));
    assertEquals(
        "测试系统",
        db.queryForObject(
            "SELECT system_name FROM eam_tenant_branding WHERE tenant_id=?", String.class, tenant));
  }

  @Test
  void uploadOwnershipPublicAccessAndHistoryRetentionRemainEnforced() throws Exception {
    String id = upload();
    assertArrayEquals(png(), service.privateAsset(id).bytes());
    assertThrows(RuntimeException.class, () -> service.publicAsset(tenant, id));
    when(auth.userId()).thenReturn(73002L);
    assertThrows(RuntimeException.class, () -> service.privateAsset(id));
    assertThrows(RuntimeException.class, () -> service.save(form("不能选择他人临时素材", id, 0)));
    when(auth.userId()).thenReturn(73001L);
    service.save(form("品牌", id, 0));
    assertArrayEquals(png(), service.publicAsset(tenant, id).bytes());
    assertFalse(json.valueToTree(service.discardUpload(id)).path("deleted").asBoolean());
    service.save(form("品牌", null, 1));
    assertThrows(RuntimeException.class, () -> service.publicAsset(tenant, id));
    assertFalse(json.valueToTree(service.discardUpload(id)).path("deleted").asBoolean());
    String temporary = upload();
    assertTrue(json.valueToTree(service.discardUpload(temporary)).path("deleted").asBoolean());
    when(auth.tenantId()).thenReturn(tenant + 1);
    assertThrows(RuntimeException.class, () -> service.privateAsset(id));
  }

  @Test
  void invalidImageDoesNotPersistAsset() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.upload("logo", new BrandingUpload(new byte[] {1, 2, 3}, 3, false)));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_branding_asset WHERE tenant_id=?", Integer.class, tenant));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void checkedAuditFailureRollsBackConfiguration() throws Exception {
    initial();
    try {
      doThrow(new JsonProcessingException("模拟受检审计异常") {}).when(json).writeValueAsString(any());
      assertThrows(JsonProcessingException.class, () -> service.save(form("不应保存", null, 0)));
      assertEquals(
          "设备运维平台",
          db.queryForObject(
              "SELECT system_name FROM eam_tenant_branding WHERE tenant_id=?",
              String.class,
              tenant));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT row_version FROM eam_tenant_branding WHERE tenant_id=?",
              Integer.class,
              tenant));
      db.update("DELETE FROM eam_tenant_branding WHERE tenant_id=?", tenant);
      assertThrows(JsonProcessingException.class, () -> service.save(form("首次保存也必须回滚", null, 0)));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_tenant_branding WHERE tenant_id=?", Integer.class, tenant));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_branding_event WHERE tenant_id=?", Integer.class, tenant));
    } finally {
      cleanup();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void saveAndDiscardCannotPublishMissingAsset() throws Exception {
    initial();
    String id = upload();
    var selected = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var workers = Executors.newFixedThreadPool(2);
    try {
      doAnswer(
              call -> {
                var result = call.callRealMethod();
                selected.countDown();
                if (!release.await(10, TimeUnit.SECONDS))
                  throw new IllegalStateException("未释放保存步骤");
                return result;
              })
          .when(assets)
          .lockMetadata(tenant, id, "logo");
      var saved = workers.submit(() -> service.save(form("并发品牌", id, 0)));
      assertTrue(selected.await(10, TimeUnit.SECONDS));
      var deleted = workers.submit(() -> service.discardUpload(id));
      try {
        deleted.get(500, TimeUnit.MILLISECONDS);
      } catch (TimeoutException waiting) {
        /* 修复后取消操作应等待保存事务。 */
      } finally {
        release.countDown();
      }
      saved.get(15, TimeUnit.SECONDS);
      deleted.get(15, TimeUnit.SECONDS);
      String published =
          db.queryForObject(
              "SELECT logo_file_id FROM eam_tenant_branding WHERE tenant_id=?",
              String.class,
              tenant);
      assertEquals(id, published);
      assertEquals(
          1,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_branding_asset WHERE tenant_id=? AND id=?",
              Integer.class,
              tenant,
              id),
          "不能提交指向已取消素材的品牌配置");
    } finally {
      release.countDown();
      workers.shutdownNow();
      assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
      cleanup();
    }
  }
}
