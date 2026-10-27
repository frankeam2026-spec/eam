package com.windasoft.eam.service.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.entity.system.UserRoleLink;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PermissionService {
  private final com.windasoft.eam.repository.system.AuthorizationRepository repository;
  private final AuthContext auth;
  private final com.windasoft.eam.service.system.OrganizationScopeService organizationScopes;
  private final ObjectMapper json;

  public PermissionService(
      AuthContext auth,
      com.windasoft.eam.repository.system.AuthorizationRepository repository,
      ObjectMapper json,
      com.windasoft.eam.service.system.OrganizationScopeService organizationScopes) {
    this.auth = auth;
    this.repository = repository;
    this.json = json;
    this.organizationScopes = organizationScopes;
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
        repository.userRoles(auth.tenantId(), auth.userId()).stream()
            .map(UserRoleLink::getRoleId)
            .toList();
    Map<String, Set<String>> result = new LinkedHashMap<>();
    if(repository.approvalParticipation(auth.tenantId(),auth.userId())) {
      result.put("inventoryRequisition",new HashSet<>(Set.of("查看")));
      result.put("workspace",new HashSet<>(Set.of("查看")));
    }
    if (repository.hasWorkflow()
        && repository.personalRepairAccess(auth.tenantId(), auth.userId())) {
      result.put("repair", new HashSet<>(Set.of("查看")));
      result.put("workspace", new HashSet<>(Set.of("查看")));
    }
    if (repository.hasTaskDatabase()
        && repository.maintenanceParticipation(auth.tenantId(), auth.userId()) > 0) {
      result.computeIfAbsent("maintenanceTask", k -> new HashSet<>()).add("查看");
      result.computeIfAbsent("workspace", k -> new HashSet<>()).add("查看");
    }
    if (repository.hasTaskDatabase()
        && repository.inspectionParticipation(auth.tenantId(), auth.userId(), null) > 0) {
      for (String taskKind : List.of("daily", "periodic")) {
        if (repository.inspectionParticipation(auth.tenantId(), auth.userId(), taskKind) > 0)
          result.computeIfAbsent(inspectionMenu(taskKind), k -> new HashSet<>()).add("查看");
      }
      result.computeIfAbsent("workspace", k -> new HashSet<>()).add("查看");
    }
    if (repository.hasTaskDatabase()
        && repository.repairPlanParticipation(auth.tenantId(), auth.userId()) > 0) {
      result.computeIfAbsent("repairPlan", k -> new HashSet<>()).add("查看");
      result.computeIfAbsent("workspace", k -> new HashSet<>()).add("查看");
    }
    if (roleIds.isEmpty()) return result;
    for (var role : repository.activeRoles(auth.tenantId(), roleIds)) {
      var rows = repository.grants(auth.tenantId(), role.getId(), null);
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
          Map.entry("inspectionDailyTask", Set.of("查看", "执行", "修改结果", "审核", "转派", "免检", "删除", "导出")),
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
      var u = repository.activeUser(auth.tenantId(), userId);
      if (u == null) return false;
      var s = scopeFor(u.getOrgId(), grants);
      return s.matches(companyOfOrganization(department), department, null, userId);
    } catch (ResponseStatusException e) {
      return false;
    }
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
      default ->
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.system.message0045");
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
      default ->
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.system.message0046");
    };
  }

  public PermissionScope maintenance(String kind, String action) {
    return scope(maintenanceMenu(kind), action);
  }

  public PermissionScope inspection(String kind, String action) {
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

  public PermissionScope equipment(String action) {
    return scope("equipment", action);
  }

  public PermissionScope repair(String action) {
    return scope("repair", action);
  }

  public void filterEquipment(LambdaQueryWrapper<Equipment> query) {
    filterEquipment(query, "查看");
  }

  public void filterEquipment(LambdaQueryWrapper<Equipment> query, String action) {
    var scope = equipment(action);
    com.windasoft.eam.repository.system.AuthorizationQueryPredicates.equipment(
        query, scope, auth.tenantId(), auth.userId());
  }

  public void filterRepair(LambdaQueryWrapper<RepairOrder> query) {
    filterRepair(query, "查看");
  }

  public void filterRepair(LambdaQueryWrapper<RepairOrder> query, String action) {
    PermissionScope scope;
    try {
      scope = repair(action);
    } catch (ResponseStatusException denied) {
      if (!"查看".equals(action)) throw denied;
      scope = new PermissionScope(false, null, Set.of(), true);
    }
    if ("查看".equals(action))
      scope =
          new PermissionScope(
              scope.all(), scope.companyId(), scope.departmentIds(), true, scope.companyIds());
    com.windasoft.eam.repository.system.AuthorizationQueryPredicates.repair(
        query, scope, auth.tenantId(), auth.userId());
  }

  public void filterRepairAcceptance(LambdaQueryWrapper<RepairOrder> query) {
    PermissionScope scope;
    try {
      scope = repair("验收");
    } catch (ResponseStatusException denied) {
      scope = new PermissionScope(false, null, Set.of(), false);
    }
    com.windasoft.eam.repository.system.AuthorizationQueryPredicates.acceptance(
        query, scope, auth.userId());
  }

  public void checkEquipment(Equipment e, String action) {
    var scope = equipment(action);
    boolean responsible =
        e.getOwnerIds() != null
            ? e.getOwnerIds().contains(auth.userId())
            : repository.hasTaskDatabase()
                && e.getId() != null
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

  private List<Grant> scopesFor(long userId, String menu, String action) {
    var roleIds =
        repository.userRoles(auth.tenantId(), userId).stream()
            .map(UserRoleLink::getRoleId)
            .toList();
    if (roleIds.isEmpty()) deny();
    var matches = new ArrayList<Grant>();
    for (var role : repository.activeRoles(auth.tenantId(), roleIds)) {
      var rows = repository.grants(auth.tenantId(), role.getId(), menu);
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
      var values = scopesFor(user.getId(), "inventory", "出库");
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

  public boolean inventoryWarehouseUser(long userId, String menu, long warehouse, String action) {
    var users = repository.userOrganization(auth.tenantId(), userId);
    var warehouses = repository.warehouse(auth.tenantId(), warehouse);
    if (users.size() != 1
        || warehouses.size() != 1
        || ((Number) users.getFirst().get("status")).intValue() != 1) return false;
    Object orgId = users.getFirst().get("org_id");
    Long company = orgId == null ? null : companyOfOrganization(((Number) orgId).longValue());
    long sourceCompany = ((Number) warehouses.getFirst().get("company_id")).longValue();
    try {
      for (var grant : scopesFor(userId, menu, action))
        if (grant.scopes.contains("all")
            || scopeFor(orgId == null ? null : ((Number) orgId).longValue(), List.of(grant))
                .companyIds()
                .contains(sourceCompany)
            || grant.scopes.contains("warehouse") && grant.orgIds.contains(warehouse)) return true;
    } catch (ResponseStatusException denied) {
      return false;
    }
    return false;
  }

  public void checkInventoryWarehouse(String menu, long warehouseId, String action) {
    var w = repository.warehouse(auth.tenantId(), warehouseId);
    if (w.size() != 1) deny();
    long company = ((Number) w.getFirst().get("company_id")).longValue();
    for (var grant : scopes(menu, action))
      if (grant.scopes.contains("all")
          || scopeFor(auth.user().getOrgId(), List.of(grant)).companyIds().contains(company)
          || grant.scopes.contains("warehouse") && grant.orgIds.contains(warehouseId)) return;
    deny();
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
    throw new com.windasoft.eam.common.LocalizedStatusException(
        HttpStatus.FORBIDDEN, "server.system.message0047");
  }
}
