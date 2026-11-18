package com.windasoft.eam.service.system;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.domain.system.PermissionMenu;
import com.windasoft.eam.dto.inventory.WarehouseChoice;
import com.windasoft.eam.dto.system.MenuGrantForm;
import com.windasoft.eam.dto.system.MenuGrantView;
import com.windasoft.eam.dto.system.RoleForm;
import com.windasoft.eam.dto.system.RoleView;
import com.windasoft.eam.entity.system.RoleMenuAction;
import com.windasoft.eam.entity.system.RoleMenuPermission;
import com.windasoft.eam.entity.system.RoleMenuScope;
import com.windasoft.eam.entity.system.RoleMenuTarget;
import com.windasoft.eam.entity.system.SystemRole;
import com.windasoft.eam.repository.inventory.InventoryQueryRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.repository.system.RoleLegacyActionRepository;
import com.windasoft.eam.repository.system.RoleManagementRepository;
import com.windasoft.eam.repository.system.RoleMenuActionRepository;
import com.windasoft.eam.repository.system.RoleMenuScopeRepository;
import com.windasoft.eam.repository.system.RoleMenuTargetRepository;
import com.windasoft.eam.repository.system.RoleOrganizationRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 角色管理服务：验证菜单动作与数据范围配置，角色授权须与普通编辑权限区分。 */
@Service
public class RoleManagementService {
  private final RoleManagementRepository repository;
  private final RoleMenuTargetRepository roleMenuTarget;
  private final RoleMenuScopeRepository roleMenuScope;
  private final RoleMenuActionRepository roleMenuAction;
  private final RoleLegacyActionRepository roleLegacyAction;
  private final RoleOrganizationRepository roleOrganization;
  private final OrgUnitRepository orgUnits;
  private final InventoryQueryRepository inventoryQueries;
  private final AuthContext auth;
  private final PermissionService permissions;

  public RoleManagementService(
      RoleManagementRepository repository,
      RoleMenuTargetRepository roleMenuTarget,
      RoleMenuScopeRepository roleMenuScope,
      RoleMenuActionRepository roleMenuAction,
      RoleLegacyActionRepository roleLegacyAction,
      RoleOrganizationRepository roleOrganization,
      OrgUnitRepository orgUnits,
      InventoryQueryRepository inventoryQueries,
      AuthContext auth,
      PermissionService permissions) {
    this.repository = repository;
    this.roleMenuTarget = roleMenuTarget;
    this.roleMenuScope = roleMenuScope;
    this.roleMenuAction = roleMenuAction;
    this.roleLegacyAction = roleLegacyAction;
    this.roleOrganization = roleOrganization;
    this.orgUnits = orgUnits;
    this.inventoryQueries = inventoryQueries;
    this.auth = auth;
    this.permissions = permissions;
  }

  public List<WarehouseChoice> roleWarehouses() {
    permissions.require(PermissionMenu.ROLE, PermissionAction.AUTHORIZE);
    return inventoryQueries.warehouseChoices(auth.tenantId());
  }

  public List<RoleView> roles() {
    permissions.require(PermissionMenu.ROLE, PermissionAction.VIEW);
    var rows = repository.roles(auth.tenantId());
    var grantsByRole =
        repository.menuPermissionsForRoles(
            auth.tenantId(), rows.stream().map(SystemRole::getId).toList());
    return rows.stream()
        .map(role -> roleView(role, grantsByRole.getOrDefault(role.getId(), List.of())))
        .toList();
  }

  @Transactional
  public RoleView createRole(RoleForm f) {
    permissions.require(PermissionMenu.ROLE, PermissionAction.CREATE);
    permissions.require(PermissionMenu.ROLE, PermissionAction.AUTHORIZE);
    checkRoleCode(f.code(), null);
    validateRoleForm(f);
    var x = new SystemRole();
    x.setTenantId(auth.tenantId());
    applyRole(x, f);
    repository.insertRole(x);
    clearLegacyRelations(x);
    saveMenuPermissions(x.getId(), f.menuPermissions());
    return roleView(x);
  }

  @Transactional
  public RoleView updateRole(Long id, RoleForm f) {
    permissions.require(PermissionMenu.ROLE, PermissionAction.EDIT);
    permissions.require(PermissionMenu.ROLE, PermissionAction.AUTHORIZE);
    var x = requireRole(id);
    checkRoleCode(f.code(), id);
    validateRoleForm(f);
    applyRole(x, f);
    repository.updateRole(x);
    clearLegacyRelations(x);
    saveMenuPermissions(id, f.menuPermissions());
    return roleView(x);
  }

  @Transactional
  public void deleteRole(Long id) {
    permissions.require(PermissionMenu.ROLE, PermissionAction.DELETE);
    requireRole(id);
    if (repository.roleUserCount(auth.tenantId(), id) > 0)
      throw new LocalizedArgumentException("server.system.message0052");
    repository.deleteMenuPermissions(auth.tenantId(), id);
    repository.deleteRole(id);
  }

