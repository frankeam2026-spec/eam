package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.knowledge.KnowledgeChunkRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeDocumentRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeIndexRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeVersionRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.knowledge.KnowledgeContentWriter;
import com.windasoft.eam.service.knowledge.KnowledgeDocumentService;
import com.windasoft.eam.service.knowledge.KnowledgeEmbeddingClient;
import com.windasoft.eam.service.knowledge.KnowledgeIndexService;
import com.windasoft.eam.service.knowledge.KnowledgePdfParser;
import com.windasoft.eam.service.knowledge.KnowledgeVectorClient;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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

/** 仅在本专项隔离库验证知识库发布、并发换版及回滚。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class KnowledgeAdministrationMySqlIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    SystemUserQueryMySqlIT.database(p);
    p.add("eam.upload.directory", () -> STORE.toString());
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean NotificationScheduler notificationScheduler;
  @MockitoBean MaintenanceScheduler maintenanceScheduler;
  @MockitoBean InspectionScheduler inspectionScheduler;
  @MockitoBean InventoryReservationCleanup cleanup;
  @MockitoBean RepairPlanScheduler repairScheduler;
  @MockitoBean KnowledgeScheduler knowledgeScheduler;
  private static final Path STORE = directory();

  private static Path directory() {
    try {
      return Files.createTempDirectory("eam-knowledge-it-");
    } catch (java.io.IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @AfterAll
  static void cleanFiles() throws Exception {
    if (!STORE.getFileName().toString().startsWith("eam-knowledge-it-"))
      throw new IllegalStateException("仅可清理本测试创建的目录");
    try (var paths = Files.walk(STORE)) {
      for (var p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
    }
  }

  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired KnowledgeDocumentService documents;
  @Autowired KnowledgeIndexService indexes;
  @Autowired KnowledgeContentWriter writer;
  @Autowired KnowledgeDocumentRepository headers;
  @Autowired KnowledgeIndexRepository indexJobs;
  @MockitoSpyBean KnowledgeVersionRepository versions;
  @MockitoSpyBean KnowledgeChunkRepository chunks;
  @MockitoBean KnowledgeEmbeddingClient embeddings;
  @MockitoBean KnowledgeVectorClient vectors;
  private long tenant, company;

  @BeforeEach
  void identity() throws Exception {
    tenant = IdWorker.getId();
    company = IdWorker.getId();
    when(auth.tenantId()).thenReturn(tenant);
    when(auth.userId()).thenReturn(73001L);
    when(permissions.scope(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    db.update(
        "INSERT INTO eam_org_unit(id,tenant_id,name,code,org_type,status) VALUES(?,?,?,?,?,1)",
        company,
        tenant,
        "知识公司",
        "KNOWLEDGE",
        "公司");
    when(embeddings.model()).thenReturn("isolated-embedding-v1");
    when(embeddings.embed(Mockito.any()))
        .thenAnswer(
            i -> ((List<?>) i.getArgument(0)).stream().map(v -> List.of(1.0, 0.0)).toList());
  }

  private byte[] pdf() throws Exception {
    try (var doc = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      var page = new PDPage();
      doc.addPage(page);
      try (var content = new PDPageContentStream(doc, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(40, 700);
        content.showText("P-103 lubrication port: see figure 2.");
        content.endText();
      }
      doc.save(out);
      return out.toByteArray();
    }
  }

  private long files() throws Exception {
    var p = STORE.resolve("knowledge").resolve(Long.toString(tenant));
    if (!Files.isDirectory(p)) return 0;
    try (var values = Files.list(p)) {
      return values.count();
    }
  }

  private void cleanTenant() {
    for (var table :
        List.of(
            "eam_knowledge_chunk_image",
            "eam_knowledge_image",
            "eam_knowledge_chunk",
            "eam_knowledge_page",
            "eam_knowledge_index_job",
            "eam_knowledge_case_source",
            "eam_knowledge_equipment",
            "eam_knowledge_version",
            "eam_knowledge_document",
            "eam_org_unit")) db.update("DELETE FROM " + table + " WHERE tenant_id=?", tenant);
  }

  @Test
  void uploadParseIndexPublishAndOriginalWireRemainCompatible() throws Exception {
    assertEquals(Long.toString(company), documents.companies().data().getFirst().id());
    var uploaded = documents.upload(company, "资料", pdf()).data();
    long id = Long.parseLong(uploaded.id()), version = Long.parseLong(uploaded.versionId());
    assertEquals("PENDING", uploaded.parseStatus());
    var old =
        db.queryForMap(
            "SELECT CAST(d.id AS CHAR) id,CAST(d.company_id AS CHAR)"
                + " companyId,d.title,d.document_type documentType,d.status,d.row_version"
                + " rowVersion,d.updated_at updatedAt,(SELECT v.parse_status FROM"
                + " eam_knowledge_version v WHERE v.tenant_id=d.tenant_id AND v.document_id=d.id"
                + " ORDER BY v.version_no DESC LIMIT 1) parseStatus,(SELECT j.status FROM"
                + " eam_knowledge_index_job j JOIN eam_knowledge_version v ON"
                + " v.tenant_id=j.tenant_id AND v.id=j.version_id WHERE v.tenant_id=d.tenant_id AND"
                + " v.document_id=d.id ORDER BY j.id DESC LIMIT 1) indexStatus FROM"
                + " eam_knowledge_document d WHERE d.tenant_id=?",
            tenant);
    assertEquals(
        json.readTree(json.writeValueAsString(old)),
        json.readTree(json.writeValueAsString(documents.list("", 1, 20).data().getFirst())));
    var job =
        versions.parsing().stream().filter(v -> v.getId() == version).findFirst().orElseThrow();
    String attempt = versions.claim(versions.find(tenant, version));
    documents.parseVersion(job, attempt);
    assertEquals("READY", versions.latest(tenant, id).getParseStatus());
    assertTrue(documents.pages(id).data().getFirst().chunkCount() > 0);
    documents.bind(id, 0, true, List.of());
    indexes.queue(id, 1);
    assertEquals(
        "PENDING",
        indexJobs.indexing().stream().anyMatch(j -> j.documentId() == id) ? "PENDING" : "MISSING");
    indexes.index(id, 1);
    indexes.publish(id, 1);
    assertEquals("PUBLISHED", headers.document(tenant, id).getStatus());
    assertTrue(Files.isRegularFile(documents.original(id, version)));
    assertTrue(documents.pageImage(id, version, 1).length > 0);
    var historic =
        db.queryForMap(
            "SELECT CAST(v.id AS CHAR) id,v.version_no versionNo,v.parse_status"
                + " parseStatus,v.parser_version parserVersion,v.error_message"
                + " errorMessage,v.created_at createdAt,(d.published_version_id=v.id) published"
                + " FROM eam_knowledge_version v JOIN eam_knowledge_document d ON"
                + " d.tenant_id=v.tenant_id AND d.id=v.document_id WHERE v.tenant_id=? AND v.id=?",
            tenant,
            version);
    assertEquals(
        json.readTree(json.writeValueAsString(historic)),
        json.readTree(json.writeValueAsString(documents.versions(id, 1, 20).data().getFirst())));
    Mockito.verify(vectors).upsert(Mockito.eq("isolated-embedding-v1"), Mockito.anyList());
    when(auth.tenantId()).thenReturn(tenant + 1);
    org.junit.jupiter.api.Assertions.assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> documents.original(id, version));
    when(auth.tenantId()).thenReturn(tenant);
    when(permissions.scope("knowledge", "查看"))
        .thenReturn(new PermissionScope(false, null, Set.of(), false));
    assertEquals(0, documents.list("", 1, 20).total());
    org.junit.jupiter.api.Assertions.assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> documents.original(id, version));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void versionInsertFailureRollsBackHeaderAndCleansOnlyNewFile() throws Exception {
    try {
      Mockito.doThrow(new IllegalStateException("模拟版本保存失败")).when(versions).insert(Mockito.any());
      org.junit.jupiter.api.Assertions.assertThrows(
          IllegalStateException.class, () -> documents.upload(company, "失败", pdf()));
      assertEquals(
          0L,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_knowledge_document WHERE tenant_id=?", Long.class, tenant));
      assertEquals(0, files());
    } finally {
      cleanTenant();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void retryRequiresFailedStateAndRollsBackVersionAdvance() throws Exception {
    try {
      long id = Long.parseLong(documents.upload(company, "重试", pdf()).data().id());
      long version = versions.latest(tenant, id).getId();
      String attempt = versions.claim(versions.find(tenant, version));
      versions.failed(tenant, version, attempt);
      documents.retryParse(id, 0);
      org.junit.jupiter.api.Assertions.assertThrows(
          IllegalArgumentException.class, () -> documents.retryParse(id, 1));
      assertEquals(1L, headers.document(tenant, id).getRowVersion());
      org.junit.jupiter.api.Assertions.assertThrows(
          org.springframework.web.server.ResponseStatusException.class,
          () -> documents.retryParse(id, 0));
    } finally {
      cleanTenant();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void chunkFailureRollsBackPagesAndParsingState() throws Exception {
    try {
      long id = Long.parseLong(documents.upload(company, "内容回滚", pdf()).data().id());
      long version = versions.latest(tenant, id).getId();
      String attempt = versions.claim(versions.find(tenant, version));
      Mockito.doThrow(new IllegalStateException("模拟切片保存失败")).when(chunks).insert(Mockito.anyList());
      org.junit.jupiter.api.Assertions.assertThrows(
          IllegalStateException.class,
          () ->
              writer.commitAttempt(
                  tenant,
                  version,
                  List.of(new KnowledgePdfParser.Page(1, "source", false, List.of("source"))),
                  attempt));
      assertEquals(
          0L,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_knowledge_page WHERE tenant_id=?", Long.class, tenant));
      assertEquals("RUNNING", versions.latest(tenant, id).getParseStatus());
    } finally {
      cleanTenant();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentReplacementKeepsOneNextVersionAndCleansLoserFile() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    try {
      byte[] bytes = pdf();
      long id = Long.parseLong(documents.upload(company, "并发版本", bytes).data().id());
      var start = new CountDownLatch(1);
      java.util.concurrent.Callable<Boolean> save =
          () -> {
            start.await();
            try {
              documents.replace(id, 0, bytes);
              return true;
            } catch (IllegalStateException conflict) {
              if (!conflict.getMessage().equals("文档已变更，请刷新")) throw conflict;
              return false;
            }
          };
      var first = pool.submit(save);
      var second = pool.submit(save);
      start.countDown();
      assertEquals(
          1,
          (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0));
      assertEquals(2, documents.versions(id, 1, 20).total());
      assertEquals(1L, headers.document(tenant, id).getRowVersion());
      assertEquals(2, files());
    } finally {
      pool.shutdownNow();
      cleanTenant();
    }
  }

  @Test
  void resetAttemptsCannotRestoreEarlierWorkerOwnership() throws Exception {
    long document = Long.parseLong(documents.upload(company, "租约回归", pdf()).data().id());
    var initial = versions.latest(tenant, document);
    long version = initial.getId();
    String oldParse = versions.claim(initial);
    versions.failed(tenant, version, oldParse);
    documents.retryParse(document, 0);
    var fresh = versions.latest(tenant, document);
    String currentParse = versions.claim(fresh);
    assertEquals(1, versions.latest(tenant, document).getParseAttempts());
    assertNotEquals(oldParse, currentParse);
    assertThrows(IllegalStateException.class, () -> versions.heartbeat(tenant, version, oldParse));
    var parsed = List.of(new KnowledgePdfParser.Page(1, "正文", false, List.of("正文")));
    assertThrows(
        IllegalStateException.class, () -> writer.commitAttempt(tenant, version, parsed, oldParse));
    versions.failed(tenant, version, oldParse);
    assertEquals("RUNNING", versions.latest(tenant, document).getParseStatus());
    writer.commitAttempt(tenant, version, parsed, currentParse);

    String model = "lease-regression";
    long job = IdWorker.getId();
    indexJobs.queue(job, tenant, 73001L, version, model, 1);
    var oldCandidate =
        indexJobs.indexing().stream().filter(c -> c.id() == job).findFirst().orElseThrow();
    String oldIndex = indexJobs.claim(oldCandidate);
    indexJobs.finish(tenant, version, model, oldIndex, false);
    indexJobs.queue(job, tenant, 73001L, version, model, 1);
    indexJobs.failIndex(oldCandidate);
    var currentCandidate =
        indexJobs.indexing().stream().filter(c -> c.id() == job).findFirst().orElseThrow();
    assertThrows(IllegalStateException.class, () -> indexJobs.claim(oldCandidate));
    String currentIndex = indexJobs.claim(currentCandidate);
    assertEquals(
        1,
        db.queryForObject(
            "SELECT attempts FROM eam_knowledge_index_job WHERE id=?", Integer.class, job));
    assertNotEquals(oldIndex, currentIndex);
    assertThrows(
        IllegalStateException.class, () -> indexJobs.heartbeat(tenant, version, model, oldIndex));
    assertThrows(
        IllegalStateException.class,
        () -> indexJobs.finish(tenant, version, model, oldIndex, true));
    assertThrows(
        IllegalStateException.class,
        () -> indexJobs.finish(tenant, version, model, oldIndex, false));
    indexJobs.finish(tenant, version, model, currentIndex, true);
    assertTrue(indexJobs.ready(tenant, version, model));
  }
}
