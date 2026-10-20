package com.windasoft.eam.service.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.entity.system.RoleMenuPermission;
import com.windasoft.eam.entity.system.SystemRole;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.entity.system.UserRoleLink;
import com.windasoft.eam.repository.system.OrgUnitMapper;
import com.windasoft.eam.repository.system.RoleMenuPermissionMapper;
import com.windasoft.eam.repository.system.SystemRoleMapper;
import com.windasoft.eam.repository.system.SystemUserMapper;
import com.windasoft.eam.repository.system.UserRoleLinkMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PermissionServiceTest {
  private AuthContext auth;
  private UserRoleLinkMapper links;
  private SystemRoleMapper roles;
  private RoleMenuPermissionMapper grants;
  private PermissionService service;
  private OrgUnitMapper orgs;

  @BeforeEach
  void setUp() {
    MapperBuilderAssistant assistant =
        new MapperBuilderAssistant(new MybatisConfiguration(), "permission-test");
    TableInfoHelper.initTableInfo(assistant, SystemRole.class);
    TableInfoHelper.initTableInfo(assistant, RoleMenuPermission.class);
    TableInfoHelper.initTableInfo(assistant, OrgUnit.class);
    auth = mock(AuthContext.class);
    links = mock(UserRoleLinkMapper.class);
    roles = mock(SystemRoleMapper.class);
    grants = mock(RoleMenuPermissionMapper.class);
    orgs = mock(OrgUnitMapper.class);
    SystemUser user = new SystemUser();
    user.setId(10L);
    user.setTenantId(10001L);
    user.setOrgId(30L);
    when(auth.user()).thenReturn(user);
    when(auth.userId()).thenReturn(10L);
    when(auth.tenantId()).thenReturn(10001L);
    when(orgs.selectList(any())).thenReturn(List.of());
    var repository =
        new com.windasoft.eam.repository.system.AuthorizationRepository(
            links, roles, grants, orgs, mock(SystemUserMapper.class), null, null, com.windasoft.eam.support.RelationStubs.roles());
    service =
        new PermissionService(
            auth,
            repository,
            new ObjectMapper(),
            new com.windasoft.eam.service.system.OrganizationScopeService(auth, repository));
  }

  private void assignTwoRoles() {
    UserRoleLink first = new UserRoleLink();
    first.setRoleId(1L);
    UserRoleLink second = new UserRoleLink();
    second.setRoleId(2L);
    when(links.selectList(any())).thenReturn(List.of(first, second));
    SystemRole firstRole = new SystemRole();
    firstRole.setId(1L);
    firstRole.setDataScope("PER_MENU");
    SystemRole secondRole = new SystemRole();
    secondRole.setId(2L);
    secondRole.setDataScope("PER_MENU");
    when(roles.selectList(any())).thenReturn(List.of(firstRole, secondRole));
  }

  private RoleMenuPermission grant(String actions, String scopes, String departments) {
    RoleMenuPermission grant = new RoleMenuPermission();
    grant.setActionsJson(actions);
    grant.setDataScopesJson(scopes);
    grant.setOrgIdsJson(departments);
    return grant;
  }

  @Test
  void missingRolesDenyAccess() {
    when(links.selectList(any())).thenReturn(List.of());
    assertEquals(
        403,
        assertThrows(ResponseStatusException.class, () -> service.require("equipment", "查看"))
            .getStatusCode()
            .value());
  }

  @Test
  void viewAllDoesNotGrantDeleteAll() {
    assignTwoRoles();
    when(grants.selectList(any()))
        .thenReturn(
            List.of(grant("[\"查看\"]", "[\"all\"]", "[]")),
            List.of(grant("[\"删除\"]", "[\"department\"]", "[]")));
    com.windasoft.eam.dto.system.PermissionScope scope = service.equipment("删除");
    assertFalse(scope.all());
    assertTrue(scope.matches(null, 30L, null, 10L));
    assertFalse(scope.matches(null, 99L, null, 10L));
  }

  @Test
  void matchingRoleScopesAreCombined() {
    assignTwoRoles();
    when(grants.selectList(any()))
        .thenReturn(
            List.of(grant("[\"查看\"]", "[\"department\"]", "[]")),
            List.of(grant("[\"查看\"]", "[\"selected\",\"owner\"]", "[40]")));
    com.windasoft.eam.dto.system.PermissionScope scope = service.equipment("查看");
    assertTrue(scope.matches(null, 30L, null, 10L));
    assertTrue(scope.matches(null, 40L, null, 10L));
    assertTrue(scope.matches(null, 99L, 10L, 10L));
    assertFalse(scope.matches(null, 99L, 11L, 10L));
  }

  @Test
  void allScopeStillRejectsOtherTenant() {
    assignTwoRoles();
    when(grants.selectList(any())).thenReturn(List.of(grant("[\"查看\"]", "[\"all\"]", "[]")));
    Equipment equipment = new Equipment();
    equipment.setTenantId(20002L);
    assertEquals(
        403,
        assertThrows(ResponseStatusException.class, () -> service.checkEquipment(equipment, "查看"))
            .getStatusCode()
            .value());
  }

  private OrgUnit org(long id, Long parent, String type) {
    var row = new OrgUnit();
    row.setId(id);
    row.setParentId(parent);
    row.setTenantId(10001L);
    row.setOrgType(type);
    row.setStatus(1);
    return row;
  }

  private void hierarchy() {
    var rows =
        List.of(
            org(1, null, "公司"),
            org(2, 1L, "公司"),
            org(3, 2L, "公司"),
            org(4, 1L, "公司"),
            org(20, 1L, "部门"),
            org(30, 2L, "部门"),
            org(31, 30L, "部门"),
            org(32, 3L, "部门"),
            org(40, 4L, "部门"));
    when(orgs.selectList(any())).thenReturn(rows);
    when(orgs.selectOne(any()))
        .thenAnswer(
            call -> {
              var q =
                  (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>)
                      call.getArgument(0);
              q.getSqlSegment();
              var values = q.getParamNameValuePairs().values();
              return rows.stream().filter(o -> values.contains(o.getId())).findFirst().orElse(null);
            });
  }

  private com.windasoft.eam.dto.system.PermissionScope oneScope(String name, String ids) {
    assignTwoRoles();
    when(grants.selectList(any()))
        .thenReturn(List.of(grant("[\"查看\"]", "[\"" + name + "\"]", ids)));
    return service.equipment("查看");
  }

  @Test
  void ownCompanyExcludesSubsidiaries() {
    hierarchy();
    var s = oneScope("company", "[]");
    assertTrue(s.matches(2L, 30L, null, 10L));
    assertTrue(s.matches(2L, 31L, null, 10L));
    assertFalse(s.matches(3L, 32L, null, 10L));
    assertFalse(s.matches(1L, 20L, null, 10L));
    assertEquals(java.util.Set.of(2L), s.companyIds());
  }

  @Test
  void companyTreeIncludesOnlyDescendantCompanies() {
    hierarchy();
    var s = oneScope("companyTree", "[]");
    assertTrue(s.matches(3L, 32L, null, 10L));
    assertFalse(s.matches(4L, 40L, null, 10L));
    assertEquals(java.util.Set.of(2L, 3L), s.companyIds());
  }

  @Test
  void selectedCompaniesDoNotIncludeTheirChildren() {
    hierarchy();
    var s = oneScope("companies", "[1,4]");
    assertTrue(s.matches(1L, 20L, null, 10L));
    assertTrue(s.matches(4L, 40L, null, 10L));
    assertFalse(s.matches(2L, 30L, null, 10L));
  }

  @Test
  void departmentTreeDoesNotCrossCompanyBoundary() {
    hierarchy();
    var s = oneScope("departmentTree", "[]");
    assertTrue(s.matches(2L, 31L, null, 10L));
    assertFalse(s.matches(3L, 32L, null, 10L));
    assertTrue(s.companyIds().isEmpty());
  }

  @Test
  void groupDisplayCompanyIsARealBusinessOwner() {
    hierarchy();
    auth.user().setOrgId(20L);
    var s = oneScope("company", "[]");
    assertEquals(1L, service.companyOfUser());
    assertTrue(s.matches(1L, 20L, null, 10L));
    assertFalse(s.matches(2L, 30L, null, 10L));
  }

  @Test
  void directCompanyUserGetsOwnCompanyScope() {
    hierarchy();
    auth.user().setOrgId(2L);
    var s = oneScope("company", "[]");
    assertTrue(s.departmentIds().contains(2L));
    assertTrue(s.matches(2L, null, null, 10L));
  }

  @Test
  void broadReadDoesNotEnlargeNarrowWriteAcrossCompanyTree() {
    hierarchy();
    assignTwoRoles();
    when(grants.selectList(any()))
        .thenReturn(
            List.of(grant("[\"查看\"]", "[\"companyTree\"]", "[]")),
            List.of(grant("[\"编辑\"]", "[\"company\"]", "[]")));
    var s = service.equipment("编辑");
    assertTrue(s.matches(2L, 30L, null, 10L));
    assertFalse(s.matches(3L, 32L, null, 10L));
  }
}
