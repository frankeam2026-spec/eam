package com.windasoft.eam.service.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.domain.system.PermissionMenu;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.inventory.Warehouse;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.entity.system.SystemRole;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.entity.system.UserRoleLink;
import com.windasoft.eam.repository.inventory.WarehouseRepository;
import com.windasoft.eam.repository.system.AuthorizationQueryPredicates;
import com.windasoft.eam.repository.system.AuthorizationRepository;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 统一权限入口：综合角色动作授权、组织数据范围和业务参与资格，服务端操作必须再次校验。 */
@Service
public class PermissionService {
  private static final Set<String> REPAIR_REPORTER_ACTIONS =
      PermissionAction.codes(
          PermissionAction.ACCEPT, PermissionAction.REPORT, PermissionAction.EDIT,
          PermissionAction.WITHDRAW, PermissionAction.DISPATCH);

  private final AuthorizationRepository repository;
  private final AuthContext auth;
  private final WarehouseRepository warehouseRepository;
  private final OrganizationScopeService organizationScopes;

  public PermissionService(
      AuthContext auth,
      WarehouseRepository warehouseRepository,
      AuthorizationRepository repository,
      OrganizationScopeService organizationScopes) {
    this.auth = auth;
    this.warehouseRepository = warehouseRepository;
    this.repository = repository;
    this.organizationScopes = organizationScopes;
  }

  /** 保留字符串入口以兼容数据库授权、动态菜单和已有调用方； 领域代码优先通过类型安全重载避免拼错动作或菜单键。 */
  public void require(String menu, String action) {
    scopes(menu, action);
  }

  public void require(String menu, PermissionAction action) {
    require(menu, action.code());
  }

  public void require(PermissionMenu menu, PermissionAction action) {
    require(menu.code(), action.code());
  }

  public boolean allowed(String menu, PermissionAction action) {
    return allowed(menu, action.code());
  }

