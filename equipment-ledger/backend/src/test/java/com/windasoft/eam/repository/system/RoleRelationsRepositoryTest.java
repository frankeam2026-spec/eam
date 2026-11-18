package com.windasoft.eam.repository.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.windasoft.eam.entity.system.RoleMenuAction;
import com.windasoft.eam.entity.system.RoleMenuPermission;
import com.windasoft.eam.entity.system.RoleMenuScope;
import com.windasoft.eam.entity.system.RoleMenuTarget;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleRelationsRepositoryTest {
  @Test
  void groupsGrantRelationsWithoutMixingGrantsAndKeepsRowOrder() {
    var actions = mock(RoleMenuActionRepository.class);
    var scopes = mock(RoleMenuScopeRepository.class);
    var targets = mock(RoleMenuTargetRepository.class);
    var first = new RoleMenuPermission();
    first.setId(11L);
    first.setTenantId(7L);
    var second = new RoleMenuPermission();
    second.setId(12L);
    second.setTenantId(7L);
    var a = new RoleMenuAction();
    a.setGrantId(11L);
    a.setActionName("查看");
    var b = new RoleMenuAction();
    b.setGrantId(12L);
    b.setActionName("编辑");
    var c = new RoleMenuAction();
    c.setGrantId(11L);
    c.setActionName("删除");
    var scope = new RoleMenuScope();
    scope.setGrantId(11L);
    scope.setScopeCode("all");
    var target = new RoleMenuTarget();
    target.setGrantId(12L);
    target.setTargetId(100L);
    when(actions.find(7L, List.of(11L, 12L))).thenReturn(List.of(a, b, c));
    when(scopes.find(7L, List.of(11L, 12L))).thenReturn(List.of(scope));
    when(targets.find(7L, List.of(11L, 12L))).thenReturn(List.of(target));
    var result =
        new RoleRelationsRepository(
                mock(RoleOrganizationRepository.class),
                mock(RoleLegacyActionRepository.class),
                actions,
                scopes,
                targets)
            .grants(List.of(first, second));
    assertEquals(List.of(first, second), result);
    assertEquals(List.of("查看", "删除"), first.getActions());
    assertEquals(List.of("all"), first.getDataScopes());
    assertEquals(List.of(), first.getTargetIds());
    assertEquals(List.of("编辑"), second.getActions());
    assertEquals(List.of(), second.getDataScopes());
    assertEquals(List.of(100L), second.getTargetIds());
    verify(actions).find(7L, List.of(11L, 12L));
    verify(scopes).find(7L, List.of(11L, 12L));
    verify(targets).find(7L, List.of(11L, 12L));
  }
}
