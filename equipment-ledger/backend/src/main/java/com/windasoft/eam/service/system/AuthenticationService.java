package com.windasoft.eam.service.system;

import com.windasoft.eam.common.FieldValidationException;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.config.security.AuthFilter;
import com.windasoft.eam.dto.common.SuccessResponse;
import com.windasoft.eam.dto.system.AuthenticatedUserView;
import com.windasoft.eam.dto.system.AuthenticationResult;
import com.windasoft.eam.dto.system.ChangePasswordForm;
import com.windasoft.eam.dto.system.CurrentUserView;
import com.windasoft.eam.dto.system.LoginForm;
import com.windasoft.eam.entity.system.AuthSession;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.system.AuthenticationRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 身份认证服务：负责凭据校验和登录流程，不能绕过会话安全规则。 */
@Service
public class AuthenticationService {
  private final AuthenticationRepository repository;
  private final SystemUserRepository users;
  private final OrgUnitRepository organizations;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final Environment environment;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
  private final SecureRandom random = new SecureRandom();

  public AuthenticationService(
      AuthenticationRepository repository,
      SystemUserRepository users,
      OrgUnitRepository organizations,
      AuthContext auth,
      PermissionService permissions,
      Environment environment) {
    this.repository = repository;
    this.users = users;
    this.organizations = organizations;
    this.auth = auth;
    this.permissions = permissions;
    this.environment = environment;
  }

  /** 密码校验后锁定并复核凭据，保证会话签发与密码修改、停用和删除互斥。 */
  @Transactional
  public AuthenticationResult login(LoginForm form) {
    String account = form.account().trim();
    var observed = users.activeUser(form.tenantId(), account);
    if (observed == null
        || observed.getPasswordHash() == null
        || !passwords.matches(form.password(), observed.getPasswordHash()))
      throw new LocalizedStatusException(HttpStatus.UNAUTHORIZED, "server.system.message0035");
    var user = users.lockUser(form.tenantId(), observed.getId());
    if (user == null
        || user.getStatus() != 1
        || !Objects.equals(user.getAccount(), observed.getAccount())
        || !Objects.equals(user.getPasswordHash(), observed.getPasswordHash()))
      throw new LocalizedStatusException(HttpStatus.UNAUTHORIZED, "server.system.message0035");
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

  /** 仅返回当前可信身份与已授权菜单，开发标志只取有效运行配置。 */
  public CurrentUserView me() {
    return new CurrentUserView(
        view(auth.user()),
        permissions.menuActions(),
        environment.acceptsProfiles(Profiles.of("local-dev")));
  }

  public void logout(String token) {
    if (token != null) repository.deleteToken(AuthFilter.hash(token));
  }

  @Transactional
  public SuccessResponse changePassword(ChangePasswordForm form, String token) {
    if (environment.acceptsProfiles(Profiles.of("local-dev")))
      throw new LocalizedArgumentException("server.system.message0036");
    var user = users.lockUser(auth.tenantId(), auth.userId());
    if (user == null
        || user.getPasswordHash() == null
        || !passwords.matches(form.currentPassword(), user.getPasswordHash()))
      throw new FieldValidationException("currentPassword", "当前密码不正确");
    if (form.newPassword().length() < 6
        || form.newPassword().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new FieldValidationException("newPassword", "新密码至少6位，且不超过72字节");
    if (form.currentPassword().equals(form.newPassword()))
      throw new FieldValidationException("newPassword", "新密码不能与当前密码相同");
    user.setPasswordHash(passwords.encode(form.newPassword()));
    users.updatePassword(user);
    repository.revokeOtherSessions(auth.tenantId(), auth.userId(), null);
    return new SuccessResponse(true);
  }

  private AuthenticatedUserView view(SystemUser user) {
    var department = organizations.organization(user.getTenantId(), user.getOrgId());
    var company =
        department == null
            ? null
            : organizations.organization(user.getTenantId(), department.getParentId());
    return new AuthenticatedUserView(
        String.valueOf(user.getId()),
        String.valueOf(user.getTenantId()),
        user.getAccount(),
        user.getDisplayName(),
        String.valueOf(user.getOrgId()),
        company == null ? "" : company.getName(),
        department == null ? "" : department.getName());
  }
}
