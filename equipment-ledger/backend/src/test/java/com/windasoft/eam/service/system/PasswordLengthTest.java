package com.windasoft.eam.service.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.dto.system.ChangePasswordForm;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.system.AuthenticationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordLengthTest {
  @Test
  void acceptsSixCharactersAndRevokesSessions() {
    var repository = mock(AuthenticationRepository.class);
    var auth = mock(AuthContext.class);
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(1L);
    var user = new SystemUser();
    user.setPasswordHash(new BCryptPasswordEncoder().encode("old-password"));
    when(repository.lockUser(10001L, 1L)).thenReturn(user);
    var service = new AuthenticationService(repository, auth, mock(PermissionService.class), mock(Environment.class));
    service.changePassword(new ChangePasswordForm("old-password", "123456"), null);
    assertTrue(new BCryptPasswordEncoder().matches("123456", user.getPasswordHash()));
    verify(repository).updateUser(user);
    verify(repository).revokeOtherSessions(10001L, 1L, null);
  }

  @Test
  void rejectsFiveCharactersWithoutChangingPassword() {
    var repository = mock(AuthenticationRepository.class);
    var auth = mock(AuthContext.class);
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(1L);
    var user = new SystemUser();
    user.setPasswordHash(new BCryptPasswordEncoder().encode("old-password"));
    when(repository.lockUser(10001L, 1L)).thenReturn(user);
    var service = new AuthenticationService(repository, auth, mock(PermissionService.class), mock(Environment.class));
    assertThrows(com.windasoft.eam.common.FieldValidationException.class,
        () -> service.changePassword(new ChangePasswordForm("old-password", "12345"), null));
    verify(repository, never()).updateUser(any());
    assertTrue(new BCryptPasswordEncoder().matches("old-password", user.getPasswordHash()));
  }
}
