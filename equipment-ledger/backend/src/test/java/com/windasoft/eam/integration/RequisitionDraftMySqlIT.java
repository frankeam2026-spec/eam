package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.service.inventory.RequisitionService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.service.workflow.WorkflowRuntimeService;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

/** Runs against a disposable clone only; fixtures and cleanup are scoped to the test actor. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "EAM_REQUISITION_DRAFT_TEST_URL", matches = ".+")
class RequisitionDraftMySqlIT {
  static final long ACTOR = 991520001L, TASK = 991520002L;
  static final long WAREHOUSE = 2114966584203218946L, MATERIAL = 2114966756152905729L;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry p) {
    String url = System.getenv("EAM_REQUISITION_DRAFT_TEST_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:3308/eam_requisition_draft_test_20261030(\\?.*)?")) {
      throw new IllegalStateException("Requires isolated requisition draft database");
    }
    p.add("spring.datasource.url", () -> url);
    p.add("spring.datasource.username", () -> "root");
    p.add("spring.datasource.password", () -> "");
    p.add("eam.notification.secret-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean WorkflowRuntimeService workflow;
  @MockitoBean com.windasoft.eam.scheduler.system.NotificationScheduler notificationScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inspection.InspectionScheduler inspectionScheduler;
  @MockitoBean com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler maintenanceScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup cleanup;
  @MockitoBean com.windasoft.eam.scheduler.experience.RepairPlanScheduler repairScheduler;
  @MockitoBean com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler knowledgeScheduler;
  @Autowired RequisitionService service;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.windasoft.eam.repository.inventory.RequisitionRepository repository;

  @Autowired org.springframework.web.context.WebApplicationContext web;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.windasoft.eam.repository.inventory.RequisitionDraftRepository drafts;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.windasoft.eam.service.inventory.InventoryMutex mutex;

  @BeforeEach
  void fixture() {
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(ACTOR);
    var user = new SystemUser();
    user.setId(ACTOR);
    user.setDisplayName("draft tester");
    when(auth.user()).thenReturn(user);
    when(permissions.allowed("inventoryRequisition", "新增")).thenReturn(true);
    when(permissions.companyOfOrganization(71003L)).thenReturn(71002L);
    when(workflow.history("requisition", 0)).thenReturn(List.of());
    when(workflow.history(eq("requisition"), anyLong())).thenReturn(List.of());
    db.update(
        "INSERT INTO"
            + " eam_repair_order(id,tenant_id,order_no,equipment_code,equipment_name,fault_type,description,reporter,status,department_org_id,approval_gate)"
            + " VALUES(?,10001,'QA-DRAFT','QA-DRAFT','draft"
            + " device','test','test','test','处理中',71003,'EXEMPT')",
        TASK);
  }

  @AfterEach
  void cleanup() {
    db.update("DELETE FROM inv_issue_allocation WHERE tenant_id=10001 AND issue_id=991520003");
    db.update("DELETE FROM inv_issue WHERE tenant_id=10001 AND id=991520003");
    db.update("UPDATE inv_warehouse SET enabled=1 WHERE tenant_id=10001 AND id=?", WAREHOUSE);
    db.update(
        "DELETE e FROM inv_document_event e JOIN inv_requisition r ON r.tenant_id=e.tenant_id AND"
            + " r.id=e.document_id WHERE e.document_kind='领料' AND r.tenant_id=10001 AND"
            + " r.applicant_id=?",
        ACTOR);
    db.update(
        "DELETE l FROM inv_requisition_line l JOIN inv_requisition r ON r.tenant_id=l.tenant_id AND"
            + " r.id=l.requisition_id WHERE r.tenant_id=10001 AND r.applicant_id=?",
        ACTOR);
    db.update("DELETE FROM inv_requisition WHERE tenant_id=10001 AND applicant_id=?", ACTOR);
    db.update("DELETE FROM eam_repair_order WHERE tenant_id=10001 AND id=?", TASK);
  }

  Map<String, Object> form() {
    var body = new LinkedHashMap<String, Object>();
    body.put("taskKind", "维修");
    body.put("taskId", Long.toString(TASK));
    body.put("warehouseId", Long.toString(WAREHOUSE));
    body.put("remark", "  test  ");
    body.put(
        "lines", List.of(Map.of("materialId", Long.toString(MATERIAL), "quantity", "1.250000")));
    body.put("companyId", "71006");
    body.put("applicantId", "73001");
    return body;
  }

  Map<String, Object> save(Long id, Map<String, Object> form) {
    var input = json.convertValue(form, com.windasoft.eam.dto.inventory.RequisitionDraftForm.class);
    return json.convertValue(
        id == null ? service.create(input) : service.update(id, input),
        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
  }

  long id(Map<String, Object> row) {
    return Long.parseLong(row.get("id").toString());
  }

  @Test
  void createsAndUpdatesWithoutTrustingReadonlyFields() {
    var created = save(null, form());
    long id = id(created);
    assertEquals("71002", created.get("companyId"));
    assertEquals(Long.toString(ACTOR), created.get("applicantId"));
    assertEquals("test", created.get("remark"));
    assertEquals("草稿", created.get("status"));
    var wire = json.valueToTree(created);
    assertEquals(json.valueToTree(new RequisitionDetailBaseline(db).read(10001L, id, false)), wire);
    assertTrue(wire.path("id").isTextual());
    assertEquals("1.250000", wire.path("lines").get(0).path("quantity").asText());
    assertTrue(wire.has("workflow"));
    assertTrue(wire.has("reservations"));
    assertTrue(wire.has("issues"));
    assertTrue(wire.has("events"));
    var update = form();
    update.put("rowVersion", 0);
    update.put("remark", "changed");
    var updated = save(id, update);
    assertEquals(1, ((Number) updated.get("rowVersion")).intValue());
    assertEquals("changed", updated.get("remark"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM inv_requisition_line WHERE requisition_id=?", Integer.class, id));
  }

  @Test
  void duplicateMaterialRollsBackHeaderLinesAndEvent() {
    var body = form();
    body.put(
        "lines",
        List.of(
            Map.of("materialId", MATERIAL, "quantity", "1"),
            Map.of("materialId", MATERIAL, "quantity", "2")));
    assertThrows(com.windasoft.eam.common.FieldValidationException.class, () -> save(null, body));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM inv_requisition WHERE applicant_id=?", Integer.class, ACTOR));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM inv_document_event WHERE actor_id=?", Integer.class, ACTOR));
  }

  @Test
  void staleVersionAndWrongApplicantCannotReplaceLines() {
    var created = save(null, form());
    long id = id(created);
    var body = form();
    body.put("rowVersion", 8);
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> save(id, body)).getStatusCode().value());
    body.put("rowVersion", 0);
    when(auth.userId()).thenReturn(ACTOR + 10);
    assertThrows(IllegalArgumentException.class, () -> save(id, body));
    assertEquals(
        0,
        db.queryForObject("SELECT row_version FROM inv_requisition WHERE id=?", Integer.class, id));
  }

  @Test
  void otherCompanyWarehouseAndUnreleasedRepairAreRejected() {
    var body = form();
    body.put("warehouseId", "2114966619494092801");
    assertThrows(IllegalArgumentException.class, () -> save(null, body));
    db.update("UPDATE eam_repair_order SET status='审批中',approval_gate='REQUIRED' WHERE id=?", TASK);
    assertThrows(IllegalArgumentException.class, () -> save(null, form()));
  }

  @Test
  void tenantBoundaryAndQuantityScaleAreEnforced() {
    var created = save(null, form());
    long id = id(created);
    when(auth.tenantId()).thenReturn(10002L);
    assertThrows(IllegalArgumentException.class, () -> service.detail(id));
    when(auth.tenantId()).thenReturn(10001L);
    var body = form();
    body.put("lines", List.of(Map.of("materialId", MATERIAL, "quantity", "1.0000001")));
    assertThrows(com.windasoft.eam.common.FieldValidationException.class, () -> save(null, body));
  }

  @Test
  void failureAfterPersistenceRollsBackCreateAndUpdate() {
    var created = save(null, form());
    long id = id(created);
    int events =
        db.queryForObject(
            "SELECT COUNT(*) FROM inv_document_event WHERE tenant_id=10001 AND document_id=?",
            Integer.class,
            id);
    doThrow(new IllegalStateException("injected persistence failure"))
        .when(repository)
        .insertEvent(anyLong(), anyLong(), anyLong(), anyString(), anyLong(), anyString());
    var update = form();
    update.put("rowVersion", 0);
    update.put("remark", "must roll back");
    assertThrows(IllegalStateException.class, () -> save(id, update));
    assertEquals(
        "test",
        db.queryForObject("SELECT remark FROM inv_requisition WHERE id=?", String.class, id));
    assertEquals(
        0,
        db.queryForObject("SELECT row_version FROM inv_requisition WHERE id=?", Integer.class, id));
    assertEquals(
        events,
        db.queryForObject(
            "SELECT COUNT(*) FROM inv_document_event WHERE tenant_id=10001 AND document_id=?",
            Integer.class,
            id));
    assertThrows(IllegalStateException.class, () -> save(null, form()));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM inv_requisition WHERE applicant_id=?", Integer.class, ACTOR));
  }

  @Test
  void inactiveWarehouseMissingMaterialAndDeniedTaskCannotWrite() {
    db.update("UPDATE inv_warehouse SET enabled=0 WHERE tenant_id=10001 AND id=?", WAREHOUSE);
    assertThrows(IllegalArgumentException.class, () -> save(null, form()));
    db.update("UPDATE inv_warehouse SET enabled=1 WHERE tenant_id=10001 AND id=?", WAREHOUSE);
    var invalid = form();
    invalid.put("lines", List.of(Map.of("materialId", 991529999L, "quantity", "1")));
    assertEquals(
        404,
        assertThrows(ResponseStatusException.class, () -> save(null, invalid))
            .getStatusCode()
            .value());
    doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
        .when(permissions)
        .checkRepair(any(), eq("查看"));
    assertEquals(
        403,
        assertThrows(ResponseStatusException.class, () -> save(null, form()))
            .getStatusCode()
            .value());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM inv_requisition WHERE applicant_id=?", Integer.class, ACTOR));
  }

  @Test
  void nonDraftCannotBeEditedAndUnrelatedReaderCannotView() {
    long id = id(save(null, form()));
    db.update("UPDATE inv_requisition SET status='审批中' WHERE id=?", id);
    var update = form();
    update.put("rowVersion", 0);
    assertThrows(IllegalArgumentException.class, () -> save(id, update));
    when(auth.userId()).thenReturn(ACTOR + 10);
    doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
        .when(permissions)
        .checkInventoryWarehouse("inventoryRequisition", WAREHOUSE, "查看");
    assertEquals(
        403,
        assertThrows(ResponseStatusException.class, () -> service.detailView(id))
            .getStatusCode()
            .value());
    when(workflow.participant("requisition", id)).thenReturn(true);
    assertEquals(Long.valueOf(id), service.detailView(id).header().getId());
  }

  @Test
  void detailScopesMergedAllocationAmountAndOmitsUnauthorizedMoney() {
    long id = id(save(null, form()));
    long line =
        db.queryForObject(
            "SELECT id FROM inv_requisition_line WHERE requisition_id=?", Long.class, id);
    db.update(
        "INSERT INTO"
            + " inv_issue(id,tenant_id,code,company_id,warehouse_id,confirmation_key,preview_fingerprint,total_amount,actor_id,actor_name)"
            + " VALUES(991520003,10001,'QA-DRAFT-ISSUE',71002,?,'qa-draft','qa-draft',999,?,'tester')",
        WAREHOUSE,
        ACTOR);
    long layer =
        db.queryForObject(
            "SELECT MIN(id) FROM inv_cost_layer WHERE tenant_id=10001 AND"
                + " balance_id=2114966957332697089",
            Long.class);
    db.update(
        "INSERT INTO"
            + " inv_issue_allocation(id,tenant_id,issue_id,requisition_line_id,balance_id,cost_layer_id,quantity,amount,unit_cost)"
            + " VALUES(991520004,10001,991520003,?,2114966957332697089,?,1,12.50,12.5)",
        line,
        layer);
    long other = id(save(null, form()));
    long otherLine =
        db.queryForObject(
            "SELECT id FROM inv_requisition_line WHERE requisition_id=?", Long.class, other);
    db.update(
        "INSERT INTO"
            + " inv_issue_allocation(id,tenant_id,issue_id,requisition_line_id,balance_id,cost_layer_id,quantity,amount,unit_cost)"
            + " VALUES(991520005,10001,991520003,?,2114966957332697089,?,1,986.50,986.5)",
        otherLine,
        layer);
    var hidden = json.valueToTree(service.detailView(id));
    assertEquals(
        json.valueToTree(new RequisitionDetailBaseline(db).read(10001L, id, false)), hidden);
    assertFalse(hidden.path("issues").get(0).has("totalAmount"));
    assertFalse(hidden.path("issues").get(0).path("allocations").get(0).has("amount"));
    assertFalse(hidden.path("issues").get(0).path("allocations").get(0).has("unitCost"));
    assertFalse(hidden.path("issues").get(0).path("allocations").get(0).has("returnedAmount"));
    assertEquals("", hidden.path("issues").get(0).path("requisitionId").asText());
    when(permissions.inventoryWarehouseAllowed("inventoryRequisition", WAREHOUSE, "查看金额"))
        .thenReturn(true);
    var visible = json.valueToTree(service.detailView(id));
    assertEquals(
        json.valueToTree(new RequisitionDetailBaseline(db).read(10001L, id, true)), visible);
    assertEquals("12.50", visible.path("issues").get(0).path("totalAmount").asText());
    assertEquals(
        "12.500000",
        visible.path("issues").get(0).path("allocations").get(0).path("unitCost").asText());
    assertEquals("991520003", visible.path("issues").get(0).path("id").asText());
  }

  @Test
  void actualMvcConfigurationPreservesWireContractAndNestedErrors() throws Exception {
    var mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).build();
    var response =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/inventory/requisitions")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(form())))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn();
    var data = json.readTree(response.getResponse().getContentAsString()).path("data");
    assertTrue(data.path("id").isTextual());
    assertEquals("1.250000", data.path("lines").get(0).path("quantity").asText());
    var full = (com.fasterxml.jackson.databind.node.ObjectNode) data.deepCopy();
    full.put("remark", "round-trip");
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/inventory/requisitions/" + data.path("id").asText())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(full)))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.data.rowVersion")
                .value(1));
    var bad = form();
    bad.put("lines", List.of(Map.of("materialId", MATERIAL, "quantity", "abc")));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/inventory/requisitions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(bad)))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isBadRequest())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.fieldErrors['lines[0].quantity']")
                .isString());
  }

  @Test
  void jdbcStateChangesAreVisibleInsideAnEnclosingWorkflowTransaction() {
    new org.springframework.transaction.support.TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              long id = id(save(null, form()));
              assertEquals("草稿", service.detailView(id).header().getStatus().value());
              db.update(
                  "UPDATE inv_requisition SET status='审批中',row_version=row_version+1 WHERE"
                      + " tenant_id=10001 AND id=?",
                  id);
              var detail = service.detailView(id);
              assertEquals("审批中", detail.header().getStatus().value());
              assertEquals(1, detail.header().getRowVersion());
            });
  }

  @Test
  void updateKeepsInventoryRequestTaskWarehouseAndAscendingMaterialLockOrder() {
    long id = id(save(null, form()));
    clearInvocations(mutex, drafts);
    var update = form();
    update.put("rowVersion", 0);
    long second = 2114966786972651521L;
    update.put(
        "lines",
        List.of(
            Map.of("materialId", second, "quantity", "1"),
            Map.of("materialId", MATERIAL, "quantity", "1")));
    save(id, update);
    var order = inOrder(mutex, drafts);
    order.verify(mutex).enter();
    order.verify(drafts).header(10001L, id, true);
    order.verify(drafts).source(10001L, TASK, "维修", true);
    order.verify(drafts).warehouse(10001L, WAREHOUSE, true);
    order.verify(drafts).material(10001L, MATERIAL);
    order.verify(drafts).material(10001L, second);
  }

  @Test
  void versionOverflowCannotWrapIntoANegativeVersion() {
    long id = id(save(null, form()));
    db.update(
        "UPDATE inv_requisition SET row_version=? WHERE tenant_id=10001 AND id=?",
        Integer.MAX_VALUE,
        id);
    var update = form();
    update.put("rowVersion", Integer.MAX_VALUE);
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class, () -> save(id, update));
    assertEquals(
        Integer.MAX_VALUE,
        db.queryForObject("SELECT row_version FROM inv_requisition WHERE id=?", Integer.class, id));
  }
}
