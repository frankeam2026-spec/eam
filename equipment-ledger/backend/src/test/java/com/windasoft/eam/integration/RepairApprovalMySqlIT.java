package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.repair.RepairForm;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.dto.workflow.WorkflowDefinition.Node;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.repair.RepairWorkflowRepository;
import com.windasoft.eam.service.repair.*;
import com.windasoft.eam.service.system.*;
import com.windasoft.eam.service.workflow.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_REPAIR_APPROVAL_TEST_URL", matches = ".+")
@Transactional
class RepairApprovalMySqlIT {
  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry p) {
    String url = System.getenv("EAM_REPAIR_APPROVAL_TEST_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:3308/eam_repair_approval_test_20261030(\\?.*)?"))
      throw new IllegalStateException("Requires isolated repair approval database");
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
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired WorkflowTemplateService templates;
  @Autowired WorkflowRuntimeService runtime;
  @Autowired RepairOrderService orders;
  @Autowired RepairApprovalService approvals;
  @Autowired RepairWorkflowService repairWorkflow;
  @Autowired RepairWorkflowRepository repairs;
  @Autowired com.windasoft.eam.service.inventory.RequisitionService requisitions;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  long actor = 73001L, fault;

  @BeforeEach
  void fixture() {
    actor = 73001L;
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenAnswer(i -> actor);
    when(auth.user())
        .thenAnswer(
            i -> {
              var u = new SystemUser();
              u.setId(actor);
              u.setTenantId(10001L);
              u.setOrgId(71003L);
              u.setDisplayName("approval tester");
              return u;
            });
    when(permissions.scope(eq("workflow"), anyString()))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    when(permissions.companyAllowed(eq("workflow"), anyString(), anyLong())).thenReturn(true);
    when(permissions.companyOfOrganization(any()))
        .thenAnswer(
            i -> {
              Long id = i.getArgument(0);
              return id != null && Set.of(71006L, 71007L).contains(id) ? 71006L : 71002L;
            });
    db.update(
        "INSERT INTO"
            + " eam_equipment(id,tenant_id,equipment_code,name,category,company,department,company_org_id,department_org_id,category_id)"
            + " VALUES(990410001,10001,'QA-APPROVAL','审批设备','测试分类','测试公司','测试部门',71002,71003,?)",
        db.queryForObject(
            "SELECT MIN(id) FROM eam_base_item WHERE tenant_id=10001 AND item_type='CATEGORY' AND"
                + " deleted=0",
            Long.class));
    fault =
        db.queryForObject(
            "SELECT MIN(id) FROM eam_base_item WHERE tenant_id=10001 AND item_type='FAULT_TYPE' AND"
                + " deleted=0 AND status=1",
            Long.class);
  }

  WorkflowDefinition publish(String business, String mode, String company) {
    var d =
        templates.save(
            new WorkflowDefinition(
                null,
                0,
                "审批回归",
                business,
                company,
                "",
                null,
                "DRAFT",
                List.of(
                    new Node(
                        "start", "START", "提交", "approve", "ANY", List.of(), List.of(), List.of()),
                    new Node(
                        "approve",
                        "APPROVAL",
                        "主管审批",
                        "end",
                        mode,
                        company.equals("71002") ? List.of("73001", "73002") : List.of("990410002"),
                        List.of(),
                        List.of()),
                    new Node("end", "END", "结束", null, "ANY", List.of(), List.of(), List.of()))));
    return templates.publish(Long.parseLong(d.id()), d.rowVersion());
  }

  void enable() {
    var v = (Map<?, ?>) templates.approvalSetting(71002);
    templates.approvalSetting(71002, true, ((Number) v.get("rowVersion")).longValue());
  }

  RepairForm form() {
    return new RepairForm(
        "QA-APPROVAL",
        "审批设备",
        "",
        fault,
        "紧急",
        "测试故障",
        "不可信前端人员",
        null,
        null,
        null,
        json.createArrayNode(),
        json.createObjectNode(),
        db.queryForObject(
            "SELECT published_version FROM eam_business_schema WHERE tenant_id=10001 AND"
                + " object_key='repair_report'",
            Integer.class));
  }

  long create() {
    var result = (Map<?, ?>) orders.create(form()).get("data");
    return Long.parseLong(result.get("id").toString());
  }

  Map<String, Object> instance(long id) {
    return db.queryForMap(
        "SELECT * FROM wf_instance WHERE tenant_id=10001 AND business_type='repair_report' AND"
            + " business_id=? ORDER BY round_no DESC LIMIT 1",
        id);
  }

  long n(Map<String, Object> m, String k) {
    return ((Number) m.get(k)).longValue();
  }

  void act(long id, boolean approve) {
    var i = instance(id);
    long task =
        db.queryForObject(
            "SELECT id FROM wf_approval WHERE tenant_id=10001 AND instance_id=? AND user_id=? AND"
                + " state='PENDING'",
            Long.class,
            n(i, "id"),
            actor);
    runtime.act(n(i, "id"), task, n(i, "row_version"), approve, "测试意见");
  }

  @Test
  void optionalCompanyAndPublishedButNotEnabledKeepExistingPath() {
    publish("repair_report", "ANY", "71002");
    long id = create();
    var row = repairs.findOrder(10001, id);
    assertEquals("EXEMPT", row.getApprovalGate());
    assertEquals("待派工", row.getStatus());
    assertEquals(row.getReportedAt(), row.getApprovalReadyAt());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM wf_instance WHERE business_id=?", Integer.class, id));
  }

  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void enablingWithoutTemplateRollsBackTheEntireSubmission() {
    long count =
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_repair_order WHERE tenant_id=10001", Long.class);
    try {
      var transaction =
          new org.springframework.transaction.support.TransactionTemplate(transactions);
      assertThrows(
          IllegalArgumentException.class,
          () ->
              transaction.execute(
                  s -> {
                    enable();
                    return create();
                  }));
      assertEquals(
          count,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_repair_order WHERE tenant_id=10001", Long.class));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM wf_binding WHERE tenant_id=10001 AND company_id=71002 AND"
                  + " business_type='repair_report'",
              Integer.class));
    } finally {
      db.update("DELETE FROM eam_equipment WHERE tenant_id=10001 AND id=990410001");
    }
  }

  @Test
  void stoppingTemplateDoesNotDisableTheCompanyRequirement() {
    var d = publish("repair_report", "ANY", "71002");
    enable();
    templates.disable(Long.parseLong(d.id()), d.rowVersion());
    assertThrows(IllegalArgumentException.class, this::create);
  }

  @Test
  void anyApprovalReleasesDispatchAndRoutesOnlyRepairTodos() throws Exception {
    publish("repair_report", "ANY", "71002");
    enable();
    long id = create();
    var row = repairs.findOrder(10001, id);
    assertEquals("审批中", row.getStatus());
    assertNull(row.getApprovalReadyAt());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            repairWorkflow.action(
                id,
                repairs.findOrder(10001, id).getRowVersion(),
                json.readTree("{\"action\":\"dispatch\",\"userIds\":[\"73001\"]}")));
    assertEquals(
        2,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_repair_inbox WHERE order_id=? AND kind='报修审批' AND done=0",
            Integer.class,
            id));
    assertEquals(
        0,
        db.queryForObject("SELECT COUNT(*) FROM inv_inbox WHERE document_id=?", Integer.class, id));
    act(id, true);
    row = repairs.findOrder(10001, id);
    assertEquals("待派工", row.getStatus());
    assertNotNull(row.getApprovalReadyAt());
    assertFalse(row.getApprovalReadyAt().isBefore(row.getReportedAt()));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_repair_inbox WHERE order_id=? AND is_todo=1 AND done=0",
            Integer.class,
            id));
    assertThrows(
        IllegalArgumentException.class,
        () -> orders.update(id, form(), repairs.findOrder(10001, id).getRowVersion()));
    var dispatched =
        repairWorkflow.action(
            id,
            repairs.findOrder(10001, id).getRowVersion(),
            json.readTree("{\"action\":\"dispatch\",\"userIds\":[\"73001\"]}"));
    assertEquals("待接单", dispatched.getStatus());
  }

  @Test
  void allApprovalRejectWithdrawAndResubmitKeepRoundsAndVersion() {
    publish("repair_report", "ALL", "71002");
    enable();
    long id = create();
    act(id, true);
    assertEquals("审批中", repairs.findOrder(10001, id).getStatus());
    actor = 73002L;
    act(id, false);
    assertEquals("待提交", repairs.findOrder(10001, id).getStatus());
    actor = 73001L;
    doThrow(
            new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN))
        .when(permissions)
        .checkRepair(any(), eq("编辑"));
    orders.update(id, form(), repairs.findOrder(10001, id).getRowVersion());
    verify(permissions, atLeastOnce()).checkRepair(any(), eq("报修"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_repair_inbox WHERE order_id=? AND kind='报修待修改' AND done=0",
            Integer.class,
            id));
    approvals.submit(id, repairs.findOrder(10001, id).getRowVersion());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_repair_inbox WHERE order_id=? AND kind='报修待修改' AND done=0",
            Integer.class,
            id));
    assertEquals(2, n(instance(id), "round_no"));
    var i = instance(id);
    actor = 73002L;
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> runtime.withdraw(n(i, "id"), n(i, "row_version")));
    actor = 73001L;
    runtime.withdraw(n(i, "id"), n(i, "row_version"));
    assertEquals("待提交", repairs.findOrder(10001, id).getStatus());
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> approvals.submit(id, 0));
  }

  @Test
  void turningCompanyOffNeverReleasesExistingPendingOrder() {
    publish("repair_report", "ANY", "71002");
    enable();
    long id = create();
    var s = (Map<?, ?>) templates.approvalSetting(71002);
    templates.approvalSetting(71002, false, ((Number) s.get("rowVersion")).longValue());
    assertThrows(
        IllegalArgumentException.class,
        () -> approvals.requireReleased(repairs.findOrder(10001, id)));
    assertEquals("审批中", repairs.findOrder(10001, id).getStatus());
    assertEquals("EXEMPT", repairs.findOrder(10001, create()).getApprovalGate());
    act(id, true);
    assertEquals("待派工", repairs.findOrder(10001, id).getStatus());
  }

  @Test
  void companySettingsEnforcePermissionAndOptimisticVersion() {
    when(permissions.companyAllowed(eq("workflow"), anyString(), eq(71006L))).thenReturn(false);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> templates.approvalSetting(71006, true, -1));
    enable();
    assertThrows(IllegalArgumentException.class, () -> templates.approvalSetting(71002, false, -1));
    when(auth.tenantId()).thenReturn(99999L);
    assertThrows(IllegalArgumentException.class, () -> approvals.requireActive(990410001));
  }

  @Test
  void repairVariableCatalogSupportsTypedDynamicBranches() {
    var fields = new WorkflowVariables().catalog("repair_report");
    assertTrue(fields.containsKey("severity"));
    assertFalse(fields.containsKey("warehouseId"));
    var expression =
        com.windasoft.eam.common.workflow.WorkflowExpression.parse(
            "severity == \"紧急\" AND departmentId == 71003", fields);
    assertTrue(
        com.windasoft.eam.common.workflow.WorkflowExpression.test(
                expression, Map.of("severity", "紧急", "departmentId", 71003L))
            .matched());
  }

  @Test
  void companiesUseTheirOwnTemplateAndAssignedApproversOnly() {
    var a = publish("repair_report", "ANY", "71002");
    enable();
    long first = create();
    db.update(
        "INSERT INTO eam_sys_user(id,tenant_id,account,display_name,org_id)"
            + " VALUES(990410002,10001,'QA-APPROVAL-B','B公司审批人',71007)");
    var b = publish("repair_report", "ANY", "71006");
    var setting = (Map<?, ?>) templates.approvalSetting(71006);
    templates.approvalSetting(71006, true, ((Number) setting.get("rowVersion")).longValue());
    db.update(
        "UPDATE eam_equipment SET company_org_id=71006,department_org_id=71007 WHERE id=990410001");
    long second = create();
    assertEquals(a.versionId(), instance(first).get("version_id").toString());
    assertEquals(b.versionId(), instance(second).get("version_id").toString());
    var i = instance(first);
    long task =
        db.queryForObject(
            "SELECT id FROM wf_approval WHERE instance_id=? AND user_id=73001",
            Long.class,
            n(i, "id"));
    actor = 990410002L;
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> runtime.act(n(i, "id"), task, n(i, "row_version"), true, "越权测试"));
    act(second, true);
    assertEquals("待派工", repairs.findOrder(10001, second).getStatus());
    assertEquals("审批中", repairs.findOrder(10001, first).getStatus());
  }

  @Test
  void editingAnExemptOrderCannotMoveItIntoACompanyRequiringApproval() {
    long id = create();
    templates.approvalSetting(71006, true, -1);
    db.update(
        "UPDATE eam_equipment SET company_org_id=71006,department_org_id=71007 WHERE id=990410001");
    assertThrows(
        IllegalArgumentException.class,
        () -> orders.update(id, form(), repairs.findOrder(10001, id).getRowVersion()));
    assertEquals(71002L, repairs.findOrder(10001, id).getCompanyOrgId());
  }
}
