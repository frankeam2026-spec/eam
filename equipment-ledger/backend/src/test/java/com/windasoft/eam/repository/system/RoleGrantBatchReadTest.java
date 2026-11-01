package com.windasoft.eam.repository.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.windasoft.eam.entity.system.RoleMenuPermission;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleGrantBatchReadTest {
  @Test
  void grantsForMultipleRolesAreReadOnceAndGrouped() {
    var mapper = mock(RoleMenuPermissionMapper.class);
    var relations = mock(RoleRelationsRepository.class);
    var first = new RoleMenuPermission();
    first.setRoleId(11L);
    var second = new RoleMenuPermission();
    second.setRoleId(12L);
    when(mapper.selectList(any())).thenReturn(List.of(first, second));
    when(relations.grants(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var repository = new RoleManagementRepository(
        relations, mock(SystemRoleMapper.class), mock(UserRoleLinkMapper.class), mapper);

    var rows = repository.menuPermissionsForRoles(5L, List.of(11L, 12L));

    assertEquals(List.of(first), rows.get(11L));
    assertEquals(List.of(second), rows.get(12L));
    verify(mapper, times(1)).selectList(any());
  }

  @Test
  void emptyInputSkipsGrantQueries() {
    var mapper = mock(RoleMenuPermissionMapper.class);
    var repository = new RoleManagementRepository(
        mock(RoleRelationsRepository.class), mock(SystemRoleMapper.class),
        mock(UserRoleLinkMapper.class), mapper);
    assertEquals(0, repository.menuPermissionsForRoles(5L, List.of()).size());
    verifyNoInteractions(mapper);
  }
}
