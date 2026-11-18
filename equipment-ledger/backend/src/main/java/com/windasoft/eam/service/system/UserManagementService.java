package com.windasoft.eam.service.system;

import com.windasoft.eam.common.FieldValidationException;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.dto.system.UserBatchForm;
import com.windasoft.eam.dto.system.UserEmailProfile;
import com.windasoft.eam.dto.system.UserForm;
import com.windasoft.eam.dto.system.UserStatusForm;
import com.windasoft.eam.dto.system.UserView;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.entity.system.UserRoleLink;
import com.windasoft.eam.repository.system.AuthenticationRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.repository.system.RoleManagementRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户管理服务：协调账号唯一性、组织归属、角色分配及会话失效，敏感操作须经授权。 */
@Service
public class UserManagementService {
  private final AuthenticationRepository sessions;
  private final RoleManagementRepository roleRepository;
  private final SystemUserRepository systemUsers;
  private final OrgUnitRepository orgUnits;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

  public UserManagementService(
      AuthenticationRepository sessions,
      RoleManagementRepository roleRepository,
      SystemUserRepository systemUsers,
      OrgUnitRepository orgUnits,
      AuthContext auth,
      PermissionService permissions) {
    this.sessions = sessions;
    this.roleRepository = roleRepository;
    this.systemUsers = systemUsers;
    this.orgUnits = orgUnits;
    this.auth = auth;
    this.permissions = permissions;
  }

  @Transactional
  public UserView createUser(UserForm f) {
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
    systemUsers.insertUser(x);
    saveRoleLinks(x.getId(), f.roleIds());
    return userView(x);
  }

  /** 修改前校验原人员可编辑，修改组织后再次校验目标范围，防止把可见用户移入未授权部门。 角色变化才追加授权权限；邮箱以独立版本更新，会话撤销或关联写入失败时整笔回滚。 */
  @Transactional
  public UserView updateUser(Long id, UserForm f) {
    var x = requireUser(id);
    permissions.checkUser("user", "编辑", x);
    var oldRoles =
        roleRepository.userRoles(auth.tenantId(), id).stream()
            .map(UserRoleLink::getRoleId)
            .collect(Collectors.toSet());
    if (!oldRoles.equals(new HashSet<>(f.roleIds() == null ? List.of() : f.roleIds()))) {
      permissions.require("user", "分配角色");
      permissions.require("role", "授权");
    }
    checkAccount(f.account(), id);
    requireUserOrganization(f.orgId());
    validateRoleIds(f.roleIds());
    applyUser(x, f);
    permissions.checkUser("user", "编辑", x);
    requireStatusChangeAllowed(id, x.getStatus());
    setPassword(x, f.password(), false);
    systemUsers.updateProfile(x);
    if (f.password() != null && !f.password().isBlank()) systemUsers.updatePassword(x);
    if (f.email() != null) {
      if (f.emailVersion() == null)
        throw new LocalizedArgumentException("server.common.message0018");
      VersionGuard.updated(
          systemUsers.updateEmail(auth.tenantId(), id, f.email().trim(), f.emailVersion()));
    }
    if (f.password() != null && !f.password().isBlank() || x.getStatus() == 0) revokeSessions(id);
    roleRepository.deleteUserRoles(auth.tenantId(), id);
    saveRoleLinks(id, f.roleIds());
    return userView(x);
  }

  @Transactional
  public UserView userStatus(Long id, UserStatusForm body) {
    var x = requireUser(id);
    permissions.checkUser("user", "停用", x);
    if (body == null || body.status() == null)
      throw new LocalizedArgumentException("server.system.message0037");
    int status = body.status();
    requireStatusChangeAllowed(id, status);
    x.setStatus(status);
    systemUsers.updateStatus(x);
    if (status == 0) revokeSessions(id);
    return userView(x);
  }

  /** 批量修改先校验全部目标及目标部门，再写入并撤销会话；任一失败不得只修改部分账号。 */
  @Transactional
  public List<UserView> batchUsers(UserBatchForm f) {
    if (f.ids().isEmpty() || f.ids().size() > 200 || f.ids().contains(null))
      throw new LocalizedArgumentException("ui.equipment.text1182");
    if (!Set.of("department", "password").contains(f.operation()))
      throw new LocalizedArgumentException("server.system.message0039");
    if ("department".equals(f.operation())) {
      if (f.orgId() == null) throw new LocalizedArgumentException("ui.equipment.text0315");
      requireUserOrganization(f.orgId());
      if (requireOrg(f.orgId()).getStatus() != 1)
        throw new LocalizedArgumentException("server.system.message0040");
    } else if (f.password() == null || f.password().length() < 6 || f.password().length() > 72)
      throw new LocalizedArgumentException("ui.equipment.text1179");
    // 多账号按 ID 加锁，避免两个相反顺序的批次互相等待。响应仍按输入去重后的顺序。
    var requested = new LinkedHashSet<>(f.ids());
    var locked = new HashMap<Long, SystemUser>();
    requested.stream().sorted().forEach(id -> locked.put(id, requireUser(id)));
    var users = requested.stream().map(locked::get).toList();
    for (var user : users) {
      permissions.checkUser("user", "编辑", user);
      if ("department".equals(f.operation())) {
        user.setOrgId(f.orgId());
        permissions.checkUser("user", "编辑", user);
      }
    }
    for (var user : users) {
      if ("password".equals(f.operation())) {
        setPassword(user, f.password(), true);
        systemUsers.updatePassword(user);
      } else {
        systemUsers.updateDepartment(user);
      }
      revokeSessions(user.getId());
    }
    var ids = users.stream().map(SystemUser::getId).toList();
    var rolesByUser = roleRepository.userRolesForUsers(auth.tenantId(), ids);
    var emailsByUser = systemUsers.emailProfiles(auth.tenantId(), ids);
    return users.stream()
        .map(
            user ->
                userView(
                    user,
                    rolesByUser.getOrDefault(user.getId(), List.of()),
                    emailsByUser.get(user.getId())))
        .toList();
  }

