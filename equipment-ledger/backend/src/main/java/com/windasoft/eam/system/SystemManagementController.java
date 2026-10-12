package com.windasoft.eam.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system")
public class SystemManagementController {
  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.basedata.BaseItemMapper locationMapper;

  private final OrgUnitMapper orgMapper;
  private final SystemUserMapper userMapper;
  private final SystemRoleMapper roleMapper;
  private final RoleMenuPermissionMapper roleMenuMapper;
  private final UserRoleLinkMapper linkMapper;
  private final AuthSessionMapper sessionMapper;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

  public SystemManagementController(
      OrgUnitMapper orgMapper,
      SystemUserMapper userMapper,
      SystemRoleMapper roleMapper,
      RoleMenuPermissionMapper roleMenuMapper,
      UserRoleLinkMapper linkMapper,
      AuthSessionMapper sessionMapper,
      ObjectMapper json,
      AuthContext auth,
      PermissionService permissions) {
    this.orgMapper = orgMapper;
    this.userMapper = userMapper;
    this.roleMapper = roleMapper;
    this.roleMenuMapper = roleMenuMapper;
    this.linkMapper = linkMapper;
    this.sessionMapper = sessionMapper;
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
  }

  private boolean hasLocations(Long id) {
    return locationMapper.selectCount(
            new LambdaQueryWrapper<com.windasoft.eam.basedata.BaseItem>()
                .eq(com.windasoft.eam.basedata.BaseItem::getTenantId, auth.tenantId())
                .eq(com.windasoft.eam.basedata.BaseItem::getItemType, "LOCATION")
                .eq(com.windasoft.eam.basedata.BaseItem::getCompanyOrgId, id))
        > 0;
  }

  public record OrgForm(
      @NotBlank String name,
      @NotBlank String code,
      @NotBlank String type,
      Long parentId,
      String manager,
      Integer status) {}

  public record UserForm(
      @NotBlank String account,
      @NotBlank String name,
      String employeeNo,
      String phone,
      @NotNull Long orgId,
      List<Long> roleIds,
      Integer status,
      String password) {}

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.jdbc.core.JdbcTemplate jdbc;

  @GetMapping("/roles/warehouse-choices")
  public Object roleWarehouses() {
    permissions.require("role", "授权");
    return Map.of(
        "data",
        jdbc.queryForList(
            "SELECT CAST(w.id AS CHAR) id,CONCAT(o.name,' / ',w.name) name FROM inv_warehouse w"
                + " JOIN eam_org_unit o ON o.tenant_id=w.tenant_id AND o.id=w.company_id WHERE"
                + " w.tenant_id=? ORDER BY o.name,w.name",
            auth.tenantId()));
  }

  public record MenuGrantForm(List<String> actions, List<String> dataScopes, List<Long> orgIds) {}

  public record RoleForm(
      @NotBlank String code,
      @NotBlank String name,
      String description,
      Integer status,
      @NotNull Map<String, MenuGrantForm> menuPermissions) {}

  @GetMapping("/organizations")
  public Map<String, Object> organizations() {
    var rows =
        orgMapper.selectList(
            new LambdaQueryWrapper<OrgUnit>()
                .eq(OrgUnit::getTenantId, auth.tenantId())
                .orderByAsc(OrgUnit::getId));
    return Map.of(
        "data", permissions.visibleOrganizations(rows).stream().map(this::orgView).toList());
  }

  @PostMapping("/organizations")
  public Map<String, Object> createOrg(@Valid @RequestBody OrgForm f) {
    permissions.require("org", "新增");
    checkOrgCode(f.code(), null);
    validateOrg(f.type(), f.parentId(), null);
    var x = new OrgUnit();
    x.setTenantId(auth.tenantId());
    applyOrg(x, f);
    permissions.checkOrg("org", "新增", x);
    orgMapper.insert(x);
    return Map.of("data", orgView(x));
  }

