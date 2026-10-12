package com.windasoft.eam.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.equipment.Equipment;
import com.windasoft.eam.repair.RepairOrder;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PermissionService {
  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.repair.RepairWorkflowMapper workflow;

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.jdbc.core.JdbcTemplate maintenanceDb;

  private final AuthContext auth;
  private final UserRoleLinkMapper links;
  private final SystemRoleMapper roles;
  private final RoleMenuPermissionMapper grants;
  private final OrgUnitMapper orgs;
  private final SystemUserMapper users;
  private final ObjectMapper json;

  public PermissionService(
      AuthContext auth,
      UserRoleLinkMapper links,
      SystemRoleMapper roles,
      RoleMenuPermissionMapper grants,
      OrgUnitMapper orgs,
      SystemUserMapper users,
      ObjectMapper json) {
    this.auth = auth;
    this.links = links;
    this.roles = roles;
    this.grants = grants;
    this.orgs = orgs;
    this.users = users;
    this.json = json;
  }

  public record Scope(boolean all, Long companyId, Set<Long> departmentIds, boolean owner) {
    public boolean matches(Long company, Long department, Long ownerId, long userId) {
      return all
          || (companyId != null && companyId.equals(company))
          || (department != null && departmentIds.contains(department))
          || (owner && ownerId != null && ownerId == userId);
    }
  }

  public void require(String menu, String action) {
    scopes(menu, action);
  }

  public boolean allowed(String menu, String action) {
    try {
      require(menu, action);
      return true;
    } catch (ResponseStatusException denied) {
      return false;
    }
  }

  public Map<String, Set<String>> menuActions() {
    var roleIds =
        links
            .selectList(
                new QueryWrapper<UserRoleLink>()
                    .eq("tenant_id", auth.tenantId())
                    .eq("user_id", auth.userId()))
            .stream()
            .map(UserRoleLink::getRoleId)
            .toList();
    Map<String, Set<String>> result = new LinkedHashMap<>();
    if (workflow != null && workflow.personalAccess(auth.tenantId(), auth.userId())) {
      result.put("repair", new HashSet<>(Set.of("查看")));
      result.put("workspace", new HashSet<>(Set.of("查看")));
    }
    if (maintenanceDb != null
        && maintenanceDb.queryForObject(
                "SELECT COUNT(*) FROM eam_maintenance_task WHERE tenant_id=? AND"
                    + " (JSON_CONTAINS(JSON_EXTRACT(snapshot,'$.owners'),JSON_ARRAY(CAST(? AS"
                    + " CHAR))) OR"
                    + " JSON_CONTAINS(JSON_EXTRACT(snapshot,'$.reviewers'),JSON_ARRAY(CAST(? AS"
                    + " CHAR))))",
                Integer.class,
                auth.tenantId(),
                auth.userId(),
                auth.userId())
            > 0) {
      result.computeIfAbsent("maintenanceTask", k -> new HashSet<>()).add("查看");
      result.computeIfAbsent("workspace", k -> new HashSet<>()).add("查看");
    }
    if (maintenanceDb != null
        && maintenanceDb.queryForObject(
                "SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=? AND deleted=0 AND"
                    + " (JSON_CONTAINS(JSON_EXTRACT(snapshot,'$.owners'),JSON_ARRAY(CAST(? AS"
                    + " CHAR))) OR"
                    + " JSON_CONTAINS(JSON_EXTRACT(snapshot,'$.reviewers'),JSON_ARRAY(CAST(? AS"
                    + " CHAR))) OR owner_id=? OR"
                    + " JSON_UNQUOTE(JSON_EXTRACT(snapshot,'$.personMode'))='任何人可点检')",
                Integer.class,
                auth.tenantId(),
                auth.userId(),
                auth.userId(),
                auth.userId())
            > 0) {
      for (String taskKind : List.of("daily", "periodic")) {
        if (maintenanceDb.queryForObject(
                "SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=? AND deleted=0 AND"
                    + " kind=? AND"
                    + " (JSON_CONTAINS(JSON_EXTRACT(snapshot,'$.owners'),JSON_ARRAY(CAST(? AS"
                    + " CHAR))) OR"
                    + " JSON_CONTAINS(JSON_EXTRACT(snapshot,'$.reviewers'),JSON_ARRAY(CAST(? AS"
                    + " CHAR))) OR owner_id=? OR"
                    + " JSON_UNQUOTE(JSON_EXTRACT(snapshot,'$.personMode'))='任何人可点检')",
                Integer.class,
                auth.tenantId(),
                taskKind,
                auth.userId(),
                auth.userId(),
                auth.userId())
            > 0) result.computeIfAbsent(inspectionMenu(taskKind), k -> new HashSet<>()).add("查看");
      }
      result.computeIfAbsent("workspace", k -> new HashSet<>()).add("查看");
    }
    if (roleIds.isEmpty()) return result;
    for (var role :
        roles.selectList(
            new LambdaQueryWrapper<SystemRole>()
                .eq(SystemRole::getTenantId, auth.tenantId())
                .eq(SystemRole::getStatus, 1)
                .in(SystemRole::getId, roleIds))) {
      var rows =
          grants.selectList(
              new LambdaQueryWrapper<RoleMenuPermission>()
                  .eq(RoleMenuPermission::getTenantId, auth.tenantId())
                  .eq(RoleMenuPermission::getRoleId, role.getId()));
      if (!rows.isEmpty())
        for (var row : rows)
          result
              .computeIfAbsent(row.getMenuKey(), k -> new HashSet<>())
              .addAll(strings(row.getActionsJson()));
      else if (!"PER_MENU".equals(role.getDataScope())) {
        Map<String, Set<String>> legacyMenus = new LinkedHashMap<>();
        for (var raw : strings(role.getPermissionsJson())) {
          var parts = raw.split("\\.", 2);
          if (parts.length != 2) continue;
          legacyMenus
              .computeIfAbsent(parts[0], k -> new HashSet<>())
              .add(legacyActionName(parts[1]));
        }
        expandLegacyMenus(legacyMenus);
        legacyMenus.forEach(
            (menu, actions) -> result.computeIfAbsent(menu, k -> new HashSet<>()).addAll(actions));
      }
    }
    return result;
  }

  private static final Map<String, Set<String>> CHILD_ACTIONS =
      Map.ofEntries(
          Map.entry(
              "inventoryMaterial", Set.of("查看", "新增", "编辑", "删除", "导入", "导出", "查看金额", "编辑参考价")),
          Map.entry("inventoryWarehouse", Set.of("查看", "新增", "编辑", "删除", "导出")),
          Map.entry("inventoryReceipt", Set.of("查看", "新增", "编辑", "删除", "入库", "导出", "查看金额")),
          Map.entry(
              "inventoryOutbound",
              Set.of("查看", "新增直接出库", "编辑草稿", "删除草稿", "预留", "释放预留", "确认出库", "关闭剩余需求", "导出", "查看金额")),
          Map.entry("inventoryRequisition", Set.of("查看", "新增", "编辑", "删除", "出库", "导出", "查看金额")),
          Map.entry("inventoryReturn", Set.of("查看", "新增", "编辑", "删除", "入库", "导出", "查看金额")),
          Map.entry("inventoryTransfer", Set.of("查看", "新增", "编辑", "删除", "出库", "入库", "导出", "查看金额")),
          Map.entry("inventoryCount", Set.of("查看", "新增", "编辑", "删除", "出库", "入库", "导出", "查看金额")),
          Map.entry("inventoryOpening", Set.of("查看", "新增", "编辑", "删除", "入库", "导入", "导出", "查看金额")),
          Map.entry("inventoryReversal", Set.of("查看", "新增", "编辑", "删除", "冲销", "导出", "查看金额")),
          Map.entry("inventoryQuery", Set.of("查看", "导出", "冻结", "查看金额")),
          Map.entry("location", Set.of("查看", "新增", "编辑", "删除", "导出")),
          Map.entry("maintenanceType", Set.of("查看", "新增", "编辑", "删除", "导出")),
          Map.entry("maintenanceStandard", Set.of("查看", "新增", "编辑", "删除", "导出")),
          Map.entry("maintenancePlan", Set.of("查看", "新增", "编辑", "删除", "导出")),
          Map.entry("maintenanceTask", Set.of("查看", "编辑", "执行", "验收", "删除", "导出")),
          Map.entry("inspectionType", Set.of("查看", "新增", "编辑", "删除", "导出")),
          Map.entry("inspectionStandard", Set.of("查看", "新增", "编辑", "删除", "导出")),
          Map.entry("inspectionDailyPlan", Set.of("查看", "新增", "编辑", "删除", "导出")),
          Map.entry("inspectionPeriodicPlan", Set.of("查看", "新增", "编辑", "删除", "导出")),
          Map.entry("inspectionDailyTask", Set.of("查看", "执行", "审核", "转派", "删除", "导出")),
          Map.entry("inspectionPeriodicTask", Set.of("查看", "执行", "审核", "转派", "删除", "导出")));

  public static Set<String> legacyTargets(String parent) {
    var result = new HashSet<String>();
    for (String child : CHILD_ACTIONS.keySet())
      if (legacyParent(child).equals(parent)) result.add(child);
    return result;
  }

  private static String legacyParent(String menu) {
    if (menu.startsWith("inventory") && !menu.equals("inventory")) return "inventory";
    if (menu.equals("location")) return "base";
    if (menu.startsWith("maintenance")) return "maintenance";
    if (menu.startsWith("inspection")) return "inspection";
    return menu;
  }

  private void expandLegacyMenus(Map<String, Set<String>> result) {
    for (var entry : CHILD_ACTIONS.entrySet()) {
      var parentActions = result.get(legacyParent(entry.getKey()));
      if (parentActions != null) {
        var actions = new HashSet<>(parentActions);
        actions.retainAll(entry.getValue());
        if (!actions.isEmpty())
          result.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).addAll(actions);
      }
    }
    result.remove("maintenance");
    result.remove("inspection");
  }

  public boolean canMaintenanceAccept(long userId, Long department) {
    try {
      var grants = scopesFor(userId, "maintenanceTask", "验收");
      var u =
          users.selectOne(
              new LambdaQueryWrapper<SystemUser>()
                  .eq(SystemUser::getTenantId, auth.tenantId())
                  .eq(SystemUser::getId, userId)
                  .eq(SystemUser::getStatus, 1));
      if (u == null) return false;
      for (var grant : grants) {
        if (grant.scopes.contains("all")
            || grant.scopes.contains("owner")
            || grant.scopes.contains("selected") && grant.orgIds.contains(department)
            || grant.scopes.contains("department") && Objects.equals(u.getOrgId(), department))
          return true;
        if (grant.scopes.contains("company")) {
          Long company = u.getOrgId();
          Set<Long> seen = new HashSet<>();
          while (company != null && seen.add(company)) {
            var node =
                orgs.selectOne(
                    new LambdaQueryWrapper<OrgUnit>()
                        .eq(OrgUnit::getTenantId, auth.tenantId())
                        .eq(OrgUnit::getId, company));
            if (node == null) break;
            if ("公司".equals(node.getOrgType())) {
              if (companyDepartments(company).contains(department)) return true;
              break;
            }
            company = node.getParentId();
          }
        }
      }
      return false;
    } catch (ResponseStatusException e) {
      return false;
    }
  }

  public Scope maintenance(String action) {
    return maintenance("tasks", action);
  }

  public static String maintenanceMenu(String kind) {
    return switch (kind) {
      case "types" -> "maintenanceType";
      case "standards" -> "maintenanceStandard";
      case "plans" -> "maintenancePlan";
      case "tasks" -> "maintenanceTask";
      default -> throw new IllegalArgumentException("未知保养资源");
    };
  }

  public static String inspectionMenu(String kind) {
    return switch (kind) {
      case "types" -> "inspectionType";
      case "standards" -> "inspectionStandard";
      case "daily-plans" -> "inspectionDailyPlan";
      case "periodic-plans" -> "inspectionPeriodicPlan";
      case "daily", "daily-tasks" -> "inspectionDailyTask";
      case "periodic", "periodic-tasks" -> "inspectionPeriodicTask";
      default -> throw new IllegalArgumentException("未知点巡检资源");
    };
  }

  public Scope maintenance(String kind, String action) {
    return scope(maintenanceMenu(kind), action);
  }

  public Scope inspection(String kind, String action) {
    return scope(inspectionMenu(kind), action);
  }

  public boolean anyMaintenance(String action) {
    return List.of("types", "standards", "plans", "tasks").stream()
        .anyMatch(k -> allowed(maintenanceMenu(k), action));
  }

  public boolean anyInspection(String action) {
    return List.of("types", "standards", "daily-plans", "periodic-plans", "daily", "periodic")
        .stream()
        .anyMatch(k -> allowed(inspectionMenu(k), action));
  }

  public boolean maintenanceEquipmentVisible(Long company, Long department, Long owner) {
    for (String kind : List.of("standards", "plans", "tasks")) {
      if (allowed(maintenanceMenu(kind), "查看")
          && maintenance(kind, "查看").matches(company, department, owner, auth.userId()))
        return true;
    }
    return false;
  }

  public boolean inspectionEquipmentVisible(Long company, Long department, Long owner) {
    for (String kind : List.of("standards", "daily-plans", "periodic-plans", "daily", "periodic")) {
      if (allowed(inspectionMenu(kind), "查看")
          && inspection(kind, "查看").matches(company, department, owner, auth.userId())) return true;
    }
    return false;
  }

  public Scope equipment(String action) {
    return scope("equipment", action);
  }

  public Scope repair(String action) {
    return scope("repair", action);
  }

  public void filterEquipment(LambdaQueryWrapper<Equipment> query) {
    var scope = equipment("查看");
    if (scope.all()) return;
    if (scope.companyId() == null && scope.departmentIds().isEmpty() && !scope.owner()) {
      query.eq(Equipment::getId, -1L);
      return;
    }
    query.and(
        w -> {
          boolean added = false;
          if (scope.companyId() != null) {
            w.eq(Equipment::getCompanyOrgId, scope.companyId());
            added = true;
          }
          if (!scope.departmentIds().isEmpty()) {
            if (added) w.or();
            w.in(Equipment::getDepartmentOrgId, scope.departmentIds());
            added = true;
          }
          if (scope.owner()) {
            if (added) w.or();
            w.eq(Equipment::getOwnerUserId, auth.userId());
          }
        });
  }

  public void filterRepair(LambdaQueryWrapper<RepairOrder> query) {
    filterRepair(query, "查看");
  }

  public void filterRepair(LambdaQueryWrapper<RepairOrder> query, String action) {
    Scope scope;
    try {
      scope = repair(action);
    } catch (ResponseStatusException denied) {
      if (!"查看".equals(action)) throw denied;
      scope = new Scope(false, null, Set.of(), true);
    }
    if ("查看".equals(action))
      scope = new Scope(scope.all(), scope.companyId(), scope.departmentIds(), true);
    final Scope effective = scope;
    if (scope.all()) return;
    if (scope.departmentIds().isEmpty() && !scope.owner()) {
      query.eq(RepairOrder::getId, -1L);
      return;
    }
    query.and(
        w -> {
          boolean added = false;
          if (!effective.departmentIds().isEmpty()) {
            w.in(RepairOrder::getDepartmentOrgId, effective.departmentIds());
            added = true;
          }
          if (effective.owner()) {
            if (added) w.or();
            w.eq(RepairOrder::getReporterUserId, auth.userId())
                .or()
                .eq(RepairOrder::getAssigneeUserId, auth.userId())
                .or()
                .apply(
                    "id IN (SELECT order_id FROM eam_repair_candidate WHERE tenant_id={0} AND"
                        + " user_id={1} AND status='待接单')",
                    auth.tenantId(), auth.userId());
          }
        });
  }

  public void filterRepairAcceptance(LambdaQueryWrapper<RepairOrder> query) {
    Scope scope;
    try {
      scope = repair("验收");
    } catch (ResponseStatusException denied) {
      scope = new Scope(false, null, Set.of(), false);
    }
    if (scope.all()) return;
    final Set<Long> departments = scope.departmentIds();
    query.and(
        w -> {
          w.eq(RepairOrder::getReporterUserId, auth.userId());
          if (!departments.isEmpty()) w.or().in(RepairOrder::getDepartmentOrgId, departments);
        });
  }

  public void checkEquipment(Equipment e, String action) {
    var scope = equipment(action);
    if (!Objects.equals(e.getTenantId(), auth.tenantId())
        || !scope.matches(
            e.getCompanyOrgId(), e.getDepartmentOrgId(), e.getOwnerUserId(), auth.userId())) deny();
  }

  public void checkRepair(RepairOrder r, String action) {
    if (!Objects.equals(r.getTenantId(), auth.tenantId())) deny();
    if ("验收".equals(action) && Objects.equals(r.getReporterUserId(), auth.userId())) return;
    if (Set.of("查看", "处理").contains(action) && Objects.equals(r.getAssigneeUserId(), auth.userId()))
      return;
    var scope = repair(action);
    if (!Objects.equals(r.getTenantId(), auth.tenantId())
        || !scope.all()
            && !(scope.departmentIds().contains(r.getDepartmentOrgId())
                || scope.owner() && repairOwnerMatches(r, action))) deny();
  }

  private boolean repairOwnerMatches(RepairOrder r, String action) {
    if (Set.of("处理").contains(action)) return Objects.equals(r.getAssigneeUserId(), auth.userId());
    if (Set.of("验收", "报修", "编辑", "撤回", "派工").contains(action))
      return Objects.equals(r.getReporterUserId(), auth.userId());
    return Objects.equals(r.getReporterUserId(), auth.userId())
        || Objects.equals(r.getAssigneeUserId(), auth.userId());
  }

  public List<OrgUnit> visibleOrganizations(List<OrgUnit> rows) {
    var scope = scope("org", "查看");
    if (scope.all()) return rows;
    Map<Long, OrgUnit> byId = new HashMap<>();
    for (var row : rows) byId.put(row.getId(), row);
    Set<Long> visible = new HashSet<>();
    for (var row : rows)
      if (allowedOrg(scope, row)) {
        OrgUnit node = row;
        while (node != null && visible.add(node.getId()))
          node = node.getParentId() == null ? null : byId.get(node.getParentId());
      }
    return rows.stream().filter(row -> visible.contains(row.getId())).toList();
  }

  public void checkOrg(String menu, String action, OrgUnit org) {
    if (!Objects.equals(org.getTenantId(), auth.tenantId())
        || !allowedOrg(scope(menu, action), org)) deny();
  }

  public List<SystemUser> visibleUsers(List<SystemUser> rows) {
    var scope = scope("user", "查看");
    return rows.stream().filter(row -> allowedUser(scope, row)).toList();
  }

  public void checkUser(String menu, String action, SystemUser user) {
    if (!Objects.equals(user.getTenantId(), auth.tenantId())
        || !allowedUser(scope(menu, action), user)) deny();
  }

  private boolean allowedOrg(Scope scope, OrgUnit org) {
    if (scope.all()) return true;
    if ("集团".equals(org.getOrgType())) return false;
    if ("公司".equals(org.getOrgType())) return Objects.equals(org.getId(), scope.companyId());
    return scope.departmentIds().contains(org.getId())
        || scope.companyId() != null
            && Objects.equals(companyOfOrganization(org.getId()), scope.companyId());
  }

  private boolean allowedUser(Scope scope, SystemUser user) {
    if (scope.all()) return true;
    if (scope.departmentIds().contains(user.getOrgId())) return true;
    return false;
  }

  public Long companyOfOrganization(Long orgId) {
    var visited = new HashSet<Long>();
    while (orgId != null && visited.add(orgId)) {
      var org =
          orgs.selectOne(
              new LambdaQueryWrapper<OrgUnit>()
                  .eq(OrgUnit::getTenantId, auth.tenantId())
                  .eq(OrgUnit::getId, orgId));
      if (org == null) return null;
      if ("公司".equals(org.getOrgType())) return org.getId();
      orgId = org.getParentId();
    }
    return null;
  }

  public Long companyOfUser() {
    return companyOfOrganization(auth.user().getOrgId());
  }

  public Set<Long> companyDepartments(Long companyId) {
    if (companyId == null) return Set.of();
    var rows =
        orgs.selectList(
            new LambdaQueryWrapper<OrgUnit>().eq(OrgUnit::getTenantId, auth.tenantId()));
    var result = new HashSet<Long>();
    var parents = new HashSet<Long>();
    parents.add(companyId);
    boolean changed;
    do {
      changed = false;
      for (var org : rows)
        if ("部门".equals(org.getOrgType())
            && parents.contains(org.getParentId())
            && result.add(org.getId())) {
          parents.add(org.getId());
          changed = true;
        }
    } while (changed);
    return result;
  }

  public OrgUnit requireDepartment(Long id) {
    var org =
        orgs.selectOne(
            new LambdaQueryWrapper<OrgUnit>()
                .eq(OrgUnit::getTenantId, auth.tenantId())
                .eq(OrgUnit::getId, id)
                .eq(OrgUnit::getOrgType, "部门")
                .eq(OrgUnit::getStatus, 1));
    if (org == null || companyOfOrganization(id) == null)
      throw new IllegalArgumentException("所属部门不存在、已停用或未归属公司");
    return org;
  }

  public OrgUnit requireDepartment(String companyName, String departmentName) {
    var company =
        orgs.selectOne(
            new LambdaQueryWrapper<OrgUnit>()
                .eq(OrgUnit::getTenantId, auth.tenantId())
                .eq(OrgUnit::getOrgType, "公司")
                .eq(OrgUnit::getName, companyName));
    if (company == null) throw new IllegalArgumentException("所属公司不在当前租户内");
    var matches =
        orgs
            .selectList(
                new LambdaQueryWrapper<OrgUnit>()
                    .eq(OrgUnit::getTenantId, auth.tenantId())
                    .eq(OrgUnit::getOrgType, "部门")
                    .eq(OrgUnit::getName, departmentName))
            .stream()
            .filter(d -> Objects.equals(companyOfOrganization(d.getId()), company.getId()))
            .toList();
    if (matches.size() != 1) throw new IllegalArgumentException("请通过所属部门选择框选择唯一的部门");
    var department = matches.getFirst();
    return department;
  }

  public Long userIdByName(String name) {
    if (name == null || name.isBlank()) return null;
    var matches =
        users.selectList(
            new LambdaQueryWrapper<SystemUser>()
                .eq(SystemUser::getTenantId, auth.tenantId())
                .eq(SystemUser::getDisplayName, name.trim()));
    if (matches.size() > 1) throw new IllegalArgumentException("该姓名对应多个员工，请选择唯一的负责人");
    return matches.isEmpty() ? null : matches.getFirst().getId();
  }

  public Scope scope(String menu, String action) {
    var values = scopes(menu, action);
    boolean all = values.stream().anyMatch(x -> x.scopes.contains("all"));
    boolean owner = values.stream().anyMatch(x -> x.scopes.contains("owner"));
    Long company = companyOfUser();
    Set<Long> departments = new HashSet<>();
    for (var grant : values) {
      if (grant.scopes.contains("department")) departments.add(auth.user().getOrgId());
      if (grant.scopes.contains("selected")) departments.addAll(grant.orgIds);
      if (grant.scopes.contains("company")) departments.addAll(companyDepartments(company));
    }
    return new Scope(
        all,
        values.stream().anyMatch(x -> x.scopes.contains("company")) ? company : null,
        departments,
        owner);
  }

  private record Grant(Set<String> actions, Set<String> scopes, Set<Long> orgIds) {}

  private List<Grant> scopes(String menu, String action) {
    return scopesFor(auth.userId(), menu, action);
  }

  private List<Grant> scopesFor(long userId, String menu, String action) {
    var roleIds =
        links
            .selectList(
                new QueryWrapper<UserRoleLink>()
                    .eq("tenant_id", auth.tenantId())
                    .eq("user_id", userId))
            .stream()
            .map(UserRoleLink::getRoleId)
            .toList();
    if (roleIds.isEmpty()) deny();
    var matches = new ArrayList<Grant>();
    for (var role :
        roles.selectList(
            new LambdaQueryWrapper<SystemRole>()
                .eq(SystemRole::getTenantId, auth.tenantId())
                .eq(SystemRole::getStatus, 1)
                .in(SystemRole::getId, roleIds))) {
      var rows =
          grants.selectList(
              new LambdaQueryWrapper<RoleMenuPermission>()
                  .eq(RoleMenuPermission::getTenantId, auth.tenantId())
                  .eq(RoleMenuPermission::getRoleId, role.getId())
                  .eq(RoleMenuPermission::getMenuKey, menu));
      if (!rows.isEmpty())
        for (var row : rows) {
          var grant =
              new Grant(
                  strings(row.getActionsJson()),
                  strings(row.getDataScopesJson()),
                  longs(row.getOrgIdsJson()));
          if (grant.actions.contains(action)) matches.add(grant);
        }
      else if (!"PER_MENU".equals(role.getDataScope())) {
        var legacy = strings(role.getPermissionsJson());
        if (legacy.contains(menu + "." + action)
            || legacy.contains(menu + "." + legacyAction(action))
            || (CHILD_ACTIONS.getOrDefault(menu, Set.of()).contains(action)
                && (legacy.contains(legacyParent(menu) + "." + action)
                    || legacy.contains(legacyParent(menu) + "." + legacyAction(action)))))
          matches.add(
              new Grant(
                  Set.of(action),
                  Set.of(legacyScope(role.getDataScope())),
                  longs(role.getOrgIdsJson())));
      }
    }
    if (matches.isEmpty()) deny();
    return matches;
  }

  /** Evaluate the recipient's own roles and data scope without changing the request identity. */
  public boolean canAccept(SystemUser user, RepairOrder row) {
    if (!Objects.equals(user.getTenantId(), row.getTenantId())
        || !Integer.valueOf(1).equals(user.getStatus())) return false;
    if (Objects.equals(user.getId(), row.getReporterUserId())) return true;
    try {
      var values = scopesFor(user.getId(), "repair", "验收");
      var dept =
          orgs.selectOne(
              new LambdaQueryWrapper<OrgUnit>()
                  .eq(OrgUnit::getTenantId, auth.tenantId())
                  .eq(OrgUnit::getId, user.getOrgId()));
      for (var g : values)
        if (g.scopes.contains("all")
            || g.scopes.contains("department")
                && Objects.equals(user.getOrgId(), row.getDepartmentOrgId())
            || g.scopes.contains("selected") && g.orgIds.contains(row.getDepartmentOrgId())
            || g.scopes.contains("company")
                && dept != null
                && companyDepartments(dept.getParentId()).contains(row.getDepartmentOrgId()))
          return true;
      return false;
    } catch (ResponseStatusException denied) {
      return false;
    }
  }

  public boolean canIssue(SystemUser user, long companyId) {
    if (!Objects.equals(user.getTenantId(), auth.tenantId())
        || !Integer.valueOf(1).equals(user.getStatus())) return false;
    try {
      var values = scopesFor(user.getId(), "inventory", "出库");
      var dept =
          orgs.selectOne(
              new LambdaQueryWrapper<OrgUnit>()
                  .eq(OrgUnit::getTenantId, auth.tenantId())
                  .eq(OrgUnit::getId, user.getOrgId()));
      return values.stream()
          .anyMatch(
              g ->
                  g.scopes.contains("all")
                      || dept != null && Objects.equals(dept.getParentId(), companyId));
    } catch (ResponseStatusException denied) {
      return false;
    }
  }

  public boolean inventoryWarehouseAllowed(String menu, long warehouse, String action) {
    try {
      checkInventoryWarehouse(menu, warehouse, action);
      return true;
    } catch (ResponseStatusException e) {
      if (e.getStatusCode().value() != 403) throw e;
      return false;
    }
  }

  public boolean inventoryWarehouseUser(long userId, String menu, long warehouse, String action) {
    var users =
        maintenanceDb.queryForList(
            "SELECT org_id,status FROM eam_sys_user WHERE tenant_id=? AND id=? AND deleted=0",
            auth.tenantId(),
            userId);
    var warehouses =
        maintenanceDb.queryForList(
            "SELECT company_id FROM inv_warehouse WHERE tenant_id=? AND id=?",
            auth.tenantId(),
            warehouse);
    if (users.size() != 1
        || warehouses.size() != 1
        || ((Number) users.getFirst().get("status")).intValue() != 1) return false;
    Long company = null;
    Object orgId = users.getFirst().get("org_id");
    Set<Long> seen = new HashSet<>();
    while (orgId != null && seen.add(((Number) orgId).longValue())) {
      var org =
          maintenanceDb.queryForList(
              "SELECT id,parent_id,org_type FROM eam_org_unit WHERE tenant_id=? AND id=?",
              auth.tenantId(),
              orgId);
      if (org.isEmpty()) break;
      if ("公司".equals(org.getFirst().get("org_type"))
          || "company".equals(org.getFirst().get("org_type"))) {
        company = ((Number) org.getFirst().get("id")).longValue();
        break;
      }
      orgId = org.getFirst().get("parent_id");
    }
    long sourceCompany = ((Number) warehouses.getFirst().get("company_id")).longValue();
    try {
      for (var grant : scopesFor(userId, menu, action))
        if (grant.scopes.contains("all")
            || grant.scopes.contains("company") && Objects.equals(company, sourceCompany)
            || grant.scopes.contains("warehouse") && grant.orgIds.contains(warehouse)) return true;
    } catch (ResponseStatusException denied) {
      return false;
    }
    return false;
  }

  public void checkInventoryWarehouse(String menu, long warehouseId, String action) {
    var w =
        maintenanceDb.queryForList(
            "SELECT company_id FROM inv_warehouse WHERE tenant_id=? AND id=?",
            auth.tenantId(),
            warehouseId);
    if (w.size() != 1) deny();
    long company = ((Number) w.getFirst().get("company_id")).longValue();
    for (var grant : scopes(menu, action))
      if (grant.scopes.contains("all")
          || grant.scopes.contains("company") && Objects.equals(companyOfUser(), company)
          || grant.scopes.contains("warehouse") && grant.orgIds.contains(warehouseId)) return;
    deny();
  }

  public void checkInventoryCompany(long companyId, String action) {
    var scope = scope("inventory", action);
    if (!scope.all() && !Objects.equals(companyOfUser(), companyId)) deny();
  }

  private String legacyScope(String value) {
    return switch (value) {
      case "全部数据" -> "all";
      case "本公司及下级" -> "company";
      case "指定部门" -> "selected";
      case "本人" -> "owner";
      default -> "department";
    };
  }

  private String legacyAction(String action) {
    return switch (action) {
      case "查看" -> "view";
      case "新增", "报修", "申请" -> "create";
      case "编辑" -> "edit";
      case "删除" -> "delete";
      case "停用" -> "disable";
      case "派工" -> "dispatch";
      case "处理" -> "process";
      case "验收" -> "accept";
      case "导出" -> "export";
      default -> action;
    };
  }

  private String legacyActionName(String action) {
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

  private Set<String> strings(String raw) {
    try {
      return new HashSet<>(json.readValue(raw, new TypeReference<List<String>>() {}));
    } catch (Exception e) {
      return Set.of();
    }
  }

  private Set<Long> longs(String raw) {
    try {
      return new HashSet<>(json.readValue(raw, new TypeReference<List<Long>>() {}));
    } catch (Exception e) {
      return Set.of();
    }
  }

  private static void deny() {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有该操作或数据访问权限");
  }
}
