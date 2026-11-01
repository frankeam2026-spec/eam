package com.windasoft.eam.repository.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.windasoft.eam.entity.system.UserRoleLink;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleBatchReadTest {
  @Test
  void emptyBatchDoesNotQueryDatabase() {
    var links = mock(UserRoleLinkMapper.class);
    var repository = new RoleManagementRepository(
        mock(RoleRelationsRepository.class), mock(SystemRoleMapper.class),
        links, mock(RoleMenuPermissionMapper.class));
    assertEquals(0, repository.userRolesForUsers(5L, List.of()).size());
    verifyNoInteractions(links);
  }

  @Test
  void userRolesAreGroupedWithoutPerUserLookups() {
    var links = mock(UserRoleLinkMapper.class);
    var one = new UserRoleLink();
    one.setTenantId(5L);
    one.setUserId(101L);
    one.setRoleId(7L);
    var two = new UserRoleLink();
    two.setTenantId(5L);
    two.setUserId(102L);
    two.setRoleId(8L);
    when(links.selectList(any())).thenReturn(List.of(one, two));
    var repository = new RoleManagementRepository(
        mock(RoleRelationsRepository.class), mock(SystemRoleMapper.class),
        links, mock(RoleMenuPermissionMapper.class));
    var result = repository.userRolesForUsers(5L, List.of(101L, 102L));
    assertEquals(List.of(one), result.get(101L));
    assertEquals(List.of(two), result.get(102L));
    verify(links, times(1)).selectList(any());
  }
}