  @PutMapping("/organizations/{id}")
  public Map<String, Object> updateOrg(@PathVariable Long id, @Valid @RequestBody OrgForm f) {
    var x = requireOrg(id);
    permissions.checkOrg("org", "编辑", x);
    checkOrgCode(f.code(), id);
    if (!"公司".equals(f.type()) && hasLocations(id))
      throw new IllegalArgumentException("该公司仍有存放地点，不能修改为其他组织类型");
    validateOrg(f.type(), f.parentId(), id);
    if (f.parentId() != null && isDescendant(f.parentId(), id))
      throw new IllegalArgumentException("不能将组织移动到自己的下级");
    applyOrg(x, f);
    permissions.checkOrg("org", "编辑", x);
    orgMapper.updateById(x);
    return Map.of("data", orgView(x));
  }

  @DeleteMapping("/organizations/{id}")
  public Map<String, Object> deleteOrg(@PathVariable Long id) {
    var x = requireOrg(id);
    permissions.checkOrg("org", "删除", x);
    if (orgMapper.selectCount(
            new LambdaQueryWrapper<OrgUnit>()
                .eq(OrgUnit::getTenantId, auth.tenantId())
                .eq(OrgUnit::getParentId, id))
        > 0) throw new IllegalArgumentException("请先移除下级组织");
    if (userMapper.selectCount(
            new LambdaQueryWrapper<SystemUser>()
                .eq(SystemUser::getTenantId, auth.tenantId())
                .eq(SystemUser::getOrgId, id))
        > 0) throw new IllegalArgumentException("该组织仍有用户，不能删除");
    if (hasLocations(id)) throw new IllegalArgumentException("该公司仍有存放地点，不能删除");
    orgMapper.deleteById(x.getId());
    return Map.of("data", Map.of("deleted", true));
  }

  @GetMapping("/users")
  public Map<String, Object> users() {
    var rows =
        userMapper.selectList(
            new LambdaQueryWrapper<SystemUser>()
                .eq(SystemUser::getTenantId, auth.tenantId())
                .orderByAsc(SystemUser::getId));
    return Map.of("data", permissions.visibleUsers(rows).stream().map(this::userView).toList());
  }

  @PostMapping("/users")
  @Transactional
  public Map<String, Object> createUser(@Valid @RequestBody UserForm f) {
    permissions.require("user", "新增");
    if (f.roleIds() != null && !f.roleIds().isEmpty()) {
      permissions.require("user", "分配角色");
      permissions.require("role", "授权");
    }
    checkAccount(f.account(), null);
    requireDepartment(f.orgId());
    validateRoleIds(f.roleIds());
    var x = new SystemUser();
    x.setTenantId(auth.tenantId());
    applyUser(x, f);
    permissions.checkUser("user", "新增", x);
    setPassword(x, f.password(), true);
    userMapper.insert(x);
    saveRoleLinks(x.getId(), f.roleIds());
    return Map.of("data", userView(x));
  }

  @PutMapping("/users/{id}")
  @Transactional
  public Map<String, Object> updateUser(@PathVariable Long id, @Valid @RequestBody UserForm f) {
    var x = requireUser(id);
    permissions.checkUser("user", "编辑", x);
    var oldRoles =
        linkMapper
            .selectList(
                new QueryWrapper<UserRoleLink>().eq("tenant_id", auth.tenantId()).eq("user_id", id))
            .stream()
            .map(UserRoleLink::getRoleId)
            .collect(java.util.stream.Collectors.toSet());
    if (!oldRoles.equals(new HashSet<>(f.roleIds() == null ? List.of() : f.roleIds()))) {
      permissions.require("user", "分配角色");
      permissions.require("role", "授权");
    }
    checkAccount(f.account(), id);
    requireDepartment(f.orgId());
    validateRoleIds(f.roleIds());
    applyUser(x, f);
    permissions.checkUser("user", "编辑", x);
    setPassword(x, f.password(), false);
    userMapper.updateById(x);
    if (f.password() != null && !f.password().isBlank() || x.getStatus() == 0) revokeSessions(id);
    linkMapper.delete(
        new QueryWrapper<UserRoleLink>().eq("tenant_id", auth.tenantId()).eq("user_id", id));
    saveRoleLinks(id, f.roleIds());
    return Map.of("data", userView(x));
  }