  private void applyRole(SystemRole x, RoleForm f) {
    x.setCode(f.code().trim());
    x.setName(f.name().trim());
    x.setDescription(f.description() == null ? "" : f.description().trim());
    x.setStatus(f.status() == null ? 1 : f.status());
    x.setDataScope("PER_MENU");
    x.setOrganizationIds(List.of());
    x.setLegacyActions(List.of());
  }

  private void validateRoleForm(RoleForm f) {
    if (f.menuPermissions().isEmpty())
      throw new LocalizedArgumentException("server.system.message0053");
    boolean granted = false;
    for (var entry : f.menuPermissions().entrySet()) {
      var menu = entry.getKey();
      var grant = entry.getValue();
      var allowedActions = allowedActions(menu);
      if (allowedActions.isEmpty() || grant == null)
        throw new LocalizedArgumentException("server.system.message0054");
      var actions = grant.actions() == null ? List.<String>of() : grant.actions();
      if (actions.isEmpty()) continue;
      granted = true;
      if (!allowedActions.containsAll(actions) || actions.size() != new HashSet<>(actions).size())
        throw new LocalizedArgumentException("server.system.message0055", menu);
      if (!actions.contains("查看"))
        throw new LocalizedArgumentException("server.system.message0056", menu);
      var scopes = grant.dataScopes() == null ? List.<String>of() : grant.dataScopes();
      if (scopes.isEmpty()
          || !allowedScopes(menu).containsAll(scopes)
          || scopes.size() != new HashSet<>(scopes).size()
          || (scopes.contains("all") && scopes.size() > 1))
        throw new LocalizedArgumentException("server.system.message0057", menu);
      var ids = grant.orgIds() == null ? List.<Long>of() : grant.orgIds();
      if (Stream.of("warehouse", "companies", "selected").filter(scopes::contains).count() > 1)
        throw new LocalizedArgumentException("server.system.message0058");
      if (scopes.contains("warehouse")) {
        if (ids.isEmpty() || ids.size() != new HashSet<>(ids).size())
          throw new LocalizedArgumentException("server.system.message0059");
        long warehouses = inventoryQueries.warehouseCount(auth.tenantId(), ids);
        if (warehouses != ids.size())
          throw new LocalizedArgumentException("server.system.message0060");
      } else if (scopes.contains("selected") || scopes.contains("companies")) {
        if (ids.isEmpty() || ids.size() != new HashSet<>(ids).size())
          throw new LocalizedArgumentException("server.system.message0061", menu);
        long count =
            orgUnits.organizationCount(
                auth.tenantId(), scopes.contains("companies") ? "公司" : "部门", ids);
        if (count != ids.size())
          throw new LocalizedArgumentException("server.system.message0062", menu);
      } else if (!ids.isEmpty())
        throw new LocalizedArgumentException("server.system.message0063", menu);
    }
    if (!granted) throw new LocalizedArgumentException("server.system.message0053");
  }

