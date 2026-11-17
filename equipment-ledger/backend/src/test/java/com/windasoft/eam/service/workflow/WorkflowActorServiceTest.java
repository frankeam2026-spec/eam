package com.windasoft.eam.service.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.dto.workflow.WorkflowDefinition.Node;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.system.RoleManagementRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import com.windasoft.eam.service.system.PermissionService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowActorServiceTest {
  @Test
  void fixedUsersAreBatchedAndRoleMembershipIsLimitedBeforeReading() {
    var users = mock(SystemUserRepository.class);
    var permissions = mock(PermissionService.class);
    var actor = new WorkflowActorService(users, mock(RoleManagementRepository.class), permissions);
    var first = user(1L, 11L);
    var second = user(2L, 11L);
    when(users.activeIdentities(7L, List.of(1L, 2L))).thenReturn(List.of(first, second));
    when(permissions.companyOfOrganization(11L)).thenReturn(10L);
    when(permissions.companyOfOrganization(10L)).thenReturn(10L);
    when(permissions.companyOfOrganization(21L)).thenReturn(20L);
    when(permissions.companyDepartments(10L)).thenReturn(Set.of(11L, 21L));
    when(users.activeRoleMembers(eq(7L), eq(List.of(30L, 31L)), anyList()))
        .thenReturn(List.of(second));
    var node =
        new Node(
            "approve",
            "APPROVAL",
            "审批",
            "end",
            "ANY",
            List.of("1", "2"),
            List.of("30", "31"),
            List.of());
    var definition =
        new WorkflowDefinition(null, 0, "流程", "requisition", "10", "", null, null, List.of(node));
    assertEquals(Set.of(1L, 2L), actor.candidates(7L, definition, node));
    verify(users).activeIdentities(7L, List.of(1L, 2L));
    verify(permissions, times(2)).companyOfOrganization(11L);
    verify(users)
        .activeRoleMembers(
            eq(7L),
            eq(List.of(30L, 31L)),
            argThat(orgs -> Set.copyOf(orgs).equals(Set.of(10L, 11L))));
  }

  @Test
  void missingOrForeignFixedUsersCannotDisappearSilently() {
    var users = mock(SystemUserRepository.class);
    var permissions = mock(PermissionService.class);
    var actor = new WorkflowActorService(users, mock(RoleManagementRepository.class), permissions);
    var node =
        new Node("approve", "APPROVAL", "审批", "end", "ANY", List.of("1"), List.of(), List.of());
    var definition =
        new WorkflowDefinition(null, 0, "流程", "requisition", "10", "", null, null, List.of(node));
    when(users.activeIdentities(7L, List.of(1L))).thenReturn(List.of());
    assertThrows(IllegalArgumentException.class, () -> actor.candidates(7L, definition, node));
    when(users.activeIdentities(7L, List.of(1L))).thenReturn(List.of(user(1L, 21L)));
    when(permissions.companyOfOrganization(21L)).thenReturn(20L);
    assertThrows(IllegalArgumentException.class, () -> actor.candidates(7L, definition, node));
    verify(users, never()).activeRoleMembers(anyLong(), anyList(), anyList());
  }

  private SystemUser user(long id, long org) {
    var row = new SystemUser();
    row.setId(id);
    row.setOrgId(org);
    return row;
  }
}
