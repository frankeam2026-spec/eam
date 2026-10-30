package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.FieldValidationException;
import com.windasoft.eam.dto.equipment.EquipmentForm;
import com.windasoft.eam.dto.equipment.EquipmentView;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.equipment.EquipmentWriteRepository;
import com.windasoft.eam.service.equipment.EquipmentCommandService;
import com.windasoft.eam.service.equipment.EquipmentQueryService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "EAM_EQUIPMENT_COMMAND_TEST_URL", matches = ".+")
class EquipmentCommandMySqlIT {
  static final long LOCATION = 991620001L;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry p) {
    String url = System.getenv("EAM_EQUIPMENT_COMMAND_TEST_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:3308/eam_equipment_command_test_20261030(\\?.*)?")) {
      throw new IllegalStateException("Requires isolated equipment command database");
    }
    p.add("spring.datasource.url", () -> url);
    p.add("spring.datasource.username", () -> "root");
    p.add("spring.datasource.password", () -> "");
    p.add("eam.notification.secret-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean com.windasoft.eam.scheduler.system.NotificationScheduler notificationScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inspection.InspectionScheduler inspectionScheduler;
  @MockitoBean com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler maintenanceScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup cleanup;
  @MockitoBean com.windasoft.eam.scheduler.experience.RepairPlanScheduler repairScheduler;
  @MockitoBean com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler knowledgeScheduler;
  @Autowired EquipmentCommandService service;
  @Autowired EquipmentQueryService queries;
  @Autowired EquipmentWriteRepository writes;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  @Autowired org.springframework.web.context.WebApplicationContext web;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.windasoft.eam.service.business.BusinessValueService extensions;

  @BeforeEach
  void fixture() {
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(73001L);
    var user = new SystemUser();
    user.setId(73001L);
    user.setTenantId(10001L);
    user.setDisplayName("equipment tester");
    when(auth.user()).thenReturn(user);
    when(permissions.requireDepartment(anyLong()))
        .thenAnswer(c -> writes.organization(c.getArgument(0)));
    when(permissions.companyOfOrganization(any()))
        .thenAnswer(
            c -> {
              Long id = c.getArgument(0);
              return Objects.equals(id, 71003L) ? 71002L : null;
            });
    db.update(
        "INSERT INTO eam_base_item(id,tenant_id,item_type,code,name,company_org_id)"
            + " VALUES(?,10001,'LOCATION','QA-COMMAND-LOCATION','测试位置',71002)",
        LOCATION);
    db.update(
        "UPDATE eam_business_schema SET"
            + " published_version=0,published=JSON_OBJECT('fields',JSON_ARRAY()) WHERE"
            + " tenant_id=10001 AND object_key='equipment'");
  }

  Map<String, Object> input(String code) {
    var form = new LinkedHashMap<String, Object>();
    form.put("code", code);
    form.put("name", "测试设备");
    form.put("state", "正常");
    form.put("departmentOrgId", "71003");
    form.put("locationId", Long.toString(LOCATION));
    form.put("ownerIds", List.of("73001"));
    form.put("customFields", Map.of());
    form.put("customSchemaVersion", 0);
    form.put("images", List.of());
    form.put("documents", List.of());
    for (var field :
        Map.of("categoryId", "CATEGORY", "unitId", "UNIT", "useStateId", "USE_STATUS").entrySet()) {
      form.put(
          field.getKey(),
          db.queryForObject(
              "SELECT MIN(id) FROM eam_base_item WHERE tenant_id=10001 AND item_type=? AND status=1"
                  + " AND deleted=0",
              Long.class,
              field.getValue()));
    }
    return form;
  }

  EquipmentForm form(Map<String, Object> body) {
    return json.convertValue(body, EquipmentForm.class);
  }

  long id(EquipmentView view) {
    return Long.parseLong(view.id());
  }

  @Test
  void coreCreateAndEditPreserveOwnersAndWireValues() {
    var body = input("QA-COMMAND-CORE");
    body.put("amount", "123.45");
    body.put("purchaseDate", "2026-10-01");
    body.put("company", "伪造公司");
    body.put("department", "伪造部门");
    body.put("owner", "伪造人员");
    var created = service.create(form(body));
    assertEquals("71002", created.companyOrgId());
    assertEquals("71003", created.departmentOrgId());
    assertNotEquals("伪造人员", created.owner());
    assertEquals(List.of("73001"), created.ownerIds());
    assertEquals("123.45", created.amount());
    body.put("name", "修改设备");
    body.put("ownerIds", List.of());
    var updated = service.updateDetails(id(created), form(body), 0);
    assertEquals(1, updated.rowVersion());
    assertEquals(List.of(), updated.ownerIds());
    assertEquals("修改设备", updated.name());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_equipment_owner WHERE tenant_id=10001 AND equipment_id=?",
            Integer.class,
            id(created)));
    assertEquals(
        new BigDecimal("123.45"),
        db.queryForObject(
            "SELECT acquired_amount FROM eam_equipment WHERE id=?", BigDecimal.class, id(created)));
  }

  @Test
  void editingRejectsOversizedModelBeforePersistence() {
    var body = input("QA-COMMAND-LENGTH");
    var created = service.create(form(body));
    body.put("model", "x".repeat(121));
    var failure =
        assertThrows(
            FieldValidationException.class,
            () -> service.updateDetails(id(created), form(body), 0));
    assertEquals("model", failure.field());
  }

  @Test
  void independentBomSaveReturnsTheNewVersion() {
    var created = service.create(form(input("QA-COMMAND-VERSION")));
    var changed = service.updateRelations(id(created), json.createArrayNode(), 0, false);
    assertEquals(1, changed.rowVersion());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT row_version FROM eam_equipment WHERE id=?", Integer.class, id(created)));
  }

  @Test
  void staleVersionAndDeniedEditCannotAlterOwners() {
    var body = input("QA-COMMAND-GUARD");
    var created = service.create(form(body));
    body.put("ownerIds", List.of());
    assertEquals(
        409,
        assertThrows(
                ResponseStatusException.class,
                () -> service.updateDetails(id(created), form(body), 9))
            .getStatusCode()
            .value());
    doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
        .when(permissions)
        .checkEquipment(any(), eq("编辑"));
    assertEquals(
        403,
        assertThrows(
                ResponseStatusException.class,
                () -> service.updateDetails(id(created), form(body), 0))
            .getStatusCode()
            .value());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_equipment_owner WHERE tenant_id=10001 AND equipment_id=?",
            Integer.class,
            id(created)));
  }

  @Test
  void editingRejectsAmountPrecisionAndInvalidFlagsForInternalCallers() {
    var body = input("QA-COMMAND-INPUT");
    var created = service.create(form(body));
    body.put("amount", "1.001");
    assertEquals(
        "amount",
        assertThrows(
                FieldValidationException.class,
                () -> service.updateDetails(id(created), form(body), 0))
            .field());
    body.remove("amount");
    body.put("specialEquipment", "unknown");
    assertEquals(
        "specialEquipment",
        assertThrows(
                FieldValidationException.class,
                () -> service.updateDetails(id(created), form(body), 0))
            .field());
  }

  @Test
  void savingCoreDetailsDoesNotReplaceIndependentRelations() {
    var child = service.create(form(input("QA-COMMAND-CHILD")));
    var body = input("QA-COMMAND-PARENT");
    body.put("children", List.of(Map.of("equipmentId", child.id())));
    var parent = service.create(form(body));
    body.put("children", List.of());
    body.put("name", "只改基础信息");
    var updated = service.updateDetails(id(parent), form(body), 0);
    assertEquals(1, updated.children().size());
    assertEquals(child.id(), updated.children().getFirst().get("equipmentId"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_equipment_child WHERE tenant_id=10001 AND parent_id=?",
            Integer.class,
            id(parent)));
  }

  @Test
  void existingDisabledOwnerMayRemainButNewDisabledOwnerIsRejected() {
    var body = input("QA-COMMAND-OLD-OWNER");
    var created = service.create(form(body));
    db.update("UPDATE eam_sys_user SET status=0 WHERE tenant_id=10001 AND id=73001");
    var retained = service.updateDetails(id(created), form(body), 0);
    assertEquals(List.of("73001"), retained.ownerIds());
    body.put("code", "QA-COMMAND-NEW-OWNER");
    assertThrows(IllegalArgumentException.class, () -> service.create(form(body)));
  }

  @Test
  void extensionFailureRollsBackEquipmentOwnerAndVersionWrites() {
    var body = input("QA-COMMAND-ROLLBACK");
    var created = service.create(form(body));
    doThrow(new IllegalStateException("injected extension persistence failure"))
        .when(extensions)
        .save(
            eq("equipment"),
            anyLong(),
            any(com.fasterxml.jackson.databind.JsonNode.class),
            any(),
            anyString(),
            anyBoolean());
    body.put("name", "must roll back");
    body.put("ownerIds", List.of());
    var nested = new org.springframework.transaction.support.TransactionTemplate(transactions);
    nested.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
    assertThrows(
        IllegalStateException.class,
        () -> nested.execute(tx -> service.updateDetails(id(created), form(body), 0)));
    assertEquals(
        "测试设备",
        db.queryForObject("SELECT name FROM eam_equipment WHERE id=?", String.class, id(created)));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT row_version FROM eam_equipment WHERE id=?", Integer.class, id(created)));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_equipment_owner WHERE tenant_id=10001 AND equipment_id=?",
            Integer.class,
            id(created)));
  }

  @Test
  void mvcPreservesResponseShapeAndReturnsExactIdAndEditErrors() throws Exception {
    var mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).build();
    var body = input("QA-COMMAND-HTTP");
    var saved =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/equipment")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn();
    var data = json.readTree(saved.getResponse().getContentAsString()).path("data");
    assertTrue(data.path("id").isTextual());
    assertEquals("73001", data.path("ownerIds").get(0).asText());
    body.put("ownerIds", List.of(new BigDecimal("73001.9")));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/equipment")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isBadRequest())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.fieldErrors['ownerIds[0]']")
                .isString());
    body.put("ownerIds", List.of("73001"));
    body.put("model", "x".repeat(121));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/equipment/" + data.path("id").asText() + "/details?rowVersion=0")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isBadRequest())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.fieldErrors.model")
                .isString());
  }

  @Test
  void tenantBoundaryAndCurrentRowReadsAreEnforced() {
    var created = service.create(form(input("QA-COMMAND-FRESH")));
    assertEquals("测试设备", writes.find(10001L, id(created)).getName());
    db.update("UPDATE eam_equipment SET name='当前名称' WHERE tenant_id=10001 AND id=?", id(created));
    assertEquals("当前名称", writes.find(10001L, id(created)).getName());
    when(auth.tenantId()).thenReturn(10002L);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.updateDetails(id(created), form(input("QA-COMMAND-FRESH")), 0));
  }

  @Test
  void resolvedLocationPathIsValidatedBeforeUpdatingTheEntity() {
    var body = input("QA-COMMAND-PATH");
    var created = service.create(form(body));
    for (int i = 0; i < 25; i++) {
      db.update(
          "INSERT INTO eam_base_item(id,tenant_id,item_type,code,name,company_org_id,parent_id)"
              + " VALUES(?,10001,'LOCATION',?,?,71002,?)",
          991621000L + i,
          "QA-COMMAND-PATH-" + i,
          "位置".repeat(30),
          i == 0 ? null : 991621000L + i - 1);
    }
    body.put("locationId", 991621024L);
    assertEquals(
        "functionLocation",
        assertThrows(
                FieldValidationException.class,
                () -> service.updateDetails(id(created), form(body), 0))
            .field());
  }

  @Test
  void deletedExistingOwnerRetainsTheStoredDisplayName() {
    var body = input("QA-COMMAND-OWNER-NAME");
    var created = service.create(form(body));
    db.update("UPDATE eam_sys_user SET deleted=1 WHERE tenant_id=10001 AND id=73001");
    var changed = service.updateDetails(id(created), form(body), 0);
    assertEquals(created.owner(), changed.owner());
    assertEquals(List.of("73001"), changed.ownerIds());
  }
}
