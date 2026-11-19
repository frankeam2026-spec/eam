package com.windasoft.eam.service.experience;

import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.repository.inventory.WarehouseRepository;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalyticsAccess {
  private final PermissionService permissions;
  private final AuthContext auth;
  private final WarehouseRepository warehouseRepository;

  public AnalyticsAccess(
      PermissionService permissions, AuthContext auth, WarehouseRepository warehouseRepository) {
    this.permissions = permissions;
    this.auth = auth;
    this.warehouseRepository = warehouseRepository;
  }

  public boolean available(AnalyticsCatalog.Metric metric) {
    var a = permissions.menuActions();
    boolean domain =
        switch (metric.domain()) {
          case "repair" -> a.getOrDefault("repair", Set.of()).contains(PermissionAction.VIEW.code());
          case "maintenance" -> a.getOrDefault("maintenanceTask", Set.of()).contains(PermissionAction.VIEW.code());
          case "inspection" ->
              a.getOrDefault("inspectionDailyTask", Set.of()).contains(PermissionAction.VIEW.code())
                  || a.getOrDefault("inspectionPeriodicTask", Set.of()).contains(PermissionAction.VIEW.code());
          case "anomaly" -> a.getOrDefault("anomaly", Set.of()).contains(PermissionAction.VIEW.code());
          case "material" ->
              permissions.allowed("inventoryQuery", PermissionAction.VIEW)
                  && permissions.allowed("inventoryQuery", PermissionAction.VIEW_AMOUNT);
          default -> false;
        };
    return domain
        && (!metric.id().equals("equipment_material_ranking")
            || a.getOrDefault("equipment", Set.of()).contains(PermissionAction.VIEW.code()));
  }

  public void require(AnalyticsCatalog.Metric metric, String scope) {
    if (!permissions.menuActions().getOrDefault("workspace", Set.of()).contains(PermissionAction.VIEW.code())
        || !available(metric)) deny();
    if (scope.equals("team")) permissions.require("workspace", PermissionAction.VIEW_TEAM_WORK);
    else if (!scope.equals("personal"))
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.experience.message0011");
  }

  public boolean exportable(AnalyticsCatalog.Metric metric) {
    return switch (metric.domain()) {
      case "repair" -> permissions.allowed("repair", PermissionAction.EXPORT);
      case "maintenance" -> permissions.allowed("maintenanceTask", PermissionAction.EXPORT);
      case "inspection" ->
          (!permissions.menuActions().getOrDefault("inspectionDailyTask", Set.of()).contains(PermissionAction.VIEW.code())
                  || permissions.allowed("inspectionDailyTask", PermissionAction.EXPORT))
              && (!permissions
                      .menuActions()
                      .getOrDefault("inspectionPeriodicTask", Set.of())
                      .contains(PermissionAction.VIEW.code())
                  || permissions.allowed("inspectionPeriodicTask", PermissionAction.EXPORT));
      case "anomaly" -> permissions.allowed("anomaly", PermissionAction.EXPORT);
      case "material" ->
          permissions.allowed("inventoryQuery", PermissionAction.EXPORT)
              && permissions.allowed("inventoryQuery", PermissionAction.VIEW_AMOUNT);
      default -> false;
    };
  }

  public PermissionScope scope(String menu) {
    try {
      return permissions.scope(menu, PermissionAction.VIEW);
    } catch (ResponseStatusException denied) {
      if (permissions.menuActions().getOrDefault(menu, Set.of()).contains(PermissionAction.VIEW.code()))
        return new PermissionScope(false, null, Set.of(), true);
      throw denied;
    }
  }

  public String scopeSql(
      PermissionScope scope, String company, String department, String owner, List<Object> args) {
    if (scope.all()) return "1=1";
    var clauses = new ArrayList<String>();
    if (!scope.companyIds().isEmpty()) clauses.add(in(company, scope.companyIds(), args));
    if (!scope.departmentIds().isEmpty()) clauses.add(in(department, scope.departmentIds(), args));
    if (scope.owner()) {
      clauses.add(owner);
      for (long i = 0; i < owner.chars().filter(c -> c == '?').count(); i++)
        args.add(auth.userId());
    }
    return clauses.isEmpty() ? "1=0" : "(" + String.join(" OR ", clauses) + ")";
  }

  public String teamSql(
      String range, String company, String department, String owner, List<Object> args) {
    return range.equals("team")
        ? " AND "
            + scopeSql(permissions.scope("workspace", PermissionAction.VIEW_TEAM_WORK), company, department, owner, args)
        : "";
  }

  public List<Long> warehouses(boolean amount) {
    var warehouses = warehouseRepository.scopeRows(auth.tenantId());
    var allowed = permissions.inventoryWarehouses("inventoryQuery", PermissionAction.VIEW, warehouses);
    if (amount) {
      allowed.retainAll(permissions.inventoryWarehouses("inventoryQuery", PermissionAction.VIEW_AMOUNT, warehouses));
    }
    return allowed.stream().sorted().toList();
  }

  public Set<Long> departments(long id) {
    return permissions.departmentTree(id);
  }

  public static String in(String column, Collection<Long> values, List<Object> args) {
    if (values.isEmpty()) return "1=0";
    args.addAll(values);
    return column + " IN (" + String.join(",", Collections.nCopies(values.size(), "?")) + ")";
  }

  public static void deny() {
    throw new com.windasoft.eam.common.LocalizedStatusException(
        HttpStatus.FORBIDDEN, "server.experience.message0012");
  }
}