  public boolean allowed(PermissionMenu menu, PermissionAction action) {
    return allowed(menu.code(), action.code());
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
        repository.userRoles(auth.tenantId(), auth.userId()).stream()
            .map(UserRoleLink::getRoleId)
            .toList();
    Map<String, Set<String>> result = new LinkedHashMap<>();
    if (repository.approvalParticipation(auth.tenantId(), auth.userId())) {
      result.put("inventoryRequisition", new HashSet<>(Set.of(PermissionAction.VIEW.code())));
      result.put("workspace", new HashSet<>(Set.of(PermissionAction.VIEW.code())));
    }
    if (repository.personalRepairAccess(auth.tenantId(), auth.userId())) {
      result.put("repair", new HashSet<>(Set.of(PermissionAction.VIEW.code())));
      result.put("workspace", new HashSet<>(Set.of(PermissionAction.VIEW.code())));
    }
    if (repository.maintenanceParticipation(auth.tenantId(), auth.userId()) > 0) {
      result.computeIfAbsent("maintenanceTask", k -> new HashSet<>()).add(PermissionAction.VIEW.code());
      result.computeIfAbsent("workspace", k -> new HashSet<>()).add(PermissionAction.VIEW.code());
    }
    if (repository.inspectionParticipation(auth.tenantId(), auth.userId(), null) > 0) {
      for (String taskKind : List.of("daily", "periodic")) {
        if (repository.inspectionParticipation(auth.tenantId(), auth.userId(), taskKind) > 0)
          result.computeIfAbsent(inspectionMenu(taskKind), k -> new HashSet<>()).add(PermissionAction.VIEW.code());
      }
      result.computeIfAbsent("workspace", k -> new HashSet<>()).add(PermissionAction.VIEW.code());
    }
    if (repository.repairPlanParticipation(auth.tenantId(), auth.userId()) > 0) {
      result.computeIfAbsent("repairPlan", k -> new HashSet<>()).add(PermissionAction.VIEW.code());
      result.computeIfAbsent("workspace", k -> new HashSet<>()).add(PermissionAction.VIEW.code());
    }
    if (roleIds.isEmpty()) return result;
    var activeRoles = repository.activeRoles(auth.tenantId(), roleIds);
    var grantsByRole =
        repository.grantsForRoles(
            auth.tenantId(), activeRoles.stream().map(SystemRole::getId).toList(), null);
    for (var role : activeRoles) {
      var rows = grantsByRole.getOrDefault(role.getId(), List.of());
      if (!rows.isEmpty())
        for (var row : rows)
          result
              .computeIfAbsent(row.getMenuKey(), k -> new HashSet<>())
              .addAll(new LinkedHashSet<>(row.getActions()));
      else if (!"PER_MENU".equals(role.getDataScope())) {
        Map<String, Set<String>> legacyMenus = new LinkedHashMap<>();
        for (var raw : new LinkedHashSet<>(role.getLegacyActions())) {
          var parts = raw.split("\\.", 2);
          if (parts.length != 2) continue;
          legacyMenus
              .computeIfAbsent(parts[0], k -> new HashSet<>())
              .add(PermissionAction.fromLegacyCode(parts[1]));
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
              "inventoryMaterial", PermissionAction.codes(
                  PermissionAction.VIEW,
                  PermissionAction.CREATE,
                  PermissionAction.EDIT,
                  PermissionAction.DELETE,
                  PermissionAction.IMPORT,
                  PermissionAction.EXPORT,
                  PermissionAction.VIEW_AMOUNT,
                  PermissionAction.EDIT_REFERENCE_PRICE)),
          Map.entry("inventoryWarehouse", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.EXPORT)),
          Map.entry("inventoryReceipt", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.STOCK_IN,
              PermissionAction.EXPORT,
              PermissionAction.VIEW_AMOUNT)),
          Map.entry(
              "inventoryOutbound",
              PermissionAction.codes(
                  PermissionAction.VIEW,
                  PermissionAction.CREATE_DIRECT_OUTBOUND,
                  PermissionAction.EDIT_DRAFT,
                  PermissionAction.DELETE_DRAFT,
                  PermissionAction.RESERVE,
                  PermissionAction.RELEASE_RESERVATION,
                  PermissionAction.CONFIRM_ISSUE,
                  PermissionAction.CLOSE_REMAINDER,
                  PermissionAction.EXPORT,
                  PermissionAction.VIEW_AMOUNT)),
          Map.entry("inventoryRequisition", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.STOCK_OUT,
              PermissionAction.EXPORT,
              PermissionAction.VIEW_AMOUNT)),
          Map.entry("inventoryReturn", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.STOCK_IN,
              PermissionAction.EXPORT,
              PermissionAction.VIEW_AMOUNT)),
          Map.entry("inventoryTransfer", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.STOCK_OUT,
              PermissionAction.STOCK_IN,
              PermissionAction.EXPORT,
              PermissionAction.VIEW_AMOUNT)),
          Map.entry("inventoryCount", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.STOCK_OUT,
              PermissionAction.STOCK_IN,
              PermissionAction.EXPORT,
              PermissionAction.VIEW_AMOUNT)),
          Map.entry("inventoryOpening", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.STOCK_IN,
              PermissionAction.IMPORT,
              PermissionAction.EXPORT,
              PermissionAction.VIEW_AMOUNT)),
          Map.entry("inventoryReversal", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.REVERSE,
              PermissionAction.EXPORT,
              PermissionAction.VIEW_AMOUNT)),
          Map.entry("inventoryQuery", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.EXPORT,
              PermissionAction.FREEZE,
              PermissionAction.VIEW_AMOUNT)),
          Map.entry("location", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.EXPORT)),
          Map.entry("maintenanceType", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.EXPORT)),
          Map.entry("maintenanceStandard", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.EXPORT)),
          Map.entry("maintenancePlan", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.EXPORT)),
          Map.entry("maintenanceTask", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.EDIT,
              PermissionAction.EXECUTE,
              PermissionAction.ACCEPT,
              PermissionAction.DELETE,
              PermissionAction.EXPORT)),
          Map.entry("inspectionType", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.EXPORT)),
          Map.entry("inspectionStandard", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.EXPORT)),
          Map.entry("inspectionDailyPlan", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.EXPORT)),
          Map.entry("inspectionPeriodicPlan", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.CREATE,
              PermissionAction.EDIT,
              PermissionAction.DELETE,
              PermissionAction.EXPORT)),
          Map.entry(
              "inspectionDailyTask", PermissionAction.codes(
                  PermissionAction.VIEW,
                  PermissionAction.EXECUTE,
                  PermissionAction.AMEND_RESULT,
                  PermissionAction.REVIEW,
                  PermissionAction.TRANSFER,
                  PermissionAction.EXEMPT,
                  PermissionAction.DELETE,
                  PermissionAction.EXPORT)),
          Map.entry("inspectionPeriodicTask", PermissionAction.codes(
              PermissionAction.VIEW,
              PermissionAction.EXECUTE,
              PermissionAction.REVIEW,
              PermissionAction.TRANSFER,
              PermissionAction.DELETE,
              PermissionAction.EXPORT)));

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
      var grants = scopesFor(userId, "maintenanceTask", PermissionAction.ACCEPT.code());
      var u = repository.activeUser(auth.tenantId(), userId);
      if (u == null) return false;
      var s = scopeFor(u.getOrgId(), grants);
      return s.matches(companyOfOrganization(department), department, null, userId);
    } catch (ResponseStatusException e) {
      return false;
    }
  }

  public PermissionScope maintenance(PermissionAction action) {
    return maintenance(action.code());
  }

  public PermissionScope maintenance(String action) {
    return maintenance("tasks", action);
  }

  public static String maintenanceMenu(String kind) {
    return switch (kind) {
      case "types" -> "maintenanceType";
      case "standards" -> "maintenanceStandard";
      case "plans" -> "maintenancePlan";
      case "tasks" -> "maintenanceTask";
      default -> throw new LocalizedArgumentException("server.system.message0045");
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
      default -> throw new LocalizedArgumentException("server.system.message0046");
    };
  }

  public PermissionScope maintenance(String kind, PermissionAction action) {
    return maintenance(kind, action.code());
  }

  public PermissionScope maintenance(String kind, String action) {
    return scope(maintenanceMenu(kind), action);
  }

  public PermissionScope inspection(String kind, PermissionAction action) {
    return inspection(kind, action.code());
  }

  public PermissionScope inspection(String kind, String action) {
    return scope(inspectionMenu(kind), action);
  }

  public boolean anyMaintenance(PermissionAction action) {
    return anyMaintenance(action.code());
  }

  public boolean anyMaintenance(String action) {
    return List.of("types", "standards", "plans", "tasks").stream()
        .anyMatch(k -> allowed(maintenanceMenu(k), action));
  }

  public boolean anyInspection(PermissionAction action) {
    return anyInspection(action.code());
  }

  public boolean anyInspection(String action) {
    return List.of("types", "standards", "daily-plans", "periodic-plans", "daily", "periodic")
        .stream()
        .anyMatch(k -> allowed(inspectionMenu(k), action));
  }

  public boolean maintenanceEquipmentVisible(Long company, Long department, Long owner) {
    for (String kind : List.of("standards", "plans", "tasks")) {
      if (allowed(maintenanceMenu(kind), PermissionAction.VIEW)
          && maintenance(kind, PermissionAction.VIEW).matches(company, department, owner, auth.userId()))
        return true;
    }
    return false;
  }

  public boolean inspectionEquipmentVisible(Long company, Long department, Long owner) {
    for (String kind : List.of("standards", "daily-plans", "periodic-plans", "daily", "periodic")) {
      if (allowed(inspectionMenu(kind), PermissionAction.VIEW)
          && inspection(kind, PermissionAction.VIEW).matches(company, department, owner, auth.userId())) return true;
    }
    return false;
  }

  public PermissionScope equipment(PermissionAction action) {
    return equipment(action.code());
  }

  public PermissionScope equipment(String action) {
    return scope("equipment", action);
  }

  public PermissionScope repair(String action) {
    return scope("repair", action);
  }

  public void filterEquipment(LambdaQueryWrapper<Equipment> query) {
    filterEquipment(query, PermissionAction.VIEW.code());
  }

  public void filterEquipment(LambdaQueryWrapper<Equipment> query, String action) {
    var scope = equipment(action);
    AuthorizationQueryPredicates.equipment(query, scope, auth.tenantId(), auth.userId());
  }

  public void filterRepair(LambdaQueryWrapper<RepairOrder> query) {
    filterRepair(query, PermissionAction.VIEW.code());
  }

  public void filterRepair(LambdaQueryWrapper<RepairOrder> query, String action) {
    PermissionScope scope;
    try {
      scope = repair(action);
    } catch (ResponseStatusException denied) {
      if (!PermissionAction.VIEW.code().equals(action)) throw denied;
      scope = new PermissionScope(false, null, Set.of(), true);
    }
    if (PermissionAction.VIEW.code().equals(action))
      scope =
          new PermissionScope(
              scope.all(), scope.companyId(), scope.departmentIds(), true, scope.companyIds());
    AuthorizationQueryPredicates.repair(query, scope, auth.tenantId(), auth.userId());
  }

  public void filterRepairAcceptance(LambdaQueryWrapper<RepairOrder> query) {
    PermissionScope scope;
    try {
      scope = repair(PermissionAction.ACCEPT.code());
    } catch (ResponseStatusException denied) {
      scope = new PermissionScope(false, null, Set.of(), false);
    }
    AuthorizationQueryPredicates.acceptance(query, scope, auth.userId());
  }

  public void checkEquipment(Equipment e, PermissionAction action) {
    checkEquipment(e, action.code());
  }

  public void checkEquipment(Equipment e, String action) {
    var scope = equipment(action);
    boolean responsible =
        e.getOwnerIds() != null
            ? e.getOwnerIds().contains(auth.userId())
            : e.getId() != null
                && repository.equipmentResponsibility(auth.tenantId(), e.getId(), auth.userId())
                    > 0;
    if (!Objects.equals(e.getTenantId(), auth.tenantId())
        || !(scope.matches(
                e.getCompanyOrgId(), e.getDepartmentOrgId(), e.getOwnerUserId(), auth.userId())
            || scope.owner() && responsible)) deny();
  }

  public boolean equipmentDepartmentAllowed(Long departmentId, String action) {
    var s = equipment(action);
    Long company = companyOfOrganization(departmentId);
    return s.all()
        || s.companyIds().contains(company)
        || s.departmentIds().contains(departmentId)
        || s.owner() && Objects.equals(company, companyOfOrganization(auth.user().getOrgId()));
  }

  public void checkEquipmentDepartment(Long departmentId, String action) {
    if (!equipmentDepartmentAllowed(departmentId, action)) deny();
  }

  public void checkRepair(RepairOrder r, PermissionAction action) {
    checkRepair(r, action.code());
  }

  public void checkRepair(RepairOrder r, String action) {
    if (!Objects.equals(r.getTenantId(), auth.tenantId())) deny();
    if (PermissionAction.ACCEPT.code().equals(action)
        && Objects.equals(r.getReporterUserId(), auth.userId())) return;
    if ((PermissionAction.VIEW.code().equals(action) || PermissionAction.PROCESS.code().equals(action)) && Objects.equals(r.getAssigneeUserId(), auth.userId()))
      return;
    var scope = repair(action);
    if (!Objects.equals(r.getTenantId(), auth.tenantId())
        || !scope.all()
            && !(scope.departmentIds().contains(r.getDepartmentOrgId())
                || scope.owner() && repairOwnerMatches(r, action))) deny();
  }

  private boolean repairOwnerMatches(RepairOrder r, String action) {
    if (PermissionAction.PROCESS.code().equals(action))
      return Objects.equals(r.getAssigneeUserId(), auth.userId());
    if (REPAIR_REPORTER_ACTIONS.contains(action))
      return Objects.equals(r.getReporterUserId(), auth.userId());
    return Objects.equals(r.getReporterUserId(), auth.userId())
        || Objects.equals(r.getAssigneeUserId(), auth.userId());
  }

  public List<OrgUnit> visibleOrganizations(List<OrgUnit> rows) {
    var scope = scope("org", PermissionAction.VIEW);
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
    var scope = scope("user", PermissionAction.VIEW);
    return rows.stream().filter(row -> allowedUser(scope, row)).toList();
  }

  public void checkUser(String menu, String action, SystemUser user) {
    if (!Objects.equals(user.getTenantId(), auth.tenantId())
        || !allowedUser(scope(menu, action), user)) deny();
  }

  private boolean allowedOrg(PermissionScope scope, OrgUnit org) {
    return scope.all()
        || scope.departmentIds().contains(org.getId())
        || scope.companyIds().contains(org.getId())
        || companyOfOrganization(org.getId()) != null
            && scope.companyIds().contains(companyOfOrganization(org.getId()));
  }

  private boolean allowedUser(PermissionScope scope, SystemUser user) {
    return scope.all()
        || scope.departmentIds().contains(user.getOrgId())
        || companyOfOrganization(user.getOrgId()) != null
            && scope.companyIds().contains(companyOfOrganization(user.getOrgId()));
  }

  public Long companyOfOrganization(Long orgId) {
    return organizationScopes.companyOfOrganization(orgId);
  }

  public Long companyOfOrganization(long tenant, Long orgId) {
    return organizationScopes.companyOfOrganization(tenant, orgId);
  }

  public Long companyOfUser() {
    return organizationScopes.companyOfUser();
  }

  public Set<Long> companyDepartments(Long id) {
    return organizationScopes.companyDepartments(id);
  }

  public OrgUnit requireDepartment(Long id) {
    return organizationScopes.requireDepartment(id);
  }

  public OrgUnit requireDepartment(String company, String department) {
    return organizationScopes.requireDepartment(company, department);
  }

  public Long userIdByName(String name) {
    return organizationScopes.userIdByName(name);
  }

  public PermissionScope scope(String menu, PermissionAction action) {
    return scope(menu, action.code());
  }

  public PermissionScope scope(PermissionMenu menu, PermissionAction action) {
    return scope(menu.code(), action.code());
  }

  public PermissionScope scope(String menu, String action) {
    return scopeFor(auth.user().getOrgId(), scopes(menu, action));
  }

  private PermissionScope scopeFor(Long userOrg, List<Grant> values) {
    boolean all = values.stream().anyMatch(x -> x.scopes.contains("all"));
    boolean owner = values.stream().anyMatch(x -> x.scopes.contains("owner"));
    Long company = companyOfOrganization(userOrg);
    Set<Long> companies = new HashSet<>();
    Set<Long> departments = new HashSet<>();
    for (var grant : values) {
      if (grant.scopes.contains("department") && userOrg != null) departments.add(userOrg);
      if (grant.scopes.contains("departmentTree")) departments.addAll(departmentTree(userOrg));
      if (grant.scopes.contains("selected")) departments.addAll(grant.orgIds);
      if (grant.scopes.contains("company") && company != null) companies.add(company);
      if (grant.scopes.contains("companyTree")) companies.addAll(companyTree(company));
      if (grant.scopes.contains("companies")) companies.addAll(grant.orgIds);
    }
    for (Long id : companies) {
      departments.add(id); // users can belong directly to a company
      departments.addAll(companyDepartments(id));
    }
    return new PermissionScope(
        all,
        companies.size() == 1 ? companies.iterator().next() : null,
        departments,
        owner,
        companies);
  }

  public Set<Long> companyTree(Long root) {
    return organizationScopes.companyTree(root);
  }

  public Set<Long> departmentTree(Long root) {
    return organizationScopes.departmentTree(root);
  }

  public boolean companyAllowed(String menu, String action, Long company) {
    var s = scope(menu, action);
    return s.all() || company != null && s.companyIds().contains(company);
  }

  private record Grant(Set<String> actions, Set<String> scopes, Set<Long> orgIds) {}

  private List<Grant> scopes(String menu, String action) {
    return scopesFor(auth.userId(), menu, action);
  }

  /** 分别读取当前用户的有效角色授权，并合并匹配动作的数据范围；不能用宽读权限替代窄写权限。 */
  private List<Grant> scopesFor(long userId, String menu, String action) {
    var roleIds =
        repository.userRoles(auth.tenantId(), userId).stream()
            .map(UserRoleLink::getRoleId)
            .toList();
    if (roleIds.isEmpty()) deny();
    var matches = new ArrayList<Grant>();
    var activeRoles = repository.activeRoles(auth.tenantId(), roleIds);
    var grantsByRole =
        repository.grantsForRoles(
            auth.tenantId(), activeRoles.stream().map(SystemRole::getId).toList(), menu);
    for (var role : activeRoles) {
      var rows = grantsByRole.getOrDefault(role.getId(), List.of());
      if (!rows.isEmpty())
        for (var row : rows) {
          var grant =
              new Grant(
                  new LinkedHashSet<>(row.getActions()),
                  new LinkedHashSet<>(row.getDataScopes()),
                  new LinkedHashSet<>(row.getTargetIds()));
          if (grant.actions.contains(action)) matches.add(grant);
        }
      else if (!"PER_MENU".equals(role.getDataScope())) {
        var legacy = new LinkedHashSet<>(role.getLegacyActions());
        if (legacy.contains(menu + "." + action)
            || legacy.contains(menu + "." + PermissionAction.toLegacyCode(action))
            || (CHILD_ACTIONS.getOrDefault(menu, Set.of()).contains(action)
                && (legacy.contains(legacyParent(menu) + "." + action)
                    || legacy.contains(legacyParent(menu) + "." + PermissionAction.toLegacyCode(action)))))
          matches.add(
              new Grant(
                  Set.of(action),
                  Set.of(legacyScope(role.getDataScope())),
                  new LinkedHashSet<>(role.getOrganizationIds())));
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
      var values = scopesFor(user.getId(), "repair", PermissionAction.ACCEPT.code());
      return scopeFor(user.getOrgId(), values)
          .matches(
              companyOfOrganization(row.getDepartmentOrgId()), row.getDepartmentOrgId(),
              row.getReporterUserId(), user.getId());
    } catch (ResponseStatusException denied) {
      return false;
    }
  }

  public boolean canIssue(SystemUser user, long companyId) {
    if (!Objects.equals(user.getTenantId(), auth.tenantId())
        || !Integer.valueOf(1).equals(user.getStatus())) return false;
    try {
      var values = scopesFor(user.getId(), "inventory", PermissionAction.STOCK_OUT.code());
      var scope = scopeFor(user.getOrgId(), values);
      return scope.all() || scope.companyIds().contains(companyId);
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

  /** 一次解析动作授权，再与同租户的真实仓库匹配；导出和金额权限仍分别计算。 */
  public Set<Long> inventoryWarehouses(
      String menu, PermissionAction action, Collection<Warehouse> candidates) {
    var ids = new HashSet<Long>();
    if (candidates.isEmpty()) return ids;
    try {
      var scope = warehouseScope(auth.user().getOrgId(), scopes(menu, action.code()));
      long tenant = auth.tenantId();
      for (var row : candidates) {
        if (Objects.equals(row.getTenantId(), tenant) && scope.matches(row)) ids.add(row.getId());
      }
      return ids;
    } catch (ResponseStatusException e) {
      if (e.getStatusCode().value() != 403) throw e;
      return ids;
    }
  }

  public Set<Long> inventoryWarehouses(String menu, PermissionAction action) {
    return inventoryWarehouses(menu, action, warehouseRepository.scopeRows(auth.tenantId()));
  }

  public boolean inventoryWarehouseUser(long userId, String menu, long warehouse, String action) {
    var users = repository.userOrganization(auth.tenantId(), userId);
    var row = warehouseRepository.scopeRow(auth.tenantId(), warehouse);
    if (users.size() != 1
        || row == null
        || ((Number) users.getFirst().get("status")).intValue() != 1) return false;
    var orgId = (Number) users.getFirst().get("org_id");
    try {
      return warehouseScope(
              orgId == null ? null : orgId.longValue(), scopesFor(userId, menu, action))
          .matches(row);
    } catch (ResponseStatusException e) {
      if (e.getStatusCode().value() != 403) throw e;
      return false;
    }
  }

  public void checkInventoryWarehouse(String menu, long warehouseId, String action) {
    var row = warehouseRepository.scopeRow(auth.tenantId(), warehouseId);
    if (row == null || !warehouseScope(auth.user().getOrgId(), scopes(menu, action)).matches(row))
      deny();
  }

  private record WarehouseScope(boolean all, Set<Long> companies, Set<Long> warehouses) {
    boolean matches(Warehouse row) {
      return all || companies.contains(row.getCompanyId()) || warehouses.contains(row.getId());
    }
  }

  /** 仓库授权只使用公司和显式仓库范围，部门或本人授权不能扩大到整仓。 */
  private WarehouseScope warehouseScope(Long userOrg, List<Grant> grants) {
    if (grants.stream().anyMatch(grant -> grant.scopes.contains("all"))) {
      return new WarehouseScope(true, Set.of(), Set.of());
    }
    boolean currentCompany = false;
    boolean childCompanies = false;
    var companies = new HashSet<Long>();
    var warehouses = new HashSet<Long>();
    for (var grant : grants) {
      currentCompany |= grant.scopes.contains("company");
      childCompanies |= grant.scopes.contains("companyTree");
      if (grant.scopes.contains("companies")) companies.addAll(grant.orgIds);
      if (grant.scopes.contains("warehouse")) warehouses.addAll(grant.orgIds);
    }
    if (currentCompany || childCompanies) {
      var company = companyOfOrganization(userOrg);
      if (currentCompany && company != null) companies.add(company);
      if (childCompanies) companies.addAll(companyTree(company));
    }
    return new WarehouseScope(false, companies, warehouses);
  }

  public void checkInventoryCompany(long companyId, String action) {
    var scope = scope("inventory", action);
    if (!scope.all() && !scope.companyIds().contains(companyId)) deny();
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

  private static void deny() {
    throw new LocalizedStatusException(HttpStatus.FORBIDDEN, "server.system.message0047");
  }
}
