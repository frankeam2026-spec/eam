package com.windasoft.eam.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.equipment.Equipment;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PermissionServiceTest {
    private AuthContext auth;
    private UserRoleLinkMapper links;
    private SystemRoleMapper roles;
    private RoleMenuPermissionMapper grants;
    private PermissionService service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "permission-test");
        TableInfoHelper.initTableInfo(assistant, SystemRole.class);
        TableInfoHelper.initTableInfo(assistant, RoleMenuPermission.class);
        TableInfoHelper.initTableInfo(assistant, OrgUnit.class);
        auth = mock(AuthContext.class);
        links = mock(UserRoleLinkMapper.class);
        roles = mock(SystemRoleMapper.class);
        grants = mock(RoleMenuPermissionMapper.class);
        OrgUnitMapper orgs = mock(OrgUnitMapper.class);
        SystemUser user = new SystemUser();
        user.setId(10L);
        user.setTenantId(10001L);
        user.setOrgId(30L);
        when(auth.user()).thenReturn(user);
        when(auth.userId()).thenReturn(10L);
        when(auth.tenantId()).thenReturn(10001L);
        when(orgs.selectList(any())).thenReturn(List.of());
        service = new PermissionService(auth, links, roles, grants, orgs,
            mock(SystemUserMapper.class), new ObjectMapper());
    }

    private void assignTwoRoles() {
        UserRoleLink first = new UserRoleLink(); first.setRoleId(1L);
        UserRoleLink second = new UserRoleLink(); second.setRoleId(2L);
        when(links.selectList(any())).thenReturn(List.of(first, second));
        SystemRole firstRole = new SystemRole(); firstRole.setId(1L); firstRole.setDataScope("PER_MENU");
        SystemRole secondRole = new SystemRole(); secondRole.setId(2L); secondRole.setDataScope("PER_MENU");
        when(roles.selectList(any())).thenReturn(List.of(firstRole, secondRole));
    }

    private RoleMenuPermission grant(String actions, String scopes, String departments) {
        RoleMenuPermission grant = new RoleMenuPermission();
        grant.setActionsJson(actions); grant.setDataScopesJson(scopes); grant.setOrgIdsJson(departments);
        return grant;
    }

    @Test
    void missingRolesDenyAccess() {
        when(links.selectList(any())).thenReturn(List.of());
        assertEquals(403, assertThrows(ResponseStatusException.class,
            () -> service.require("equipment", "查看")).getStatusCode().value());
    }

    @Test
    void viewAllDoesNotGrantDeleteAll() {
        assignTwoRoles();
        when(grants.selectList(any())).thenReturn(
            List.of(grant("[\"查看\"]", "[\"all\"]", "[]")),
            List.of(grant("[\"删除\"]", "[\"department\"]", "[]")));
        PermissionService.Scope scope = service.equipment("删除");
        assertFalse(scope.all());
        assertTrue(scope.matches(null, 30L, null, 10L));
        assertFalse(scope.matches(null, 99L, null, 10L));
    }

    @Test
    void matchingRoleScopesAreCombined() {
        assignTwoRoles();
        when(grants.selectList(any())).thenReturn(
            List.of(grant("[\"查看\"]", "[\"department\"]", "[]")),
            List.of(grant("[\"查看\"]", "[\"selected\",\"owner\"]", "[40]")));
        PermissionService.Scope scope = service.equipment("查看");
        assertTrue(scope.matches(null, 30L, null, 10L));
        assertTrue(scope.matches(null, 40L, null, 10L));
        assertTrue(scope.matches(null, 99L, 10L, 10L));
        assertFalse(scope.matches(null, 99L, 11L, 10L));
    }

    @Test
    void allScopeStillRejectsOtherTenant() {
        assignTwoRoles();
        when(grants.selectList(any())).thenReturn(List.of(grant("[\"查看\"]", "[\"all\"]", "[]")));
        Equipment equipment = new Equipment(); equipment.setTenantId(20002L);
        assertEquals(403, assertThrows(ResponseStatusException.class,
            () -> service.checkEquipment(equipment, "查看")).getStatusCode().value());
    }
}
