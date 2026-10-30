package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.dto.workflow.WorkflowDefinition.*;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.service.inventory.RequisitionService;
import com.windasoft.eam.service.system.*;
import com.windasoft.eam.service.workflow.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_WORKFLOW_TEST_URL", matches = ".+")
@Transactional
class WorkflowMySqlIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    String url = System.getenv("EAM_WORKFLOW_TEST_URL");
    if (url == null
        || !(url.matches("jdbc:mysql://127\\.0\\.0\\.1:3307/eam_staffing_test_20261023(\\?.*)?")
            || url.matches(
                "jdbc:mysql://127\\.0\\.0\\.1:3308/eam_repair_approval_test_20261030(\\?.*)?")))
      throw new IllegalStateException("Only isolated workflow test database allowed");
    p.add("spring.datasource.url", () -> url);
    p.add("spring.datasource.username", () -> "root");
    p.add("spring.datasource.password", () -> "");
    p.add("eam.notification.secret-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean com.windasoft.eam.scheduler.system.NotificationScheduler notificationScheduler;
  @MockitoBean com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler maintenanceScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inspection.InspectionScheduler inspectionScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup cleanup;
  @MockitoBean com.windasoft.eam.scheduler.experience.RepairPlanScheduler repairScheduler;
  @MockitoBean com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler knowledgeScheduler;
  @Autowired WorkflowTemplateService templates;
  @Autowired WorkflowRuntimeService runtime;
  @Autowired RequisitionService requests;
  @Autowired JdbcTemplate db;
  long actor = 73001L, warehouse, material, repair;

  @BeforeEach
  void setup() {
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
              u.setDisplayName("workflow tester");
              return u;
            });
    when(permissions.scope(eq("workflow"), anyString()))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    when(permissions.companyAllowed(eq("workflow"), anyString(), anyLong())).thenReturn(true);
    when(permissions.companyOfOrganization(any()))
        .thenAnswer(
            i -> {
              Long org = i.getArgument(0);
              return org != null && Set.of(71006L, 71007L).contains(org) ? 71006L : 71002L;
            });
    warehouse = IdWorker.getId();
    material = IdWorker.getId();
    repair = IdWorker.getId();
    db.update(
        "INSERT INTO inv_warehouse(id,tenant_id,company_id,code,name) VALUES(?,10001,71002,?,?)",
        warehouse,
        "WF-" + warehouse,
        "workflow warehouse");
    db.update(
        "INSERT INTO inv_material(id,tenant_id,code,name,unit) VALUES(?,10001,?,'workflow"
            + " material','个')",
        material,
        "WX-" + material);
    db.update(
        "INSERT INTO"
            + " eam_repair_order(id,tenant_id,order_no,equipment_code,equipment_name,department_org_id,fault_type,description,reporter,status,assignee_user_id)"
            + " VALUES(?,10001,?,'WF-EQ','workflow"
            + " device',71003,'fault','test','tester','处理中',73001)",
        repair,
        "WF-" + repair);
  }

  WorkflowDefinition definition(String mode) {
    return new WorkflowDefinition(
        null,
        0,
        "workflow test",
        "requisition",
        "71002",
        "",
        null,
        "DRAFT",
        List.of(
            new Node("start", "START", "开始", "approve", "ANY", List.of(), List.of(), List.of()),
            new Node(
                "approve",
                "APPROVAL",
                "主管审批",
                "end",
                mode,
                List.of("73001", "73002"),
                List.of(),
                List.of()),
            new Node("end", "END", "结束", null, "ANY", List.of(), List.of(), List.of())));
  }

  WorkflowDefinition publish(String mode) {
    var d = templates.save(definition(mode));
    return templates.publish(Long.parseLong(d.id()), d.rowVersion());
  }

  long request() {
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " inv_requisition(id,tenant_id,code,company_id,warehouse_id,task_kind,task_id,task_code,applicant_id,applicant_name)"
            + " VALUES(?,10001,?,71002,?,'维修',?,?,73001,'tester')",
        id,
        "WF-" + id,
        warehouse,
        repair,
        "WF-" + repair);
    db.update(
        "INSERT INTO inv_requisition_line(id,tenant_id,requisition_id,line_no,material_id,quantity)"
            + " VALUES(?,10001,?,1,?,12)",
        IdWorker.getId(),
        id,
        material);
    return id;
  }

  Map<String, Object> instance(long id) {
    return db.queryForMap(
        "SELECT * FROM wf_instance WHERE tenant_id=10001 AND business_id=? ORDER BY round_no DESC"
            + " LIMIT 1",
        id);
  }

  long n(Map<String, Object> m, String k) {
    return ((Number) m.get(k)).longValue();
  }

  long pending(long instance, long user) {
    return db.queryForObject(
        "SELECT id FROM wf_approval WHERE instance_id=? AND user_id=? AND state='PENDING'",
        Long.class,
        instance,
        user);
  }

  @Test
  void allApprovalStopsInventoryUntilEveryUserApproves() {
    publish("ALL");
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    var i = instance(id);
    assertEquals(
        "审批中",
        db.queryForObject("SELECT status FROM inv_requisition WHERE id=?", String.class, id));
    assertThrows(
        IllegalArgumentException.class, () -> requests.reserve(id, Map.of("rowVersion", 1)));
    runtime.act(n(i, "id"), pending(n(i, "id"), actor), n(i, "row_version"), true, "同意");
    assertFalse(runtime.approved("requisition", id));
    actor = 73002L;
    i = instance(id);
    runtime.act(n(i, "id"), pending(n(i, "id"), actor), n(i, "row_version"), true, "同意");
    assertTrue(runtime.approved("requisition", id));
    assertEquals(
        "待预留",
        db.queryForObject("SELECT status FROM inv_requisition WHERE id=?", String.class, id));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM wf_todo t JOIN inv_inbox i ON i.id=t.inbox_id JOIN wf_approval a"
                + " ON a.id=t.approval_id WHERE a.instance_id=? AND i.done=0",
            Integer.class,
            n(i, "id")));
  }

  @Test
  void anyApprovalCancelsOtherTasksAndRejectCreatesNewRound() {
    publish("ANY");
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    var i = instance(id);
    runtime.act(n(i, "id"), pending(n(i, "id"), actor), n(i, "row_version"), false, "修改数量");
    assertEquals(
        "草稿", db.queryForObject("SELECT status FROM inv_requisition WHERE id=?", String.class, id));
    long rv =
        db.queryForObject("SELECT row_version FROM inv_requisition WHERE id=?", Long.class, id);
    requests.submit(id, Map.of("rowVersion", rv));
    i = instance(id);
    assertEquals(2, n(i, "round_no"));
    runtime.act(n(i, "id"), pending(n(i, "id"), actor), n(i, "row_version"), true, "");
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM wf_approval WHERE instance_id=? AND state='CANCELLED'",
            Integer.class,
            n(i, "id")));
  }

  @Test
  void withdrawalAndOldVersionStaySeparate() {
    var original = publish("ANY");
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    var replacement = publish("ALL");
    assertNotEquals(original.versionId(), replacement.versionId());
    var i = instance(id);
    assertEquals(original.versionId(), i.get("version_id").toString());
    runtime.withdraw(n(i, "id"), n(i, "row_version"));
    requests.submit(
        id,
        Map.of(
            "rowVersion",
            db.queryForObject(
                "SELECT row_version FROM inv_requisition WHERE id=?", Long.class, id)));
    assertEquals(replacement.versionId(), instance(id).get("version_id").toString());
  }

  @Test
  void tenantAndCompanyPermissionAreEnforced() {
    when(permissions.companyAllowed("workflow", "编辑", 71002L)).thenReturn(false);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> templates.save(definition("ANY")));
  }

  @Test
  void userChoicesIncludeCompanyDepartmentsButNotOtherCompanies() {
    when(permissions.companyDepartments(71002L)).thenReturn(Set.of(71003L, 71004L, 71005L));
    @SuppressWarnings("unchecked")
    var result = (Map<String, Object>) templates.choices(71002L, "users", "", 1, 20);
    @SuppressWarnings("unchecked")
    var rows = (List<Map<String, Object>>) result.get("data");
    assertTrue(rows.stream().anyMatch(r -> r.get("id").toString().equals("73001")));
    assertTrue(rows.stream().anyMatch(r -> r.get("id").toString().equals("73003")));
    var departmentRows = (Map<?, ?>) templates.choices(71002L, "departments", "", 1, 20);
    assertEquals(3L, ((Number) ((Map<?, ?>) departmentRows.get("page")).get("total")).longValue());
  }

  @Test
  void disabledTemplateBlocksRatherThanBypasses() {
    var d = publish("ANY");
    templates.disable(Long.parseLong(d.id()), d.rowVersion());
    long id = request();
    assertThrows(
        IllegalArgumentException.class, () -> requests.submit(id, Map.of("rowVersion", 0)));
  }

  Node advanced(
      String key, String type, String next, List<Branch> branches, String parent, Script script) {
    return new Node(
        key,
        type,
        key,
        next,
        "ANY",
        type.equals("APPROVAL") ? List.of("73001", "73002") : List.of(),
        List.of(),
        branches,
        parent,
        script);
  }

  WorkflowDefinition advancedPublish(List<Node> nodes) {
    var d =
        templates.save(
            new WorkflowDefinition(
                null, 0, "advanced test", "requisition", "71002", "", null, "DRAFT", nodes));
    return templates.publish(Long.parseLong(d.id()), d.rowVersion());
  }

  List<Node> parallelDefinition() {
    return List.of(
        advanced("start", "START", "fork", List.of(), null, null),
        advanced(
            "fork",
            "PARALLEL",
            null,
            List.of(
                new Branch("a", "A分支", "approveA", false, ""),
                new Branch("b", "B分支", "approveB", false, "")),
            null,
            null),
        advanced("approveA", "APPROVAL", "join", List.of(), null, null),
        advanced("approveB", "APPROVAL", "join", List.of(), null, null),
        advanced(
            "join",
            "PARALLEL",
            null,
            List.of(new Branch("next", "汇合", "end", false, "")),
            null,
            null),
        advanced("end", "END", null, List.of(), null, null));
  }

  long taskApproval(long instance, String node, long user) {
    return db.queryForObject(
        "SELECT id FROM wf_approval WHERE instance_id=? AND node_key=? AND user_id=? AND"
            + " state='PENDING'",
        Long.class,
        instance,
        node,
        user);
  }

  @Test
  void parallelBranchesKeepSeparateTasksAndJoinOnlyAfterBothFinish() {
    advancedPublish(parallelDefinition());
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    var i = instance(id);
    assertEquals(
        4,
        db.queryForObject(
            "SELECT COUNT(*) FROM wf_approval WHERE instance_id=? AND state='PENDING'",
            Integer.class,
            n(i, "id")));
    runtime.act(
        n(i, "id"), taskApproval(n(i, "id"), "approveA", actor), n(i, "row_version"), true, "");
    assertFalse(runtime.approved("requisition", id));
    assertEquals(
        2,
        db.queryForObject(
            "SELECT COUNT(*) FROM wf_approval WHERE instance_id=? AND node_key='approveB' AND"
                + " state='PENDING'",
            Integer.class,
            n(i, "id")));
    i = instance(id);
    runtime.act(
        n(i, "id"), taskApproval(n(i, "id"), "approveB", actor), n(i, "row_version"), true, "");
    assertTrue(runtime.approved("requisition", id));
  }

  @Test
  void rejectingOneParallelBranchClosesTheEntireRound() {
    advancedPublish(parallelDefinition());
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    var i = instance(id);
    runtime.act(
        n(i, "id"), taskApproval(n(i, "id"), "approveA", actor), n(i, "row_version"), false, "打回");
    assertEquals("REJECTED", instance(id).get("state"));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM wf_approval WHERE instance_id=? AND state='PENDING'",
            Integer.class,
            n(i, "id")));
  }

  @Test
  void embeddedSubprocessExecutesInnerApprovalBeforeFinishing() {
    var nodes =
        List.of(
            advanced("start", "START", "sub", List.of(), null, null),
            advanced("sub", "SUBPROCESS", "end", List.of(), null, null),
            advanced("innerStart", "START", "innerApproval", List.of(), "sub", null),
            advanced("innerApproval", "APPROVAL", "innerEnd", List.of(), "sub", null),
            advanced("innerEnd", "END", null, List.of(), "sub", null),
            advanced("end", "END", null, List.of(), null, null));
    advancedPublish(nodes);
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    var i = instance(id);
    runtime.act(
        n(i, "id"),
        taskApproval(n(i, "id"), "innerApproval", actor),
        n(i, "row_version"),
        true,
        "");
    assertTrue(runtime.approved("requisition", id));
  }

  @Test
  void inclusiveBranchesRunEveryMatchingPathAndSkipTheFallback() {
    var nodes =
        List.of(
            advanced("start", "START", "fork", List.of(), null, null),
            advanced(
                "fork",
                "INCLUSIVE",
                null,
                List.of(
                    new Branch("a", "A", "approveA", false, "SUM(lines.quantity) > 0"),
                    new Branch("b", "B", "approveB", false, "SUM(lines.quantity) >= 10"),
                    new Branch("c", "其他", "approveC", true, "")),
                null,
                null),
            advanced("approveA", "APPROVAL", "join", List.of(), null, null),
            advanced("approveB", "APPROVAL", "join", List.of(), null, null),
            advanced("approveC", "APPROVAL", "join", List.of(), null, null),
            advanced(
                "join",
                "INCLUSIVE",
                null,
                List.of(new Branch("next", "汇合", "end", false, "")),
                null,
                null),
            advanced("end", "END", null, List.of(), null, null));
    advancedPublish(nodes);
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    var i = instance(id);
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM wf_approval WHERE instance_id=? AND node_key='approveC'",
            Integer.class,
            n(i, "id")));
    runtime.act(
        n(i, "id"), taskApproval(n(i, "id"), "approveA", actor), n(i, "row_version"), true, "");
    assertFalse(runtime.approved("requisition", id));
    i = instance(id);
    runtime.act(
        n(i, "id"), taskApproval(n(i, "id"), "approveB", actor), n(i, "row_version"), true, "");
    assertTrue(runtime.approved("requisition", id));
  }

  @Test
  void realEngineTrialRollsBackTemporaryDeploymentsAndCreatesNoBusinessInstance() {
    long before = db.queryForObject("SELECT COUNT(*) FROM wf_instance", Long.class);
    long deployments = db.queryForObject("SELECT COUNT(*) FROM ACT_RE_DEPLOYMENT", Long.class);
    var base = definition("ANY");
    @SuppressWarnings("unchecked")
    var trace =
        (List<Map<String, Object>>)
            templates.simulate(
                new WorkflowDefinition(
                    null,
                    0,
                    "parallel trial",
                    "requisition",
                    "71002",
                    "",
                    null,
                    "DRAFT",
                    parallelDefinition()),
                Map.of("lines", List.of()));
    assertTrue(trace.stream().anyMatch(row -> "approveA".equals(row.get("node"))));
    assertTrue(trace.stream().anyMatch(row -> "approveB".equals(row.get("node"))));
    assertEquals(before, db.queryForObject("SELECT COUNT(*) FROM wf_instance", Long.class));
    assertEquals(
        deployments, db.queryForObject("SELECT COUNT(*) FROM ACT_RE_DEPLOYMENT", Long.class));
  }

  @Test
  void scriptResultIsAvailableWhenTheLaterGatewayIsReached() {
    var nodes =
        List.of(
            advanced("start", "START", "calculate", List.of(), null, null),
            advanced(
                "calculate",
                "SCRIPT",
                "choice",
                List.of(),
                null,
                new Script(
                    "javascript",
                    "return decimal.sum(input.lines.map(line=>line.quantity));",
                    "computed_quantity",
                    "NUMBER")),
            advanced(
                "choice",
                "BRANCH",
                null,
                List.of(
                    new Branch("large", "较大数量", "approveA", false, "computed_quantity >= 10"),
                    new Branch("other", "其他", "approveB", true, "")),
                null,
                null),
            advanced("approveA", "APPROVAL", "end", List.of(), null, null),
            advanced("approveB", "APPROVAL", "end", List.of(), null, null),
            advanced("end", "END", null, List.of(), null, null));
    advancedPublish(nodes);
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    var i = instance(id);
    assertEquals(
        2,
        db.queryForObject(
            "SELECT COUNT(*) FROM wf_approval WHERE instance_id=? AND node_key='approveA'",
            Integer.class,
            n(i, "id")));
    assertEquals(
        0,
        new java.math.BigDecimal("12")
            .compareTo(
                db.queryForObject(
                    "SELECT number_value FROM wf_value WHERE instance_id=? AND"
                        + " field_key='computed_quantity'",
                    java.math.BigDecimal.class,
                    n(i, "id"))));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM wf_script_run WHERE instance_id=?", Integer.class, n(i, "id")));
  }

  @Test
  void scriptConfigurationPublicationAndTrialHaveSeparateCompanyPermissions() {
    var nodes =
        List.of(
            advanced("start", "START", "calculate", List.of(), null, null),
            advanced(
                "calculate",
                "SCRIPT",
                "approve",
                List.of(),
                null,
                new Script("javascript", "return 'ok';", "computed_result", "TEXT")),
            advanced("approve", "APPROVAL", "end", List.of(), null, null),
            advanced("end", "END", null, List.of(), null, null));
    var definition =
        new WorkflowDefinition(
            null, 0, "script permissions", "requisition", "71002", "", null, "DRAFT", nodes);
    when(permissions.companyAllowed("workflow", "脚本配置", 71002L)).thenReturn(false);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> templates.save(definition));
    when(permissions.companyAllowed("workflow", "脚本配置", 71002L)).thenReturn(true);
    var saved = templates.save(definition);
    when(permissions.companyAllowed("workflow", "脚本发布", 71002L)).thenReturn(false);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> templates.publish(Long.parseLong(saved.id()), saved.rowVersion()));
    when(permissions.companyAllowed("workflow", "脚本试算", 71002L)).thenReturn(false);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> templates.scriptTrial(71002L, nodes.get(1).script(), Map.of()));
  }

  @Test
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void scriptFailureRollsBackApprovalAndFlowProgressTogether() {
    var nodes =
        List.of(
            advanced("start", "START", "approve", List.of(), null, null),
            advanced("approve", "APPROVAL", "calculate", List.of(), null, null),
            advanced(
                "calculate",
                "SCRIPT",
                "end",
                List.of(),
                null,
                new Script(
                    "javascript",
                    "throw new Error('calculation rejected');",
                    "computed_fail",
                    "TEXT")),
            advanced("end", "END", null, List.of(), null, null));
    advancedPublish(nodes);
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    var i = instance(id);
    long approval = pending(n(i, "id"), actor);
    assertThrows(
        RuntimeException.class,
        () -> runtime.act(n(i, "id"), approval, n(i, "row_version"), true, ""));
    assertEquals("RUNNING", instance(id).get("state"));
    assertEquals(
        "PENDING",
        db.queryForObject("SELECT state FROM wf_approval WHERE id=?", String.class, approval));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM wf_script_run WHERE instance_id=?", Integer.class, n(i, "id")));
  }

  @Test
  void diagramArtifactSurvivesVersioningButIsNeverDeployedAsExecutableXml() {
    var base = definition("ANY");
    String diagram =
        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"visual_only\"/>";
    var saved =
        templates.save(
            new WorkflowDefinition(
                null,
                0,
                base.name(),
                base.businessType(),
                base.companyId(),
                base.description(),
                null,
                "DRAFT",
                base.nodes(),
                diagram));
    assertEquals(diagram, saved.diagramXml());
    var published = templates.publish(Long.parseLong(saved.id()), saved.rowVersion());
    assertEquals(
        diagram,
        templates
            .detail(Long.parseLong(saved.id()), Long.parseLong(published.versionId()))
            .diagramXml());
    String deployed =
        db.queryForObject(
            "SELECT bpmn_xml FROM wf_version WHERE id=?",
            String.class,
            Long.parseLong(published.versionId()));
    assertTrue(deployed.contains("userTask"));
    assertFalse(deployed.contains("visual_only"));
  }

  @Test
  void companiesMatchTheirOwnPublishedVersion() {
    var companyA = publish("ANY");
    long requestA = request();
    db.update("UPDATE eam_sys_user SET org_id=71007 WHERE id=73004 AND tenant_id=10001");
    var base = definition("ANY");
    var nodes = new ArrayList<>(base.nodes());
    nodes.set(
        1,
        new Node(
            "approve", "APPROVAL", "B公司审批", "end", "ANY", List.of("73004"), List.of(), List.of()));
    var companyB =
        templates.save(
            new WorkflowDefinition(
                null, 0, "B公司流程", "requisition", "71006", "", null, "DRAFT", nodes));
    companyB = templates.publish(Long.parseLong(companyB.id()), companyB.rowVersion());
    requests.submit(requestA, Map.of("rowVersion", 0));
    assertEquals(companyA.versionId(), instance(requestA).get("version_id").toString());
    long requestB = request();
    db.update("UPDATE inv_requisition SET company_id=71006 WHERE id=?", requestB);
    db.update("UPDATE inv_warehouse SET company_id=71006 WHERE id=?", warehouse);
    db.update("UPDATE eam_repair_order SET department_org_id=71007 WHERE id=?", repair);
    requests.submit(requestB, Map.of("rowVersion", 0));
    var i = instance(requestB);
    assertEquals(companyB.versionId(), i.get("version_id").toString());
    assertEquals(
        List.of(73004L),
        db.queryForList(
            "SELECT user_id FROM wf_approval WHERE instance_id=?", Long.class, n(i, "id")));
  }

  @Test
  void roleCandidatesAreCompanyScopedDeduplicatedAndSnapshotted() {
    long role = IdWorker.getId();
    db.update(
        "INSERT INTO eam_sys_role(id,tenant_id,code,name) VALUES(?,10001,?,'流程测试角色')",
        role,
        "WF-" + role);
    for (long user : List.of(73001L, 73002L, 73004L))
      db.update(
          "INSERT INTO eam_sys_user_role(tenant_id,user_id,role_id) VALUES(10001,?,?)", user, role);
    db.update("UPDATE eam_sys_user SET org_id=71007 WHERE id=73004 AND tenant_id=10001");
    var base = definition("ALL");
    var nodes = new ArrayList<>(base.nodes());
    nodes.set(
        1,
        new Node(
            "approve",
            "APPROVAL",
            "角色审批",
            "end",
            "ALL",
            List.of("73001"),
            List.of(Long.toString(role)),
            List.of()));
    var d =
        templates.save(
            new WorkflowDefinition(
                null, 0, "角色流程", "requisition", "71002", "", null, "DRAFT", nodes));
    templates.publish(Long.parseLong(d.id()), d.rowVersion());
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    var i = instance(id);
    assertEquals(
        List.of(73001L, 73002L),
        db.queryForList(
            "SELECT user_id FROM wf_approval WHERE instance_id=? ORDER BY user_id",
            Long.class,
            n(i, "id")));
    db.update("DELETE FROM eam_sys_user_role WHERE tenant_id=10001 AND role_id=?", role);
    assertEquals(
        2,
        db.queryForObject(
            "SELECT COUNT(*) FROM wf_approval WHERE instance_id=? AND state='PENDING'",
            Integer.class,
            n(i, "id")));
  }

  @Test
  void allRequisitionIssueEntrypointsRejectUnapprovedRequests() {
    publish("ANY");
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    Map<String, Object> body =
        Map.of(
            "rowVersion",
            1,
            "selections",
            List.of(),
            "confirmationKey",
            UUID.randomUUID().toString(),
            "ids",
            List.of(Long.toString(id)));
    var before =
        db.queryForObject("SELECT COUNT(*) FROM inv_issue WHERE tenant_id=10001", Long.class);
    for (org.junit.jupiter.api.function.Executable action :
        List.<org.junit.jupiter.api.function.Executable>of(
            () -> requests.reserve(id, body),
            () -> requests.previewIssue(id, body),
            () -> requests.confirmIssue(id, body),
            () -> requests.mergedPreview(body),
            () -> requests.mergedConfirm(body))) {
      var error = assertThrows(IllegalArgumentException.class, action);
      assertEquals("领料申请尚未审批通过", error.getMessage());
    }
    assertEquals(
        before,
        db.queryForObject("SELECT COUNT(*) FROM inv_issue WHERE tenant_id=10001", Long.class));
  }

  @Test
  void anotherTenantCannotReadOrOperateAnInstance() {
    var template = publish("ANY");
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    var i = instance(id);
    long approval = pending(n(i, "id"), actor);
    when(auth.tenantId()).thenReturn(10002L);
    assertThrows(
        IllegalArgumentException.class,
        () -> templates.detail(Long.parseLong(template.id()), null));
    assertThrows(
        IllegalArgumentException.class,
        () -> runtime.act(n(i, "id"), approval, n(i, "row_version"), true, ""));
    assertFalse(runtime.participant("requisition", id));
  }

  @Test
  void endedTaskCannotBeApproved() {
    publish("ANY");
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    var i = instance(id);
    db.update("UPDATE eam_repair_order SET status='已完成' WHERE id=?", repair);
    assertThrows(
        IllegalArgumentException.class,
        () -> runtime.act(n(i, "id"), pending(n(i, "id"), actor), n(i, "row_version"), true, ""));
  }

  @Test
  void relationalConditionsSelectFirstMatchingBranch() {
    var d = definition("ANY");
    var nodes = new ArrayList<Node>();
    nodes.add(new Node("start", "START", "开始", "choose", "ANY", List.of(), List.of(), List.of()));
    nodes.add(
        new Node(
            "choose",
            "BRANCH",
            "数量判断",
            null,
            "ANY",
            List.of(),
            List.of(),
            List.of(
                new Branch(
                    "large",
                    "数量达到10",
                    "approve",
                    false,
                    "SUM(lines.quantity) >= 10 AND ANY(lines, lines.code STARTS_WITH \"WX-\")"),
                new Branch("other", "其他情况", "second", true, ""))));
    nodes.add(d.nodes().get(1));
    nodes.add(
        new Node(
            "second", "APPROVAL", "其他审批", "end", "ANY", List.of("73002"), List.of(), List.of()));
    nodes.add(d.nodes().get(2));
    d =
        templates.save(
            new WorkflowDefinition(
                null, 0, "branch test", "requisition", "71002", "", null, "DRAFT", nodes));
    templates.publish(Long.parseLong(d.id()), d.rowVersion());
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    var i = instance(id);
    assertEquals(
        "approve",
        db.queryForObject(
            "SELECT node_key FROM wf_approval WHERE instance_id=? LIMIT 1",
            String.class,
            n(i, "id")));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM wf_event WHERE instance_id=? AND event_type='BRANCH'",
            Integer.class,
            n(i, "id")));
  }

  @Test
  void actorCannotHandleAnotherUsersTask() {
    publish("ALL");
    long id = request();
    requests.submit(id, Map.of("rowVersion", 0));
    var i = instance(id);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> runtime.act(n(i, "id"), pending(n(i, "id"), 73002L), n(i, "row_version"), true, ""));
  }

  @Test
  void staleVersionAndUnavailableAmountAreRejected() {
    var d = templates.save(definition("ANY"));
    templates.save(d);
    assertThrows(
        IllegalArgumentException.class,
        () -> templates.publish(Long.parseLong(d.id()), d.rowVersion()));
    assertThrows(IllegalArgumentException.class, () -> templates.parse("amount > 100"));
  }

  @Test
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void concurrentSubmissionAndApprovalVersusWithdrawal() throws Exception {
    publish("ANY");
    long id = request();
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var barrier = new java.util.concurrent.CyclicBarrier(2);
      var one =
          pool.submit(
              () -> {
                barrier.await();
                return requests.submit(id, Map.of("rowVersion", 0));
              });
      var two =
          pool.submit(
              () -> {
                barrier.await();
                return requests.submit(id, Map.of("rowVersion", 0));
              });
      one.get(30, java.util.concurrent.TimeUnit.SECONDS);
      two.get(30, java.util.concurrent.TimeUnit.SECONDS);
      assertEquals(
          1,
          db.queryForObject(
              "SELECT COUNT(*) FROM wf_instance WHERE business_id=?", Integer.class, id));
      var i = instance(id);
      long approval = pending(n(i, "id"), actor);
      var actionBarrier = new java.util.concurrent.CyclicBarrier(2);
      var a =
          pool.submit(
              () -> {
                actionBarrier.await();
                try {
                  runtime.act(n(i, "id"), approval, n(i, "row_version"), true, "");
                  return 1;
                } catch (IllegalArgumentException e) {
                  return 0;
                }
              });
      var b =
          pool.submit(
              () -> {
                actionBarrier.await();
                try {
                  runtime.withdraw(n(i, "id"), n(i, "row_version"));
                  return 1;
                } catch (IllegalArgumentException e) {
                  return 0;
                }
              });
      assertEquals(
          1,
          a.get(30, java.util.concurrent.TimeUnit.SECONDS)
              + b.get(30, java.util.concurrent.TimeUnit.SECONDS));
      assertTrue(Set.of("APPROVED", "WITHDRAWN").contains(instance(id).get("state")));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM wf_approval WHERE instance_id=? AND state='PENDING'",
              Integer.class,
              n(i, "id")));
    } finally {
      pool.shutdownNow();
    }
  }
}
