package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.knowledge.*;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.system.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_AI_TEST_URL", matches = ".+")
class AiPlatformMySqlIT {
  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry p) {
    String url = System.getenv("EAM_AI_TEST_URL");
    if (url == null
        || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:3308/eam_ai_test_20261028(\\?.*)?"))
      throw new IllegalStateException("Requires isolated AI test database");
    p.add("spring.datasource.url", () -> url);
    p.add("spring.datasource.username", () -> "root");
    p.add("spring.datasource.password", () -> "");
    p.add("eam.notification.secret-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean NotificationScheduler notificationScheduler;
  @MockitoBean InspectionScheduler inspectionScheduler;
  @MockitoBean MaintenanceScheduler maintenanceScheduler;
  @MockitoBean InventoryReservationCleanup cleanup;
  @MockitoBean RepairPlanScheduler repairScheduler;
  @MockitoBean KnowledgeScheduler knowledgeScheduler;
  @MockitoBean KnowledgeEmbeddingClient embeddings;
  @Autowired JdbcTemplate db;
  @Autowired AiGateway gateway;
  @Autowired AiMonthlyUsageRepository runtime;
  @Autowired KnowledgeDocumentService documents;
  @Autowired KnowledgeVersionRepository jobs;
  @Autowired KnowledgeIndexService indexing;
  @Autowired KnowledgeSearchRepository search;
  @Autowired KnowledgeVectorClient vectors;

  @BeforeEach
  void identity() {
    when(auth.tenantId()).thenReturn(990112L);
    when(auth.userId()).thenReturn(990113L);
  }

  @Test
  void allMigrationsApplyAndBudgetIsEnforcedUnderConcurrency() throws Exception {
    assertTrue(
        db.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version='115' AND success=1",
                Integer.class)
            > 0);
    var config =
        new AiGateway.Settings(
            true,
            "https://test.aliyuncs.com/v1",
            "test-not-a-real-key",
            "test",
            "test",
            new BigDecimal("0.000060"),
            80,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            0,
            "",
            "",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    gateway.save(config);
    // Malformed stored cipher fails before any HTTP call, but records the conservative estimate.
    db.update("UPDATE eam_ai_setting SET api_secret='invalid' WHERE tenant_id=990112");
    var pool = Executors.newFixedThreadPool(2);
    var latch = new CountDownLatch(1);
    try {
      var futures = new ArrayList<Future<?>>();
      for (int i = 0; i < 2; i++)
        futures.add(
            pool.submit(
                () -> {
                  try {
                    latch.await();
                    gateway.post(
                        "/chat/completions", Map.of("max_tokens", 1, "messages", List.of()), false);
                  } catch (Exception expected) {
                  }
                }));
      latch.countDown();
      for (var f : futures) f.get(30, TimeUnit.SECONDS);
      assertEquals(
          1,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_ai_call WHERE tenant_id=990112", Integer.class));
      var usage = runtime.usage(990112, AiGateway.month(), false);
      assertEquals(0, usage.getReservedAmount().signum());
      assertTrue(usage.getEstimatedSpent().signum() > 0);
      assertEquals(
          "UNKNOWN",
          db.queryForObject("SELECT status FROM eam_ai_call WHERE tenant_id=990112", String.class));
    } finally {
      pool.shutdownNow();
      db.update("DELETE FROM eam_ai_call WHERE tenant_id=990112");
      db.update("DELETE FROM eam_ai_monthly_usage WHERE tenant_id=990112");
      db.update("DELETE FROM eam_ai_setting WHERE tenant_id=990112");
    }
  }

  @Test
  void realQdrantCanUpsertAndFilterWithoutSendingDocuments() throws Exception {
    String model = "integration-test-20261028";
    vectors.ensure(model, 2);
    vectors.upsert(
        model,
        List.of(
            new KnowledgeVectorClient.Point(990114, 990112, 990113, 990115, List.of(1.0, 0.0))));
    assertEquals(
        List.of(990114L), vectors.search(model, List.of(1.0, 0.0), 990112, List.of(990115L)));
    assertTrue(vectors.search(model, List.of(1.0, 0.0), 990116, List.of(990115L)).isEmpty());
  }

  @Test
  void uploadParseIndexPublishAndRevokeRunsThroughRealStorage() throws Exception {
    when(permissions.scope(anyString(), any(PermissionAction.class)))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    db.update(
        "INSERT IGNORE INTO eam_org_unit(id,tenant_id,name,code,org_type,status,deleted)"
            + " VALUES(990120,990112,'AI Test','AI-TEST','公司',1,0)");
    byte[] bytes;
    try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument();
        var out = new ByteArrayOutputStream()) {
      var page = new org.apache.pdfbox.pdmodel.PDPage();
      pdf.addPage(page);
      try (var content = new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf, page)) {
        content.beginText();
        content.setFont(
            new org.apache.pdfbox.pdmodel.font.PDType1Font(
                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),
            12);
        content.newLineAtOffset(40, 700);
        content.showText("P-103 lubrication port: see figure 2.");
        content.endText();
      }
      pdf.save(out);
      bytes = out.toByteArray();
    }
    var uploaded = documents.upload(990120, "Integration manual", bytes).data();
    long document = Long.parseLong(uploaded.id()), version = Long.parseLong(uploaded.versionId());
    var row = jobs.parsing().stream().filter(r -> r.getId() == version).findFirst().orElseThrow();
    String attempt = jobs.claim(jobs.find(990112, version));
    documents.parseVersion(row, attempt);
    assertEquals(
        "READY",
        db.queryForObject(
            "SELECT parse_status FROM eam_knowledge_version WHERE id=?", String.class, version));
    when(embeddings.model()).thenReturn("it-manual");
    when(embeddings.embed(any()))
        .thenAnswer(
            i -> ((List<?>) i.getArgument(0)).stream().map(v -> List.of(1.0, 0.0)).toList());
    documents.bind(document, 0, true, List.of());
    indexing.index(document, 1);
    indexing.publish(document, 1);
    var allowed = permissions.scope("knowledge", PermissionAction.VIEW);
    assertEquals(List.of(version), search.versions(990112, allowed, "it-manual"));
    long chunk =
        db.queryForObject(
            "SELECT c.id FROM eam_knowledge_chunk c JOIN eam_knowledge_page p ON"
                + " p.tenant_id=c.tenant_id AND p.id=c.page_id WHERE p.version_id=? LIMIT 1",
            Long.class,
            version);
    assertEquals(1, search.chunks(990112, allowed, List.of(chunk)).size());
    assertTrue(documents.pageImage(document, version, 1).length > 0);
    db.update("UPDATE eam_knowledge_document SET status='DISABLED' WHERE id=?", document);
    assertTrue(search.chunks(990112, allowed, List.of(chunk)).isEmpty());
    assertThrows(ResponseStatusException.class, () -> documents.original(document, version));
  }

  @Test
  void failedParsingCanRetryWithVersionCheckAndAtomicRollback() throws Exception {
    when(permissions.scope(anyString(), any(PermissionAction.class)))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    db.update(
        "INSERT IGNORE INTO eam_org_unit(id,tenant_id,name,code,org_type,status,deleted)"
            + " VALUES(990120,990112,'AI Test','AI-TEST','公司',1,0)");
    var uploaded =
        documents
            .upload(990120, "Retry test", "%PDF-invalid".getBytes(StandardCharsets.US_ASCII))
            .data();
    long document = Long.parseLong(uploaded.id()), version = Long.parseLong(uploaded.versionId());
    String attempt = jobs.claim(jobs.find(990112, version));
    jobs.failed(990112, version, attempt);
    documents.retryParse(document, 0);
    assertEquals(
        "PENDING",
        db.queryForObject(
            "SELECT parse_status FROM eam_knowledge_version WHERE id=?", String.class, version));
    assertThrows(ResponseStatusException.class, () -> documents.retryParse(document, 0));
    assertThrows(IllegalArgumentException.class, () -> documents.retryParse(document, 1));
    assertEquals(
        1L,
        db.queryForObject(
            "SELECT row_version FROM eam_knowledge_document WHERE id=?", Long.class, document));
    var history = documents.versions(document, 1, 20);
    assertEquals(1L, history.total());
    jobs.failed(990112, version, jobs.claim(jobs.find(990112, version)));
  }
}
