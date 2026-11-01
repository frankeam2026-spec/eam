package com.windasoft.eam.service.system;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.system.SystemManagementRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class UserManagementServiceTest {
  @Test
  void statusChangeHasTransactionBoundaryAndPropagatesSessionRevocationFailure() throws Exception {
    assertNotNull(
        UserManagementService.class
            .getMethod("userStatus", Long.class, Map.class)
            .getAnnotation(Transactional.class));

    var repository = mock(SystemManagementRepository.class);
    var users = mock(SystemUserRepository.class);
    var auth = mock(AuthContext.class);
    var permissions = mock(PermissionService.class);
    var service = new UserManagementService(repository, users, mock(OrgUnitRepository.class), auth, permissions, new ObjectMapper());
    var user = new SystemUser();
    user.setId(21L);
    user.setTenantId(7L);
    user.setStatus(1);
    when(auth.tenantId()).thenReturn(7L);
    when(auth.userId()).thenReturn(99L);
    when(users.user(7L, 21L)).thenReturn(user);
    doThrow(new IllegalStateException("session delete failed"))
        .when(repository).revokeSessions(7L, 21L);

    assertThrows(IllegalStateException.class, () -> service.userStatus(21L, Map.of("status", 0)));
    var order = inOrder(repository);
    verify(users).user(7L, 21L);
    verify(users).updateUser(user);
    order.verify(repository).revokeSessions(7L, 21L);
    verify(permissions).checkUser("user", "停用", user);
  }
}