  private Set<String> allowedActions(String menu) {
    return switch (menu) {
      case "aiSetting" -> Set.of("查看", "编辑");
      case "knowledge" -> Set.of("查看", "编辑", "发布", "停用");
      case "businessConfig" -> Set.of("查看", "编辑", "发布");
      case "workflow" -> Set.of("查看", "编辑", "发布", "停用", "脚本配置", "脚本发布", "脚本试算");
      case "personalization", "language" -> Set.of("查看");
      case "workspace" -> Set.of("查看", "团队工作查看");
      case "notificationPolicy", "notificationChannel" -> Set.of("查看", "编辑");
      case "notificationDelivery" -> Set.of("查看", "重试");
      case "printTemplate" -> Set.of("查看", "新增", "编辑", "删除");
      case "homeTemplate" -> Set.of("查看", "新增", "编辑", "发布", "停用");
      case "anomaly" -> Set.of("查看", "新增", "消除", "报修", "安排维修", "作废", "导出");
      case "repairPlan" -> Set.of("查看", "新增", "编辑", "启用", "生成工单", "取消", "导出");
      case "materialCustody" -> Set.of("查看", "登记暂存", "导出");
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
      case "inspectionDailyTask" -> Set.of("查看", "执行", "修改结果", "审核", "转派", "免检", "删除", "导出");
      case "inspectionPeriodicTask" -> Set.of("查看", "执行", "审核", "转派", "删除", "导出");
      case "inventoryMaterial" -> Set.of("查看", "新增", "编辑", "删除", "导入", "导出", "查看金额", "编辑参考价");
      case "inventoryWarehouse" -> Set.of("查看", "新增", "编辑", "删除", "导出");
      case "inventoryReceipt" -> Set.of("查看", "新增", "编辑", "删除", "入库", "导出", "查看金额");
      case "inventoryOutbound" ->
          Set.of(
              "打印", "查看", "新增直接出库", "编辑草稿", "删除草稿", "预留", "释放预留", "确认出库", "关闭剩余需求", "导出", "查看金额");
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
    if (menu.equals("aiSetting")) return Set.of("all");
    if (menu.equals("knowledge")) return Set.of("all", "company", "companyTree", "companies");
    if (menu.equals("workflow")) return Set.of("all", "company", "companyTree", "companies");
    if (menu.startsWith("notification")) return Set.of("all");
    if (menu.equals("printTemplate")) return Set.of("all", "company", "companyTree", "companies");
    if (menu.equals("inventoryMaterial")) return Set.of("all");
    if (menu.startsWith("inventory") && !menu.equals("inventory"))
      return Set.of("all", "company", "companyTree", "companies", "warehouse");
    if (Set.of(
            "workspace",
            "businessConfig",
            "homeTemplate",
            "personalization",
            "language",
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
            "anomaly",
            "repairPlan",
            "materialCustody",
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
        .contains(menu))
      return Set.of(
          "all",
          "company",
          "companyTree",
          "companies",
          "department",
          "departmentTree",
          "selected",
          "owner");
    return Set.of(
        "all", "company", "companyTree", "companies", "department", "departmentTree", "selected");
  }

  /** 重写角色菜单授权时先移除旧关联，再保存新配置；调用方的事务保护整个变更。 */
  private void saveMenuPermissions(Long roleId, Map<String, MenuGrantForm> grants) {
    repository.deleteMenuPermissions(auth.tenantId(), roleId);
    for (var entry : grants.entrySet()) {
      var grant = entry.getValue();
      if (grant.actions() == null || grant.actions().isEmpty()) continue;
      var row = new RoleMenuPermission();
      row.setTenantId(auth.tenantId());
      row.setRoleId(roleId);
      row.setMenuKey(entry.getKey());
      row.setActions(grant.actions());
      row.setDataScopes(grant.dataScopes());
      row.setTargetIds(grant.orgIds() == null ? List.of() : grant.orgIds());
      repository.insertMenuPermission(row);
      saveGrantRelations(row);
    }
  }

  private void checkRoleCode(String code, Long id) {
    if (repository.roleCodeCount(auth.tenantId(), code, id) > 0)
      throw new LocalizedArgumentException("server.system.message0064");
  }

  private SystemRole requireRole(Long id) {
    var x = repository.role(auth.tenantId(), id);
    if (x == null) throw new LocalizedArgumentException("server.system.message0065");
    return x;
  }

  private RoleView roleView(SystemRole x) {
    return roleView(x, repository.menuPermissions(auth.tenantId(), x.getId()));
  }

  private RoleView roleView(SystemRole x, List<RoleMenuPermission> rows) {
    Map<String, MenuGrantView> grants = new LinkedHashMap<>();
    for (var row : rows)
      grants.put(
          row.getMenuKey(),
          new MenuGrantView(
              row.getActions(),
              row.getDataScopes(),
              row.getTargetIds().stream().map(String::valueOf).toList()));
    if (rows.isEmpty() && !"PER_MENU".equals(x.getDataScope())) {
      Map<String, List<String>> actions = new LinkedHashMap<>();
      for (var raw : x.getLegacyActions()) {
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
            new MenuGrantView(
                menuActions,
                List.of(menuScope),
                "selected".equals(menuScope)
                    ? x.getOrganizationIds().stream().map(String::valueOf).toList()
                    : List.of()));
      }
    }
    return new RoleView(
        String.valueOf(x.getId()),
        x.getCode(),
        x.getName(),
        x.getDescription() == null ? "" : x.getDescription(),
        x.getStatus() == 1 ? "启用" : "停用",
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

  /** 普通角色编辑切换为逐菜单授权，清除旧角色范围；与主体及菜单重写保持同一服务事务。 */
  private void clearLegacyRelations(SystemRole role) {
    roleOrganization.delete(role.getTenantId(), role.getId());
    roleLegacyAction.delete(role.getTenantId(), role.getId());
  }

  /** 授权主体已生成真实ID；三种关系按原次序写入，失败由外层角色事务整体回滚。 */
  private void saveGrantRelations(RoleMenuPermission grant) {
    var actions = new ArrayList<RoleMenuAction>();
    int at = 0;
    for (var value : grant.getActions()) {
      var row = new RoleMenuAction();
      row.setTenantId(grant.getTenantId());
      row.setGrantId(grant.getId());
      row.setActionName(value);
      row.setSortNo(at++);
      actions.add(row);
    }
    roleMenuAction.insert(actions);
    var scopes = new ArrayList<RoleMenuScope>();
    at = 0;
    for (var value : grant.getDataScopes()) {
      var row = new RoleMenuScope();
      row.setTenantId(grant.getTenantId());
      row.setGrantId(grant.getId());
      row.setScopeCode(value);
      row.setSortNo(at++);
      scopes.add(row);
    }
    roleMenuScope.insert(scopes);
    String kind =
        grant.getDataScopes().contains("warehouse")
            ? "warehouse"
            : grant.getDataScopes().contains("companies") ? "company" : "department";
    var targets = new ArrayList<RoleMenuTarget>();
    at = 0;
    for (var value : grant.getTargetIds()) {
      var row = new RoleMenuTarget();
      row.setTenantId(grant.getTenantId());
      row.setGrantId(grant.getId());
      row.setTargetKind(kind);
      row.setTargetId(value);
      row.setSortNo(at++);
      targets.add(row);
    }
    roleMenuTarget.insert(targets);
  }
}
