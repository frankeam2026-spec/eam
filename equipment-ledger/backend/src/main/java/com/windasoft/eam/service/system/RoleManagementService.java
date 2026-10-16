package com.windasoft.eam.service.system;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.MenuGrantForm;
import com.windasoft.eam.dto.system.RoleForm;
import com.windasoft.eam.entity.system.RoleMenuPermission;
import com.windasoft.eam.entity.system.SystemRole;
import com.windasoft.eam.repository.system.SystemManagementRepository;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementService {
  private final SystemManagementRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

  public RoleManagementService(
      SystemManagementRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
  }

  public Object roleWarehouses() {
    permissions.require("role", "授权");
    return Map.of("data", repository.warehouseChoices(auth.tenantId()));
  }

  public Map<String, Object> roles() {
    permissions.require("role", "查看");
    var rows = repository.roles(auth.tenantId());
    return Map.of("data", rows.stream().map(this::roleView).toList());
  }

  @Transactional
  public Map<String, Object> createRole(RoleForm f) {
    permissions.require("role", "新增");
    permissions.require("role", "授权");
    checkRoleCode(f.code(), null);
    validateRoleForm(f);
    var x = new SystemRole();
    x.setTenantId(auth.tenantId());
    applyRole(x, f);
    repository.insertRole(x);
    saveMenuPermissions(x.getId(), f.menuPermissions());
    return Map.of("data", roleView(x));
  }

  @Transactional
  public Map<String, Object> updateRole(Long id, RoleForm f) {
    permissions.require("role", "编辑");
    permissions.require("role", "授权");
    var x = requireRole(id);
    checkRoleCode(f.code(), id);
    validateRoleForm(f);
    applyRole(x, f);
    repository.updateRole(x);
    saveMenuPermissions(id, f.menuPermissions());
    return Map.of("data", roleView(x));
  }

  @Transactional
  public Map<String, Object> deleteRole(Long id) {
    permissions.require("role", "删除");
    requireRole(id);
    if (repository.roleUserCount(auth.tenantId(), id) > 0)
      throw new IllegalArgumentException("该角色仍分配给用户，不能删除");
    repository.deleteMenuPermissions(auth.tenantId(), id);
    repository.deleteRole(id);
    return Map.of("data", Map.of("deleted", true));
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
      if (java.util.stream.Stream.of("warehouse", "companies", "selected")
              .filter(scopes::contains)
              .count()
          > 1) throw new IllegalArgumentException("指定仓库与指定公司/部门请分角色配置");
      if (scopes.contains("warehouse")) {
        if (ids.isEmpty() || ids.size() != new HashSet<>(ids).size())
          throw new IllegalArgumentException("请选择指定仓库");
        long warehouses = repository.warehouseCount(auth.tenantId(), ids);
        if (warehouses != ids.size()) throw new IllegalArgumentException("包含无效仓库");
      } else if (scopes.contains("selected") || scopes.contains("companies")) {
        if (ids.isEmpty() || ids.size() != new HashSet<>(ids).size())
          throw new IllegalArgumentException(menu + " 请选择指定公司或部门");
        long count =
            repository.organizationCount(
                auth.tenantId(), scopes.contains("companies") ? "公司" : "部门", ids);
        if (count != ids.size()) throw new IllegalArgumentException(menu + " 包含无效组织");
      } else if (!ids.isEmpty()) throw new IllegalArgumentException(menu + " 不应包含指定部门");
    }
    if (!granted) throw new IllegalArgumentException("请至少配置一个菜单");
  }

  private Set<String> allowedActions(String menu) {
    return switch (menu) {
      case "personalization" -> Set.of("查看");
      case "workspace" -> Set.of("查看", "团队工作查看");
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
      case "inspectionDailyTask", "inspectionPeriodicTask" ->
          Set.of("查看", "执行", "审核", "转派", "删除", "导出");
      case "inventoryMaterial" -> Set.of("查看", "新增", "编辑", "删除", "导入", "导出", "查看金额", "编辑参考价");
      case "inventoryWarehouse" -> Set.of("查看", "新增", "编辑", "删除", "导出");
      case "inventoryReceipt" -> Set.of("查看", "新增", "编辑", "删除", "入库", "导出", "查看金额");
      case "inventoryOutbound" ->
          Set.of("打印", "查看", "新增直接出库", "编辑草稿", "删除草稿", "预留", "释放预留", "确认出库", "关闭剩余需求", "导出", "查看金额");
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
    if (menu.equals("printTemplate")) return Set.of("all", "company", "companyTree", "companies");
    if (menu.equals("inventoryMaterial")) return Set.of("all");
    if (menu.startsWith("inventory") && !menu.equals("inventory"))
      return Set.of("all", "company", "companyTree", "companies", "warehouse");
    if (Set.of(
            "workspace",
            "homeTemplate",
            "personalization",
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

  private void saveMenuPermissions(Long roleId, Map<String, MenuGrantForm> grants) {
    repository.deleteMenuPermissions(auth.tenantId(), roleId);
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
      repository.insertMenuPermission(row);
    }
  }

  private void checkRoleCode(String code, Long id) {
    if (repository.roleCodeCount(auth.tenantId(), code, id) > 0)
      throw new IllegalArgumentException("角色编码已存在");
  }

  private SystemRole requireRole(Long id) {
    var x = repository.role(auth.tenantId(), id);
    if (x == null) throw new IllegalArgumentException("角色不存在");
    return x;
  }

  private Map<String, Object> roleView(SystemRole x) {
    Map<String, Object> grants = new LinkedHashMap<>();
    var rows = repository.menuPermissions(auth.tenantId(), x.getId());
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