  @Transactional
  public void deleteUser(Long id) {
    var x = requireUser(id);
    permissions.checkUser("user", "删除", x);
    revokeSessions(id);
    roleRepository.deleteUserRoles(auth.tenantId(), id);
    systemUsers.deleteUser(id);
  }

  private void applyUser(SystemUser x, UserForm f) {
    x.setAccount(f.account().trim());
    x.setDisplayName(f.name().trim());
    x.setEmployeeNo(f.employeeNo() == null ? "" : f.employeeNo().trim());
    x.setPhone(f.phone() == null ? "" : f.phone().trim());
    if (f.email() != null) x.setEmail(f.email().trim());
    x.setOrgId(f.orgId());
    int status = f.status() == null ? 1 : f.status();
    requireLegalStatus(status);
    x.setStatus(status);
  }

  private void requireLegalStatus(int status) {
    if (status != 0 && status != 1) {
      throw new LocalizedArgumentException("server.system.message0037");
    }
  }

  /** 编辑和单独停用沿用同一条自停用禁令，不能通过修改入口绕过。 */
  private void requireStatusChangeAllowed(Long id, int status) {
    requireLegalStatus(status);
    if (id.equals(auth.userId()) && status == 0) {
      throw new LocalizedArgumentException("server.system.message0038");
    }
  }

  private void setPassword(SystemUser user, String password, boolean required) {
    if (password == null || password.isBlank()) {
      if (required) throw new LocalizedArgumentException("server.system.message0041");
      return;
    }
    if (password.length() < 6 || password.length() > 72)
      throw new LocalizedArgumentException("ui.equipment.text1179");
    user.setPasswordHash(passwords.encode(password));
  }

  private void revokeSessions(Long userId) {
    sessions.revokeSessions(auth.tenantId(), userId);
  }

  private void saveRoleLinks(Long userId, List<Long> ids) {
    if (ids == null) return;
    for (Long id : ids) {
      var link = new UserRoleLink();
      link.setTenantId(auth.tenantId());
      link.setUserId(userId);
      link.setRoleId(id);
      roleRepository.insertUserRole(link);
    }
  }

  private void validateRoleIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) return;
    long count = roleRepository.roleCount(auth.tenantId(), ids);
    if (count != new HashSet<>(ids).size())
      throw new LocalizedArgumentException("server.system.message0042");
  }

  private void requireUserOrganization(Long id) {
    var org = requireOrg(id);
    if (org.getStatus() != 1) throw new LocalizedArgumentException("server.system.message0043");
  }

  private void checkAccount(String account, Long id) {
    if (systemUsers.accountCount(auth.tenantId(), account, id) > 0)
      throw new FieldValidationException("account", "登录账号已存在");
  }

  private SystemUser requireUser(Long id) {
    var x = systemUsers.lockUser(auth.tenantId(), id);
    if (x == null) throw new LocalizedArgumentException("server.system.message0044");
    return x;
  }

  private OrgUnit requireOrg(Long id) {
    var x = orgUnits.organization(auth.tenantId(), id);
    if (x == null) throw new LocalizedArgumentException("server.system.message0031");
    return x;
  }

  private UserView userView(SystemUser x) {
    return userView(
        x,
        roleRepository.userRoles(auth.tenantId(), x.getId()),
        systemUsers.emailProfile(auth.tenantId(), x.getId()));
  }

  private UserView userView(SystemUser x, List<UserRoleLink> roles, UserEmailProfile emailProfile) {
    var ids = roles.stream().map(UserRoleLink::getRoleId).map(String::valueOf).toList();
    return new UserView(
        String.valueOf(x.getId()),
        x.getAccount(),
        x.getDisplayName(),
        x.getEmployeeNo() == null ? "" : x.getEmployeeNo(),
        x.getPhone() == null ? "" : x.getPhone(),
        String.valueOf(x.getOrgId()),
        ids,
        x.getStatus() == 1 ? "启用" : "停用",
        Objects.toString(emailProfile.email(), Objects.toString(x.getEmail(), "")),
        emailProfile.version());
  }
}