  @PatchMapping("/users/{id}/status")
  public Map<String, Object> userStatus(
      @PathVariable Long id, @RequestBody Map<String, Integer> body) {
    var x = requireUser(id);
    permissions.checkUser("user", "停用", x);
    int status = body.getOrDefault("status", 0);
    if (status != 0 && status != 1) throw new IllegalArgumentException("状态无效");
    if (id.equals(auth.userId()) && status == 0) throw new IllegalArgumentException("不能停用当前登录账号");
    x.setStatus(status);
    userMapper.updateById(x);
    if (status == 0) revokeSessions(id);
    return Map.of("data", userView(x));
  }

  public record UserBatchForm(
      @NotNull List<Long> ids, @NotBlank String operation, Long orgId, String password) {}

  @PostMapping("/users/batch")
  @Transactional
  public Map<String, Object> batchUsers(@Valid @RequestBody UserBatchForm f) {
    if (f.ids().isEmpty() || f.ids().size() > 200 || f.ids().contains(null))
      throw new IllegalArgumentException("请选择 1 至 200 个用户");
    if (!Set.of("department", "password").contains(f.operation()))
      throw new IllegalArgumentException("批量操作无效");
    if ("department".equals(f.operation())) {
      if (f.orgId() == null) throw new IllegalArgumentException("请选择部门");
      requireDepartment(f.orgId());
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
      userMapper.updateById(user);
      revokeSessions(user.getId());
    }
    return Map.of("data", users.stream().map(this::userView).toList());
  }

  @DeleteMapping("/users/{id}")
  @Transactional
  public Map<String, Object> deleteUser(@PathVariable Long id) {
    var x = requireUser(id);
    permissions.checkUser("user", "删除", x);
    revokeSessions(id);
    linkMapper.delete(
        new QueryWrapper<UserRoleLink>().eq("tenant_id", auth.tenantId()).eq("user_id", id));
    userMapper.deleteById(id);
    return Map.of("data", Map.of("deleted", true));
  }

  @GetMapping("/roles")
  public Map<String, Object> roles() {
    permissions.require("role", "查看");
    var rows =
        roleMapper.selectList(
            new LambdaQueryWrapper<SystemRole>()
                .eq(SystemRole::getTenantId, auth.tenantId())
                .orderByAsc(SystemRole::getId));
    return Map.of("data", rows.stream().map(this::roleView).toList());
  }

  @PostMapping("/roles")
  @Transactional
  public Map<String, Object> createRole(@Valid @RequestBody RoleForm f) {
    permissions.require("role", "新增");
    permissions.require("role", "授权");
    checkRoleCode(f.code(), null);
    validateRoleForm(f);
    var x = new SystemRole();
    x.setTenantId(auth.tenantId());
    applyRole(x, f);
    roleMapper.insert(x);
    saveMenuPermissions(x.getId(), f.menuPermissions());
    return Map.of("data", roleView(x));
  }

  @PutMapping("/roles/{id}")
  @Transactional
  public Map<String, Object> updateRole(@PathVariable Long id, @Valid @RequestBody RoleForm f) {
    permissions.require("role", "编辑");
    permissions.require("role", "授权");
    var x = requireRole(id);
    checkRoleCode(f.code(), id);
    validateRoleForm(f);
    applyRole(x, f);
    roleMapper.updateById(x);
    saveMenuPermissions(id, f.menuPermissions());
    return Map.of("data", roleView(x));
  }

  @DeleteMapping("/roles/{id}")
  @Transactional
  public Map<String, Object> deleteRole(@PathVariable Long id) {
    permissions.require("role", "删除");
    requireRole(id);
    if (linkMapper.selectCount(
            new QueryWrapper<UserRoleLink>().eq("tenant_id", auth.tenantId()).eq("role_id", id))
        > 0) throw new IllegalArgumentException("该角色仍分配给用户，不能删除");
    roleMenuMapper.delete(
        new QueryWrapper<RoleMenuPermission>().eq("tenant_id", auth.tenantId()).eq("role_id", id));
    roleMapper.deleteById(id);
    return Map.of("data", Map.of("deleted", true));
  }

  private void applyOrg(OrgUnit x, OrgForm f) {
    x.setName(f.name().trim());
    x.setCode(f.code().trim());
    x.setOrgType(f.type());
    x.setParentId(f.parentId());
    x.setManager(f.manager() == null ? "" : f.manager().trim());
    x.setStatus(f.status() == null ? 1 : f.status());
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
    sessionMapper.delete(
        new QueryWrapper<AuthSession>().eq("tenant_id", auth.tenantId()).eq("user_id", userId));
  }

