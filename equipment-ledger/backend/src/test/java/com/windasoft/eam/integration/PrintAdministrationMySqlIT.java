package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.printing.PrintForms;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.printing.DocumentPrintRepository;
import com.windasoft.eam.repository.printing.PrintJobRepository;
import com.windasoft.eam.repository.printing.PrintTemplateRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.inventory.OutboundService;
import com.windasoft.eam.service.printing.DocumentPrintLayout;
import com.windasoft.eam.service.printing.DocumentPrintService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
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

/** 仅在本专项隔离库验证打印模板协议、生成幂等、权限及回滚。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class PrintAdministrationMySqlIT {
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
  @Autowired DocumentPrintService printing;
  @Autowired PrintJobRepository jobs;
  @Autowired DocumentPrintRepository sources;
  @MockitoSpyBean PrintTemplateRepository templates;
  @MockitoSpyBean DocumentPrintLayout layout;
  @MockitoBean OutboundService outbounds;
  private long tenant;

  @BeforeEach
  void identity() {
    tenant = IdWorker.getId();
    when(auth.tenantId()).thenReturn(tenant);
    when(auth.userId()).thenReturn(73001L);
    var user = new SystemUser();
    user.setDisplayName("打印测试人");
    when(auth.user()).thenReturn(user);
    when(permissions.scope(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
  }

  private PrintForms.Template form(String name, boolean def, Integer version) {
    return new PrintForms.Template(name, null, true, def, version, layout.defaults());
  }

  @Test
  void templateWireAndDefaultVersionRemainCompatible() throws Exception {
    var saved = printing.saveTemplate(null, form("原模板", true, null));
    long id = Long.parseLong(saved.id());
    var old = new java.util.LinkedHashMap<String, Object>();
    var row =
        db.queryForMap("SELECT * FROM eam_print_template WHERE tenant_id=? AND id=?", tenant, id);
    old.put("id", id + "");
    old.put("name", row.get("name"));
    old.put("companyId", "");
    old.put("companyName", "");
    old.put("enabled", true);
    old.put("isDefault", true);
    old.put("rowVersion", row.get("row_version"));
    old.put("config", json.readTree(row.get("config_json").toString()));
    old.put("updatedAt", row.get("updated_at").toString());
    old.put("canEdit", true);
    old.put("canDelete", true);
    assertEquals(json.valueToTree(old), json.valueToTree(saved));
    printing.saveTemplate(null, form("新默认", true, null));
    assertEquals(1, templates.find(tenant, id).getRowVersion());
    assertThrows(
        IllegalArgumentException.class, () -> printing.saveTemplate(id, form("过期", false, 0)));
    printing.deleteTemplate(id, 1);
    assertEquals(1, printing.templates().size());
    assertThrows(IllegalArgumentException.class, () -> templates.find(tenant + 1, id));
  }

  @Test
  void sourceProjectionAndJobRetryKeepPermissionsAndWire() throws Exception {
    long company = IdWorker.getId(), warehouse = IdWorker.getId(), group = IdWorker.getId();
    db.update(
        "INSERT INTO eam_org_unit(id,tenant_id,name,code,org_type,status) VALUES(?,?,?,?,?,1)",
        company,
        tenant,
        "测试公司",
        "PRINT",
        "公司");
    db.update(
        "INSERT INTO inv_warehouse(id,tenant_id,company_id,code,name) VALUES(?,?,?,?,?)",
        warehouse,
        tenant,
        company,
        "PRINT",
        "测试仓");
    db.update(
        "INSERT INTO"
            + " inv_issue_group(id,tenant_id,code,warehouse_id,confirmation_key,actor_id,actor_name)"
            + " VALUES(?,?,?,?,?,?,?)",
        group,
        tenant,
        "CK-TEST",
        warehouse,
        "print-group-key",
        73001,
        "测试人");
    String reference = "g" + group;
    assertEquals(company, sources.source(tenant, reference).companyId());
    assertEquals(List.of(), sources.allocations(tenant, reference));
    when(outbounds.detail(reference))
        .thenReturn(
            Map.of(
                "code",
                "CK-TEST",
                "status",
                "草稿",
                "warehouseName",
                "测试仓",
                "createdAt",
                "2026-11-18T10:00:00.000",
                "lines",
                List.of(
                    Map.of("materialCode", "M-1", "materialName", "轴承", "quantity", "2.000000"))));
    byte[] pdf;
    try (var doc = new PDDocument();
        var bytes = new ByteArrayOutputStream()) {
      doc.addPage(new PDPage());
      doc.save(bytes);
      pdf = bytes.toByteArray();
    }
    Mockito.doReturn(pdf).when(layout).render(Mockito.any(), Mockito.anyMap(), Mockito.anyList());
    var preview = printing.preview(new PrintForms.Output(reference, "system", null, null));
    var explicitNull = new java.util.LinkedHashMap<String, Object>();
    explicitNull.put("code", "CK-TEST");
    explicitNull.put("status", "草稿");
    explicitNull.put("confirmedAt", null);
    explicitNull.put("rowVersion", null);
    explicitNull.put("lines", List.of(Map.of("quantity", "2.0")));
    when(outbounds.detail(reference)).thenReturn(explicitNull);
    printing.preview(new PrintForms.Output(reference, "system", null, null));
    Mockito.verify(layout)
        .render(
            Mockito.any(),
            Mockito.argThat(
                h ->
                    "".equals(h.get("confirmedAt"))
                        && h.containsKey("rowVersion")
                        && h.get("rowVersion") == null),
            Mockito.anyList());
    when(outbounds.detail(reference))
        .thenReturn(
            Map.of(
                "code",
                "CK-TEST",
                "status",
                "草稿",
                "warehouseName",
                "测试仓",
                "createdAt",
                "2026-11-18T10:00:00.000",
                "lines",
                List.of(
                    Map.of("materialCode", "M-1", "materialName", "轴承", "quantity", "2.000000"))));
    var request =
        new PrintForms.Output(reference, "system", preview.fingerprint(), "print_request_key_01");
    var first = printing.generate(request);
    var repeat = printing.generate(request);
    assertEquals(first, repeat);
    assertEquals("1", first.pageCount());
    assertEquals(null, first.templateId());
    assertTrue(first.id().matches("[0-9]+"));
    try (var document = Loader.loadPDF(printing.file(Long.parseLong(first.id())))) {
      assertEquals(1, document.getNumberOfPages());
    }
    assertEquals(
        1L,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_print_job WHERE tenant_id=?", Long.class, tenant));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            printing.generate(
                new PrintForms.Output(reference, "system", "wrong", "print_request_key_01")));
    assertEquals(null, jobs.retry(tenant + 1, 73001, "print_request_key_01", false));
    Mockito.doThrow(
            new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN))
        .when(permissions)
        .checkInventoryWarehouse("inventoryOutbound", warehouse, "打印");
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> printing.file(Long.parseLong(first.id())));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void failedTemplateInsertRestoresPreviousDefault() {
    try {
      var original = printing.saveTemplate(null, form("默认", true, null));
      Mockito.doThrow(new IllegalStateException("模拟写入失败")).when(templates).insert(Mockito.any());
      assertThrows(
          IllegalStateException.class, () -> printing.saveTemplate(null, form("失败默认", true, null)));
      var row = templates.find(tenant, Long.parseLong(original.id()));
      assertEquals(1, row.getIsDefault());
      assertEquals(0, row.getRowVersion());
    } finally {
      db.update("DELETE FROM eam_print_template WHERE tenant_id=?", tenant);
    }
  }
}
