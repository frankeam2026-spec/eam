package com.windasoft.eam.service.system;

import com.windasoft.eam.config.security.AuthFilter;
import com.windasoft.eam.dto.system.AuthenticationResult;
import com.windasoft.eam.dto.system.ChangePasswordForm;
import com.windasoft.eam.dto.system.LoginForm;
import com.windasoft.eam.entity.system.AuthSession;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.system.AuthenticationRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
  private final AuthenticationRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final org.springframework.core.env.Environment environment;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
  private final SecureRandom random = new SecureRandom();

  public AuthenticationService(
      AuthenticationRepository repository,
      AuthContext auth,
      PermissionService permissions,
      org.springframework.core.env.Environment environment) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.environment = environment;
  }

  public AuthenticationResult login(LoginForm form) {
    var user = repository.activeUser(form.tenantId(), form.account().trim());
    if (user == null
        || user.getPasswordHash() == null
        || !passwords.matches(form.password(), user.getPasswordHash()))
      throw new com.windasoft.eam.common.LocalizedStatusException(
          HttpStatus.UNAUTHORIZED, "server.system.message0035");
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var session = new AuthSession();
    session.setTenantId(user.getTenantId());
    session.setUserId(user.getId());
    session.setTokenHash(AuthFilter.hash(token));
    session.setExpiresAt(LocalDateTime.now().plusHours(8));
    repository.insertSession(session);
    return new AuthenticationResult(token, view(user));
  }

  public Map<String, Object> me() {
    return Map.of(
        "data",
        view(auth.user()),
        "permissions",
        permissions.menuActions(),
        "developmentMode",
        environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local-dev")));
  }

  public void logout(String token) {
    if (token != null) repository.deleteToken(AuthFilter.hash(token));
  }

  @org.springframework.transaction.annotation.Transactional
  public Map<String, Object> changePassword(ChangePasswordForm form, String token) {
    if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local-dev")))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.system.message0036");
    var user = repository.lockUser(auth.tenantId(), auth.userId());
    if (user == null
        || user.getPasswordHash() == null
        || !passwords.matches(form.currentPassword(), user.getPasswordHash()))
      throw new com.windasoft.eam.common.FieldValidationException("currentPassword", "当前密码不正确");
    if (form.newPassword().length() < 6
        || form.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new com.windasoft.eam.common.FieldValidationException(
          "newPassword", "新密码至少6位，且不超过72字节");
    if (form.currentPassword().equals(form.newPassword()))
      throw new com.windasoft.eam.common.FieldValidationException("newPassword", "新密码不能与当前密码相同");
    user.setPasswordHash(passwords.encode(form.newPassword()));
    repository.updateUser(user);
    repository.revokeOtherSessions(auth.tenantId(), auth.userId(), null);
    return Map.of("success", true);
  }

  private Map<String, Object> view(SystemUser user) {
    var department = repository.organization(user.getTenantId(), user.getOrgId());
    var company =
        department == null
            ? null
            : repository.organization(user.getTenantId(), department.getParentId());
    return Map.of(
        "id",
        String.valueOf(user.getId()),
        "tenantId",
        String.valueOf(user.getTenantId()),
        "account",
        user.getAccount(),
        "name",
        user.getDisplayName(),
        "orgId",
        String.valueOf(user.getOrgId()),
        "company",
        company == null ? "" : company.getName(),
        "department",
        department == null ? "" : department.getName());
  }
}
