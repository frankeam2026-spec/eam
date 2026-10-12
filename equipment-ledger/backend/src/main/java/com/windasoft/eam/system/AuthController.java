package com.windasoft.eam.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final SystemUserMapper users;

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.core.env.Environment environment;

  private final AuthSessionMapper sessions;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final OrgUnitMapper orgs;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
  private final SecureRandom random = new SecureRandom();

  public AuthController(
      SystemUserMapper users,
      AuthSessionMapper sessions,
      AuthContext auth,
      PermissionService permissions,
      OrgUnitMapper orgs) {
    this.users = users;
    this.sessions = sessions;
    this.auth = auth;
    this.permissions = permissions;
    this.orgs = orgs;
  }

  public record LoginForm(
      @NotNull Long tenantId, @NotBlank String account, @NotBlank String password) {}

  @PostMapping("/login")
  public Map<String, Object> login(
      @Valid @RequestBody LoginForm form,
      HttpServletRequest request,
      HttpServletResponse response) {
    var user =
        users.selectOne(
            new LambdaQueryWrapper<SystemUser>()
                .eq(SystemUser::getTenantId, form.tenantId())
                .eq(SystemUser::getAccount, form.account().trim())
                .eq(SystemUser::getStatus, 1));
    if (user == null
        || user.getPasswordHash() == null
        || !passwords.matches(form.password(), user.getPasswordHash()))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "租户、账号或密码错误");
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var session = new AuthSession();
    session.setTenantId(user.getTenantId());
    session.setUserId(user.getId());
    session.setTokenHash(AuthFilter.hash(token));
    session.setExpiresAt(LocalDateTime.now().plusHours(8));
    sessions.insert(session);
    response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, request.isSecure(), 8 * 3600));
    return Map.of("data", view(user));
  }

  @GetMapping("/me")
  public Map<String, Object> me() {
    return Map.of(
        "data",
        view(auth.user()),
        "permissions",
        permissions.menuActions(),
        "developmentMode",
        environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local-dev")));
  }

  @PostMapping("/logout")
  public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
    if (request.getCookies() != null)
      for (var cookie : request.getCookies())
        if (AuthFilter.COOKIE.equals(cookie.getName()))
          sessions.delete(
              new QueryWrapper<AuthSession>().eq("token_hash", AuthFilter.hash(cookie.getValue())));
    response.addHeader(HttpHeaders.SET_COOKIE, cookie("", request.isSecure(), 0));
    return Map.of("data", Map.of("loggedOut", true));
  }

  public record ChangePasswordForm(
      @NotBlank String currentPassword, @NotBlank String newPassword) {}

  @PostMapping("/password")
  @org.springframework.transaction.annotation.Transactional
  public Map<String, Object> changePassword(
      @Valid @RequestBody ChangePasswordForm form, HttpServletRequest request) {
    var user =
        users.selectOne(
            new LambdaQueryWrapper<SystemUser>()
                .eq(SystemUser::getTenantId, auth.tenantId())
                .eq(SystemUser::getId, auth.userId())
                .last("FOR UPDATE"));
    if (user == null
        || user.getPasswordHash() == null
        || !passwords.matches(form.currentPassword(), user.getPasswordHash()))
      throw new IllegalArgumentException("当前密码不正确");
    if (form.newPassword().length() < 12
        || form.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("新密码至少12位，且不超过72字节");
    if (form.currentPassword().equals(form.newPassword()))
      throw new IllegalArgumentException("新密码不能与当前密码相同");
    user.setPasswordHash(passwords.encode(form.newPassword()));
    users.updateById(user);
    String token = null;
    if (request.getCookies() != null)
      for (var c : request.getCookies())
        if (AuthFilter.COOKIE.equals(c.getName())) token = c.getValue();
    var revoke =
        new QueryWrapper<AuthSession>()
            .eq("tenant_id", auth.tenantId())
            .eq("user_id", auth.userId());
    if (token != null) revoke.ne("token_hash", AuthFilter.hash(token));
    sessions.delete(revoke);
    return Map.of("success", true);
  }

  private String cookie(String value, boolean secure, int maxAge) {
    return AuthFilter.COOKIE
        + "="
        + value
        + "; HttpOnly; SameSite=Strict; Path=/api/v1; Max-Age="
        + maxAge
        + (secure ? "; Secure" : "");
  }

  private Map<String, Object> view(SystemUser user) {
    var department =
        orgs.selectOne(
            new LambdaQueryWrapper<OrgUnit>()
                .eq(OrgUnit::getTenantId, user.getTenantId())
                .eq(OrgUnit::getId, user.getOrgId()));
    var company =
        department == null
            ? null
            : orgs.selectOne(
                new LambdaQueryWrapper<OrgUnit>()
                    .eq(OrgUnit::getTenantId, user.getTenantId())
                    .eq(OrgUnit::getId, department.getParentId()));
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