  private void applyRole(SystemRole x, RoleForm f) {
    x.setCode(f.code().trim());
    x.setName(f.name().trim());
    x.setDescription(f.description() == null ? "" : f.description().trim());
    x.setStatus(f.status() == null ? 1 : f.status());
    x.setDataScope("PER_MENU");
    x.setOrgIdsJson("[]");
    x.setPermissionsJson("[]");
  }

  private void saveRoleLinks(Long userId, List<Long> ids) {
    if (ids == null) return;
    for (Long id : ids) {
      var link = new UserRoleLink();
      link.setTenantId(auth.tenantId());
      link.setUserId(userId);
      link.setRoleId(id);
      linkMapper.insert(link);
    }
  }

  private void validateRoleIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) return;
    long count =
        roleMapper.selectCount(
            new LambdaQueryWrapper<SystemRole>()
                .eq(SystemRole::getTenantId, auth.tenantId())
                .in(SystemRole::getId, ids));
    if (count != new HashSet<>(ids).size()) throw new IllegalArgumentException("包含无效角色");
  }

  private void validateRoleForm(RoleForm f) {
    if (f.menuPermissions().isEmpty()) throw new IllegalArgumentException("请至少配置一个菜单");
    boolean granted = false;
    for (var entry : f.menuPermissions().entrySet()) {
      var menu = entry.getKey();
      var grant = entry.getValue();
      var allowedActions = allowedActions(menu);
      if (allowedActions.isEmpty() || grant == null) throw new IllegalArgumentException("无效的菜单权限");
      var actions = grant.actions() == null ? List.<String>of() : grant.actions();
      if (actions.isEmpty()) continue;
      granted = true;
      if (!allowedActions.containsAll(actions) || actions.size() != new HashSet<>(actions).size())
        throw new IllegalArgumentException(menu + " 包含无效操作");
      if (!actions.contains("查看")) throw new IllegalArgumentException(menu + " 必须包含查看权限");
      var scopes = grant.dataScopes() == null ? List.<String>of() : grant.dataScopes();
      if (scopes.isEmpty()
          || !allowedScopes(menu).containsAll(scopes)
          || scopes.size() != new HashSet<>(scopes).size()
          || (scopes.contains("all") && scopes.size() > 1))
        throw new IllegalArgumentException(menu + " 数据范围无效");
      var ids = grant.orgIds() == null ? List.<Long>of() : grant.orgIds();
      if (scopes.contains("warehouse")) {
        if (ids.isEmpty() || ids.size() != new HashSet<>(ids).size())
          throw new IllegalArgumentException("请选择指定仓库");
        long warehouses =
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inv_warehouse WHERE tenant_id=? AND id IN ("
                    + String.join(",", Collections.nCopies(ids.size(), "?"))
                    + ")",
                Long.class,
                java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(auth.tenantId()), ids.stream())
                    .toArray());
        if (warehouses != ids.size()) throw new IllegalArgumentException("包含无效仓库");
      } else if (scopes.contains("selected")) {
        if (ids.isEmpty() || ids.size() != new HashSet<>(ids).size())
          throw new IllegalArgumentException(menu + " 请选择指定部门");
        long count =
            orgMapper.selectCount(
                new LambdaQueryWrapper<OrgUnit>()
                    .eq(OrgUnit::getTenantId, auth.tenantId())
                    .eq(OrgUnit::getOrgType, "部门")
                    .in(OrgUnit::getId, ids));
        if (count != ids.size()) throw new IllegalArgumentException(menu + " 包含无效部门");
      } else if (!ids.isEmpty()) throw new IllegalArgumentException(menu + " 不应包含指定部门");
    }
    if (!granted) throw new IllegalArgumentException("请至少配置一个菜单");
  }

  private Set<String> allowedActions(String menu) {
    return switch (menu) {
      case "workspace" -> Set.of("查看");
      case "equipment" -> Set.of("查看", "新增", "编辑", "删除", "导入", "导出");
      case "equipmentTag" -> Set.of("查看", "新增", "编辑", "删除", "打印", "导出");
      case "category", "org" -> Set.of("查看", "新增", "编辑", "删除");
      case "repair" -> Set.of("查看", "报修", "编辑", "派工", "处理", "验收", "撤回", "删除", "导出");
      case "maintenanceType",
              "maintenanceStandard",
              "maintenancePlan",
              "inspectionType",
              "inspectionStandard",
              "inspectionDailyPlan",
              "inspectionPeriodicPlan" ->
          Set.of("查看", "新增", "编辑", "删除", "导出");
      case "maintenanceTask" -> Set.of("查看", "编辑", "执行", "验收", "删除", "导出");
      case "inspectionDailyTask", "inspectionPeriodicTask" ->
          Set.of("查看", "执行", "审核", "转派", "删除", "导出");
      case "inventoryMaterial" -> Set.of("查看", "新增", "编辑", "删除", "导入", "导出", "查看金额", "编辑参考价");
      case "inventoryWarehouse" -> Set.of("查看", "新增", "编辑", "删除", "导出");
      case "inventoryReceipt" -> Set.of("查看", "新增", "编辑", "删除", "入库", "导出", "查看金额");
      case "inventoryOutbound" ->
          Set.of("查看", "新增直接出库", "编辑草稿", "删除草稿", "预留", "释放预留", "确认出库", "关闭剩余需求", "导出", "查看金额");
      case "inventoryRequisition" -> Set.of("查看", "新增", "编辑", "删除", "出库", "导出", "查看金额");
      case "inventoryReturn" -> Set.of("查看", "新增", "编辑", "删除", "入库", "导出", "查看金额");
      case "inventoryTransfer" -> Set.of("查看", "新增", "编辑", "删除", "出库", "入库", "导出", "查看金额");
      case "inventoryCount" -> Set.of("查看", "新增", "编辑", "删除", "出库", "入库", "导出", "查看金额");
      case "inventoryOpening" -> Set.of("查看", "新增", "编辑", "删除", "入库", "导入", "导出", "查看金额");
      case "inventoryReversal" -> Set.of("查看", "新增", "编辑", "删除", "冲销", "导出", "查看金额");
      case "inventoryQuery" -> Set.of("查看", "导出", "冻结", "查看金额");
      case "inventory" -> Set.of("查看", "新增", "编辑", "入库", "出库", "导入", "导出");
      case "requisition" -> Set.of("查看", "申请", "审批", "出库", "撤回", "导出");
      case "user" -> Set.of("查看", "新增", "编辑", "停用", "分配角色", "删除", "导入", "导出");
      case "role" -> Set.of("查看", "新增", "编辑", "授权", "删除", "导出");
      case "location" -> Set.of("查看", "新增", "编辑", "删除", "导出");
      case "base" -> Set.of("查看", "新增", "编辑", "删除", "导入", "导出");
      default -> Set.of();
    };
  }

  private Set<String> allowedScopes(String menu) {
    if (menu.equals("inventoryMaterial")) return Set.of("all");
    if (menu.startsWith("inventory") && !menu.equals("inventory"))
      return Set.of("all", "company", "warehouse");
    if (Set.of(
            "workspace",
            "category",
            "equipmentTag",
            "role",
            "base",
            "maintenanceType",
            "inspectionType",
            "location")
        .contains(menu)) return Set.of("all");
    if (Set.of(
            "equipment",
            "repair",
            "maintenanceStandard",
            "maintenancePlan",
            "maintenanceTask",
            "inspectionStandard",
            "inspectionDailyPlan",
            "inspectionPeriodicPlan",
            "inspectionDailyTask",
            "inspectionPeriodicTask",
            "requisition")
        .contains(menu)) return Set.of("all", "company", "department", "selected", "owner");
    return Set.of("all", "company", "department", "selected");
  }

  private void saveMenuPermissions(Long roleId, Map<String, MenuGrantForm> grants) {
    roleMenuMapper.delete(
        new QueryWrapper<RoleMenuPermission>()
            .eq("tenant_id", auth.tenantId())
            .eq("role_id", roleId));
    for (var entry : grants.entrySet()) {
      var grant = entry.getValue();
      if (grant.actions() == null || grant.actions().isEmpty()) continue;
      var row = new RoleMenuPermission();
      row.setTenantId(auth.tenantId());
      row.setRoleId(roleId);
      row.setMenuKey(entry.getKey());
      row.setActionsJson(write(grant.actions()));
      row.setDataScopesJson(write(grant.dataScopes()));
      row.setOrgIdsJson(write(grant.orgIds() == null ? List.of() : grant.orgIds()));
      roleMenuMapper.insert(row);
    }
  }

  private void validateOrg(String type, Long parentId, Long current) {
    if (!Set.of("集团", "公司", "部门").contains(type)) throw new IllegalArgumentException("组织类型无效");
    if ("集团".equals(type) && parentId != null) throw new IllegalArgumentException("集团不能设置上级组织");
    if ("集团".equals(type)) {
      var groups =
          new LambdaQueryWrapper<OrgUnit>()
              .eq(OrgUnit::getTenantId, auth.tenantId())
              .eq(OrgUnit::getOrgType, "集团");
      if (current != null) groups.ne(OrgUnit::getId, current);
      if (orgMapper.selectCount(groups) > 0) throw new IllegalArgumentException("组织架构只能有一个集团");
    }
    if (!"集团".equals(type) && parentId == null) throw new IllegalArgumentException("公司或部门必须选择上级组织");
    if (parentId != null) {
      var p = requireOrg(parentId);
      boolean valid =
          "公司".equals(type)
              ? "集团".equals(p.getOrgType())
              : Set.of("公司", "部门").contains(p.getOrgType());
      if (!valid) throw new IllegalArgumentException("公司只能属于集团；部门只能属于公司或部门");
    }
    if (current != null && current.equals(parentId))
      throw new IllegalArgumentException("不能将组织设置为自己的上级");
    if (current != null) {
      var children =
          orgMapper.selectList(
              new LambdaQueryWrapper<OrgUnit>()
                  .eq(OrgUnit::getTenantId, auth.tenantId())
                  .eq(OrgUnit::getParentId, current));
      if (children.stream()
          .anyMatch(
              child ->
                  "集团".equals(type)
                      ? !"公司".equals(child.getOrgType())
                      : !"部门".equals(child.getOrgType())))
        throw new IllegalArgumentException("组织类型与现有下级不匹配，请先调整下级组织");
    }
  }

  private boolean isDescendant(Long candidate, Long parent) {
    var n = orgMapper.selectById(candidate);
    while (n != null && n.getParentId() != null) {
      if (parent.equals(n.getParentId())) return true;
      n = orgMapper.selectById(n.getParentId());
    }
    return false;
  }

  private void requireDepartment(Long id) {
    var org = requireOrg(id);
    if (!"部门".equals(org.getOrgType())) throw new IllegalArgumentException("员工必须归属一个部门");
  }

  private void checkOrgCode(String code, Long id) {
    var q =
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, auth.tenantId())
            .eq(OrgUnit::getCode, code.trim());
    if (id != null) q.ne(OrgUnit::getId, id);
    if (orgMapper.selectCount(q) > 0) throw new IllegalArgumentException("组织编码已存在");
  }

  private void checkAccount(String account, Long id) {
    var q =
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, auth.tenantId())
            .eq(SystemUser::getAccount, account.trim());
    if (id != null) q.ne(SystemUser::getId, id);
    if (userMapper.selectCount(q) > 0) throw new IllegalArgumentException("登录账号已存在");
  }

  private void checkRoleCode(String code, Long id) {
    var q =
        new LambdaQueryWrapper<SystemRole>()
            .eq(SystemRole::getTenantId, auth.tenantId())
            .eq(SystemRole::getCode, code.trim());
    if (id != null) q.ne(SystemRole::getId, id);
    if (roleMapper.selectCount(q) > 0) throw new IllegalArgumentException("角色编码已存在");
  }

  private OrgUnit requireOrg(Long id) {
    var x =
        orgMapper.selectOne(
            new LambdaQueryWrapper<OrgUnit>()
                .eq(OrgUnit::getTenantId, auth.tenantId())
                .eq(OrgUnit::getId, id));
    if (x == null) throw new IllegalArgumentException("组织不存在");
    return x;
  }

  private SystemUser requireUser(Long id) {
    var x =
        userMapper.selectOne(
            new LambdaQueryWrapper<SystemUser>()
                .eq(SystemUser::getTenantId, auth.tenantId())
                .eq(SystemUser::getId, id));
    if (x == null) throw new IllegalArgumentException("用户不存在");
    return x;
  }

  private SystemRole requireRole(Long id) {
    var x =
        roleMapper.selectOne(
            new LambdaQueryWrapper<SystemRole>()
                .eq(SystemRole::getTenantId, auth.tenantId())
                .eq(SystemRole::getId, id));
    if (x == null) throw new IllegalArgumentException("角色不存在");
    return x;
  }

  private Map<String, Object> orgView(OrgUnit x) {
    return Map.of(
        "id",
        String.valueOf(x.getId()),
        "name",
        x.getName(),
        "code",
        x.getCode(),
        "type",
        x.getOrgType(),
        "parentId",
        x.getParentId() == null ? "" : String.valueOf(x.getParentId()),
        "manager",
        x.getManager() == null ? "" : x.getManager(),
        "status",
        x.getStatus() == 1 ? "启用" : "停用");
  }

  private Map<String, Object> userView(SystemUser x) {
    var ids =
        linkMapper
            .selectList(
                new QueryWrapper<UserRoleLink>()
                    .eq("tenant_id", auth.tenantId())
                    .eq("user_id", x.getId()))
            .stream()
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

  private Map<String, Object> roleView(SystemRole x) {
    Map<String, Object> grants = new LinkedHashMap<>();
    var rows =
        roleMenuMapper.selectList(
            new LambdaQueryWrapper<RoleMenuPermission>()
                .eq(RoleMenuPermission::getTenantId, auth.tenantId())
                .eq(RoleMenuPermission::getRoleId, x.getId())
                .orderByAsc(RoleMenuPermission::getId));
    for (var row : rows)
      grants.put(
          row.getMenuKey(),
          Map.of(
              "actions",
              read(row.getActionsJson()),
              "dataScopes",
              read(row.getDataScopesJson()),
              "orgIds",
              read(row.getOrgIdsJson()).stream().map(String::valueOf).toList()));
    if (rows.isEmpty() && !"PER_MENU".equals(x.getDataScope())) {
      Map<String, List<String>> actions = new LinkedHashMap<>();
      for (var raw : read(x.getPermissionsJson())) {
        var parts = String.valueOf(raw).split("\\.", 2);
        if (parts.length != 2) continue;
        var action = legacyAction(parts[1]);
        var targets = new HashSet<>(PermissionService.legacyTargets(parts[0]));
        targets.add(parts[0]);
        for (String menu : targets)
          if (allowedActions(menu).contains(action))
            actions.computeIfAbsent(menu, k -> new ArrayList<>()).add(action);
      }
      var scope =
          switch (x.getDataScope()) {
            case "全部数据" -> "all";
            case "本公司及下级" -> "company";
            case "指定部门" -> "selected";
            case "本人" -> "owner";
            default -> "department";
          };
      for (var entry : actions.entrySet()) {
        var menu = entry.getKey();
        var menuScope =
            allowedScopes(menu).contains(scope)
                ? scope
                : allowedScopes(menu).contains("department") ? "department" : "all";
        var menuActions = new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
        if (!menuActions.contains("查看")) menuActions.add(0, "查看");
        grants.put(
            menu,
            Map.of(
                "actions",
                menuActions,
                "dataScopes",
                List.of(menuScope),
                "orgIds",
                "selected".equals(menuScope)
                    ? read(x.getOrgIdsJson()).stream().map(String::valueOf).toList()
                    : List.of()));
      }
    }
    return Map.of(
        "id",
        String.valueOf(x.getId()),
        "code",
        x.getCode(),
        "name",
        x.getName(),
        "description",
        x.getDescription() == null ? "" : x.getDescription(),
        "status",
        x.getStatus() == 1 ? "启用" : "停用",
        "menuPermissions",
        grants);
  }

  private String legacyAction(String action) {
    return switch (action) {
      case "view" -> "查看";
      case "create" -> "新增";
      case "edit" -> "编辑";
      case "delete" -> "删除";
      case "disable" -> "停用";
      case "dispatch" -> "派工";
      case "process" -> "处理";
      case "accept" -> "验收";
      case "export" -> "导出";
      default -> action;
    };
  }

  private String write(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private List<?> read(String s) {
    try {
      return s == null ? List.of() : json.readValue(s, new TypeReference<List<Object>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }
}
