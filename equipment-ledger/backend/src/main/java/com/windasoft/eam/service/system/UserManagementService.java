package com.windasoft.eam.service.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.UserBatchForm;
import com.windasoft.eam.dto.system.UserForm;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.entity.system.UserRoleLink;
import com.windasoft.eam.repository.system.SystemManagementRepository;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {
  private final SystemManagementRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

  public UserManagementService(
      SystemManagementRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
  }

  public Map<String, Object> users() {
    var rows = repository.users(auth.tenantId());
    return Map.of("data", permissions.visibleUsers(rows).stream().map(this::userView).toList());
  }

  @Transactional
  public Map<String, Object> createUser(UserForm f) {
    permissions.require("user", "新增");
    if (f.roleIds() != null && !f.roleIds().isEmpty()) {
      permissions.require("user", "分配角色");
      permissions.require("role", "授权");
    }
    checkAccount(f.account(), null);
    requireUserOrganization(f.orgId());
    validateRoleIds(f.roleIds());
    var x = new SystemUser();
    x.setTenantId(auth.tenantId());
    applyUser(x, f);
    permissions.checkUser("user", "新增", x);
    setPassword(x, f.password(), true);
    repository.insertUser(x);
    saveRoleLinks(x.getId(), f.roleIds());
    return Map.of("data", userView(x));
  }

  @Transactional
  public Map<String, Object> updateUser(Long id, UserForm f) {
    var x = requireUser(id);
    permissions.checkUser("user", "编辑", x);
    var oldRoles =
        repository.userRoles(auth.tenantId(), id).stream()
            .map(UserRoleLink::getRoleId)
            .collect(java.util.stream.Collectors.toSet());
    if (!oldRoles.equals(new HashSet<>(f.roleIds() == null ? List.of() : f.roleIds()))) {
      permissions.require("user", "分配角色");
      permissions.require("role", "授权");
    }
    checkAccount(f.account(), id);
    requireUserOrganization(f.orgId());
    validateRoleIds(f.roleIds());
    applyUser(x, f);
    permissions.checkUser("user", "编辑", x);
    setPassword(x, f.password(), false);
    repository.updateUser(x);
    if (f.password() != null && !f.password().isBlank() || x.getStatus() == 0) revokeSessions(id);
    repository.deleteUserRoles(auth.tenantId(), id);
    saveRoleLinks(id, f.roleIds());
    return Map.of("data", userView(x));
  }

  public Map<String, Object> userStatus(Long id, Map<String, Integer> body) {
    var x = requireUser(id);
    permissions.checkUser("user", "停用", x);
    int status = body.getOrDefault("status", 0);
    if (status != 0 && status != 1) throw new IllegalArgumentException("状态无效");
    if (id.equals(auth.userId()) && status == 0) throw new IllegalArgumentException("不能停用当前登录账号");
    x.setStatus(status);
    repository.updateUser(x);
    if (status == 0) revokeSessions(id);
    return Map.of("data", userView(x));
  }

  @Transactional
  public Map<String, Object> batchUsers(UserBatchForm f) {
    if (f.ids().isEmpty() || f.ids().size() > 200 || f.ids().contains(null))
      throw new IllegalArgumentException("请选择 1 至 200 个用户");
    if (!Set.of("department", "password").contains(f.operation()))
      throw new IllegalArgumentException("批量操作无效");
    if ("department".equals(f.operation())) {
      if (f.orgId() == null) throw new IllegalArgumentException("请选择部门");
      requireUserOrganization(f.orgId());
      if (requireOrg(f.orgId()).getStatus() != 1) throw new IllegalArgumentException("不能选择停用部门");
    } else if (f.password() == null || f.password().length() < 12 || f.password().length() > 72)
      throw new IllegalArgumentException("密码长度需为 12 至 72 位");
    var users = new LinkedHashSet<>(f.ids()).stream().map(this::requireUser).toList();
    for (var user : users) {
      permissions.checkUser("user", "编辑", user);
      if ("department".equals(f.operation())) {
        user.setOrgId(f.orgId());
        permissions.checkUser("user", "编辑", user);
      }
    }
    for (var user : users) {
      if ("password".equals(f.operation())) setPassword(user, f.password(), true);
      repository.updateUser(user);
      revokeSessions(user.getId());
    }
    return Map.of("data", users.stream().map(this::userView).toList());
  }

  @Transactional
  public Map<String, Object> deleteUser(Long id) {
    var x = requireUser(id);
    permissions.checkUser("user", "删除", x);
    revokeSessions(id);
    repository.deleteUserRoles(auth.tenantId(), id);
    repository.deleteUser(id);
    return Map.of("data", Map.of("deleted", true));
  }

  private void applyUser(SystemUser x, UserForm f) {
    x.setAccount(f.account().trim());
    x.setDisplayName(f.name().trim());
    x.setEmployeeNo(f.employeeNo() == null ? "" : f.employeeNo().trim());
    x.setPhone(f.phone() == null ? "" : f.phone().trim());
    x.setOrgId(f.orgId());
    x.setStatus(f.status() == null ? 1 : f.status());
  }

  private void setPassword(SystemUser user, String password, boolean required) {
    if (password == null || password.isBlank()) {
      if (required) throw new IllegalArgumentException("请设置初始密码");
      return;
    }
    if (password.length() < 12 || password.length() > 72)
      throw new IllegalArgumentException("密码长度需为 12 至 72 位");
    user.setPasswordHash(passwords.encode(password));
  }

  private void revokeSessions(Long userId) {
    repository.revokeSessions(auth.tenantId(), userId);
  }

  private void saveRoleLinks(Long userId, List<Long> ids) {
    if (ids == null) return;
    for (Long id : ids) {
      var link = new UserRoleLink();
      link.setTenantId(auth.tenantId());
      link.setUserId(userId);
      link.setRoleId(id);
      repository.insertUserRole(link);
    }
  }

  private void validateRoleIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) return;
    long count = repository.roleCount(auth.tenantId(), ids);
    if (count != new HashSet<>(ids).size()) throw new IllegalArgumentException("包含无效角色");
  }

  private void requireUserOrganization(Long id) {
    var org = requireOrg(id);
    if (org.getStatus() != 1) throw new IllegalArgumentException("所属组织已停用");
  }

  private void checkAccount(String account, Long id) {
    if (repository.accountCount(auth.tenantId(), account, id) > 0)
      throw new IllegalArgumentException("登录账号已存在");
  }

  private SystemUser requireUser(Long id) {
    var x = repository.user(auth.tenantId(), id);
    if (x == null) throw new IllegalArgumentException("用户不存在");
    return x;
  }

  private OrgUnit requireOrg(Long id) {
    var x = repository.organization(auth.tenantId(), id);
    if (x == null) throw new IllegalArgumentException("组织不存在");
    return x;
  }

  private Map<String, Object> userView(SystemUser x) {
    var ids =
        repository.userRoles(auth.tenantId(), x.getId()).stream()
            .map(UserRoleLink::getRoleId)
            .map(String::valueOf)
            .toList();
    return Map.of(
        "id",
        String.valueOf(x.getId()),
        "account",
        x.getAccount(),
        "name",
        x.getDisplayName(),
        "employeeNo",
        x.getEmployeeNo() == null ? "" : x.getEmployeeNo(),
        "phone",
        x.getPhone() == null ? "" : x.getPhone(),
        "orgId",
        String.valueOf(x.getOrgId()),
        "roleIds",
        ids,
        "status",
        x.getStatus() == 1 ? "启用" : "停用");
  }
}
