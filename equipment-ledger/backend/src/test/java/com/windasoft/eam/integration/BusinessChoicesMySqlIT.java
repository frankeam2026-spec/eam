package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.business.BusinessConfigService;
import com.windasoft.eam.service.business.BusinessReferenceService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.OrganizationScopeService;
import com.windasoft.eam.service.system.PermissionService;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** 运行时选择器只使用本专项隔离库，验证协议、来源过滤与真实授权查询次数。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class BusinessChoicesMySqlIT {
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

  @Autowired BusinessConfigService configuration;

  @Autowired SqlSessionTemplate sqlSessions;
  @Autowired BusinessReferenceService references;
  @Autowired OrganizationScopeService scopes;

  private long tenant;

  @BeforeEach
  void identity() {
    tenant = IdWorker.getId();
    when(auth.tenantId()).thenReturn(tenant);
    when(auth.userId()).thenReturn(73001L);
    var user = new SystemUser();
    when(auth.user()).thenReturn(user);
    when(permissions.scope(anyString(), anyString()))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    when(permissions.companyOfOrganization(any()))
        .thenAnswer(i -> scopes.companyOfOrganization(i.getArgument(0)));
  }

  private JsonNode choices(String source, String keyword, int page, int size, boolean inactive) {
    return json.valueToTree(references.choices("equipment", source, keyword, page, size, inactive));
  }

  @Test
  void databaseChoicesKeepNumericFlagsLiteralSearchAndLexicalIdTies() {
    long nonce = IdWorker.getId() % 100000000000L + 100000000000L,
        small = nonce * 2,
        big = nonce * 10 + 1;
    for (long id : new long[] {small, big})
      db.update(
          "INSERT INTO eam_base_item(id,tenant_id,item_type,code,name,status,sort_no)"
              + " VALUES(?,?,'UNIT',?,'测试%_!单位',1,0)",
          id, tenant, "U-" + id);
    var first = choices("base:UNIT", "%_!", 1, 1, false);
    assertEquals(2, first.path("total").asInt());
    assertEquals("" + big, first.path("data").get(0).path("id").asText());
    assertTrue(first.path("data").get(0).path("enabled").isIntegralNumber());
    assertEquals(
        "" + small,
        choices("base:UNIT", "%_!", 2, 1, false).path("data").get(0).path("id").asText());
    assertThrows(IllegalArgumentException.class, () -> choices("base:UNIT", "", 0, 20, false));
    assertThrows(IllegalArgumentException.class, () -> choices("base:UNIT", "", 1, 101, false));
  }

  @Test
  void inlineChoicesKeepBooleanFlagsAndDisabledFiltering() {
    String field = UUID.randomUUID().toString(),
        first = UUID.randomUUID().toString(),
        second = UUID.randomUUID().toString();
    var config = json.createObjectNode();
    var f =
        config
            .putArray("fields")
            .addObject()
            .put("id", field)
            .put("name", "选项")
            .put("type", "CHOICE")
            .put("enabled", true)
            .put("group", "扩展信息");
    var options = f.putArray("options");
    options.addObject().put("id", first).put("name", "A%").put("enabled", true).put("sortOrder", 2);
    options
        .addObject()
        .put("id", second)
        .put("name", "B%")
        .put("enabled", false)
        .put("sortOrder", 1);
    db.update(
        "INSERT INTO eam_business_schema(tenant_id,object_key,draft,published)"
            + " VALUES(?,'equipment',CAST(? AS JSON),CAST(? AS JSON))",
        tenant,
        config.toString(),
        config.toString());
    var active = choices("field:" + field, "%", 1, 20, false);
    assertEquals(1, active.path("total").asInt());
    assertTrue(active.path("data").get(0).path("enabled").isBoolean());
    assertEquals(first, active.path("data").get(0).path("id").asText());
    assertEquals(
        second,
        choices("field:" + field, "%", 1, 20, true).path("data").get(0).path("id").asText());
  }

  @Test
  void legacyDictionaryHonorsParentEnabledAndTenant() {
    String dict = UUID.randomUUID().toString(), option = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO eam_business_dictionary(id,tenant_id,name,enabled) VALUES(?,?,'旧字典',0)",
        dict,
        tenant);
    db.update(
        "INSERT INTO eam_business_option(id,tenant_id,dictionary_id,name,enabled,sort_no)"
            + " VALUES(?,?,?,'旧选项',1,0)",
        option,
        tenant,
        dict);
    assertEquals(0, choices("dictionary:" + dict, "", 1, 20, false).path("total").asInt());
    var inactive = choices("dictionary:" + dict, "", 1, 20, true);
    assertEquals(1, inactive.path("total").asInt());
    assertTrue(inactive.path("data").get(0).path("enabled").isIntegralNumber());
    assertThrows(
        IllegalArgumentException.class,
        () -> references.resolve("equipment", "dictionary:" + dict, option));
    var config = json.createObjectNode();
    config
        .putArray("fields")
        .addObject()
        .put("id", UUID.randomUUID().toString())
        .put("type", "CHOICE")
        .put("source", "dictionary:" + dict)
        .put("group", "扩展信息");
    db.update(
        "INSERT INTO eam_business_schema(tenant_id,object_key,draft,published)"
            + " VALUES(?,'equipment',CAST(? AS JSON),CAST(? AS JSON))",
        tenant,
        config.toString(),
        config.toString());
    assertEquals(
        false,
        configuration
            .configuration("equipment")
            .draft()
            .path("fields")
            .get(0)
            .path("options")
            .get(0)
            .path("enabled")
            .asBoolean());
    when(auth.tenantId()).thenReturn(tenant + 1);
    assertEquals(0, choices("dictionary:" + dict, "", 1, 20, true).path("total").asInt());
  }

  private long org(String name, String type, Long parent, int status) {
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO eam_org_unit(id,tenant_id,name,code,org_type,parent_id,status)"
            + " VALUES(?,?,?,?,?,?,?)",
        id,
        tenant,
        name,
        "C-" + id,
        type,
        parent,
        status);
    return id;
  }

  private long selects() {
    return db.queryForObject(
        "SHOW SESSION STATUS LIKE 'Com_select'", (r, n) -> Long.parseLong(r.getString(2)));
  }

  @Test
  void departmentScopeResolvesCompaniesInBoundedQueries() {
    long root = org("集团", "公司", null, 1),
        company = org("公司A", "公司", root, 0),
        department = org("部门A", "部门", company, 1);
    for (int i = 0; i < 40; i++) org("部门" + i, "部门", company, 1);
    when(permissions.scope(anyString(), anyString()))
        .thenReturn(new PermissionScope(false, company, Set.of(), false));
    sqlSessions.clearCache();
    long before = selects();
    var page = choices("department", "部门A", 1, 20, false);
    long count = selects() - before;
    assertEquals(1, page.path("total").asInt());
    assertEquals("" + department, page.path("data").get(0).path("id").asText());
    assertEquals("部门A", page.path("data").get(0).path("name").asText());
    System.out.println("business-choice Com_select extra40orgs=" + count);
    assertTrue(count <= 4, "部门授权应复用组织快照，不逐组织查询");
  }

  @Test
  void userChoicesIntersectOrganizationScopeAndPreserveOwnerFallback() {
    long root = org("集团", "公司", null, 1),
        a = org("公司A", "公司", root, 1),
        b = org("公司B", "公司", root, 1),
        deptA = org("部门A", "部门", a, 1),
        deptB = org("部门B", "部门", b, 1);
    long visible = IdWorker.getId(), own = IdWorker.getId(), hidden = IdWorker.getId();
    for (long id : new long[] {visible, own, hidden})
      db.update(
          "INSERT INTO eam_sys_user(id,tenant_id,account,display_name,org_id,status)"
              + " VALUES(?,?,?,'候选用户',?,1)",
          id,
          tenant,
          "ref-" + id,
          id == visible ? deptA : deptB);
    when(auth.userId()).thenReturn(own);
    auth.user().setOrgId(deptB);
    when(permissions.scope(anyString(), anyString()))
        .thenReturn(new PermissionScope(false, a, Set.of(), true));
    var page = choices("user", "候选", 1, 20, false);
    var ids = new HashSet<String>();
    page.path("data").forEach(row -> ids.add(row.path("id").asText()));
    assertEquals(Set.of("" + visible, "" + own), ids);
    assertEquals(2, page.path("total").asInt());
    assertEquals(
        Set.of("" + visible, "" + own),
        references.all("equipment", "user", false).stream()
            .map(o -> o.id())
            .collect(Collectors.toSet()));
    when(auth.tenantId()).thenReturn(tenant + 1);
    assertEquals(0, choices("user", "候选", 1, 20, false).path("total").asInt());
  }
}
