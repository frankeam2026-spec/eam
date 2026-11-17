package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.OrganizationScopeService;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.service.system.UserListService;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** 仅写本专项隔离库；真实组织解析用于发现逐组织查询，不用Mock掩盖SQL增长。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class SystemUserQueryMySqlIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    String url = System.getenv("EAM_SYSTEM_TEST_URL");
    if (url == null
        || !url.startsWith("jdbc:mysql://127.0.0.1:3308/eam_system_quality_test_20261117_v2?")) {
      throw new IllegalStateException("仅允许本专项3308隔离库");
    }
    p.add("spring.datasource.url", () -> url);
    p.add("spring.datasource.username", () -> "root");
    p.add("spring.datasource.password", () -> "");
    p.add("eam.notification.secret-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean NotificationScheduler notificationScheduler;
  @MockitoBean MaintenanceScheduler maintenanceScheduler;
  @MockitoBean InspectionScheduler inspectionScheduler;
  @MockitoBean InventoryReservationCleanup cleanup;
  @MockitoBean RepairPlanScheduler repairScheduler;
  @MockitoBean KnowledgeScheduler knowledgeScheduler;
  @Autowired UserListService users;
  @Autowired OrganizationScopeService scopes;
  @Autowired JdbcTemplate db;
  @Autowired SqlSessionTemplate sessions;

  @BeforeEach
  void setup() {
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(73001L);
    when(permissions.scope(eq("user"), anyString()))
        .thenReturn(new PermissionScope(false, 71002L, Set.of(), false));
    when(permissions.companyOfOrganization(any()))
        .thenAnswer(i -> scopes.companyOfOrganization((Long) i.getArgument(0)));
    when(permissions.allowed("role", "查看")).thenReturn(true);
  }

  @Test
  void exportAndListUseTheSameCompanyAndSelectionIntersection() {
    var query = ListQuery.from(Map.of("size", "100", "sortBy", "account", "direction", "asc"));
    assertEquals(users.query(query).data(), users.export(query));
    var foreign = ListQuery.from(Map.of("organization", "71006", "size", "100"));
    assertTrue(users.query(foreign).data().isEmpty());
    assertTrue(users.export(foreign).isEmpty());
    when(auth.tenantId()).thenReturn(10002L);
    assertTrue(users.query(query).data().isEmpty());
  }

  @Test
  void hiddenRoleNamesStayHiddenWithoutChangingTheExistingIdsContract() {
    when(permissions.allowed("role", "查看")).thenReturn(false);
    var rows = users.query(ListQuery.from(Map.of("size", "100"))).data();
    assertFalse(rows.isEmpty());
    assertTrue(rows.stream().allMatch(r -> r.roles().isEmpty()));
    assertTrue(rows.stream().noneMatch(r -> r.id() == null || r.id().isEmpty()));
  }

  @Test
  void explicitExportScopeCanOnlyReduceViewScope() {
    when(permissions.scope("user", "导出"))
        .thenReturn(new PermissionScope(false, null, Set.of(71007L), false));
    assertFalse(users.query(ListQuery.from(Map.of("size", "100"))).data().isEmpty());
    assertTrue(users.export(ListQuery.from(Map.of("size", "100"))).isEmpty());
  }

  @Test
  void nameAliasAndEqualNamesHaveStableCrossPageOrder() {
    long first = IdWorker.getId(), second = IdWorker.getId(), third = IdWorker.getId();
    String prefix = "sortcheck-" + first;
    for (long id : new long[] {first, second, third}) {
      db.update(
          "INSERT INTO eam_sys_user(id,tenant_id,account,display_name,org_id)"
              + " VALUES(?,10001,?,?,71003)",
          id,
          prefix + "-" + id,
          id == third ? "B测试" : "A测试");
    }
    var query =
        ListQuery.from(Map.of("q", prefix, "sortBy", "name", "direction", "asc", "size", "2"));
    assertEquals(
        List.of(Long.toString(first), Long.toString(second)),
        users.query(query).data().stream().map(r -> r.id()).toList());
    assertEquals(
        List.of(Long.toString(third)),
        users.query(query.at(2, 2)).data().stream().map(r -> r.id()).toList());
    assertEquals(
        List.of(Long.toString(first), Long.toString(second), Long.toString(third)),
        users.export(query).stream().map(r -> r.id()).toList());
  }

  @Test
  void authorizationQueriesDoNotGrowPerOrganization() {
    long before = queries();
    for (int i = 0; i < 40; i++) {
      long id = IdWorker.getId();
      db.update(
          "INSERT INTO eam_org_unit(id,tenant_id,parent_id,org_type,name,code,status)"
              + " VALUES(?,10001,71002,'部门',? ,?,1)",
          id,
          "治理测试" + i,
          "SYS-Q-" + id);
    }
    long after = queries();
    System.out.println(
        "system-user-query Com_select original:" + before + ", extra40orgs:" + after);
    assertEquals(before, after, "用户列表授权不能逐组织读取数据库");
  }

  private long queries() {
    sessions.clearCache();
    long before = selects();
    var page = users.query(ListQuery.from(Map.of("size", "20")));
    assertTrue(page.data().stream().anyMatch(r -> "73001".equals(r.id())));
    assertTrue(page.data().stream().noneMatch(r -> "71007".equals(r.orgId())));
    return selects() - before;
  }

  private long selects() {
    return db.queryForObject(
        "SHOW SESSION STATUS LIKE 'Com_select'", (r, n) -> Long.parseLong(r.getString(2)));
  }
}
