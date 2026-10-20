package com.windasoft.eam.service.experience;

import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.experience.AnalyticsRepository;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalyticsAccess {
  private final PermissionService permissions;
  private final AuthContext auth;
  private final AnalyticsRepository repository;

  public AnalyticsAccess(
      PermissionService permissions, AuthContext auth, AnalyticsRepository repository) {
    this.permissions = permissions;
    this.auth = auth;
    this.repository = repository;
  }

  public boolean available(AnalyticsCatalog.Metric metric) {
    var a = permissions.menuActions();
    boolean domain =
        switch (metric.domain()) {
          case "repair" -> a.getOrDefault("repair", Set.of()).contains("查看");
          case "maintenance" -> a.getOrDefault("maintenanceTask", Set.of()).contains("查看");
          case "inspection" ->
              a.getOrDefault("inspectionDailyTask", Set.of()).contains("查看")
                  || a.getOrDefault("inspectionPeriodicTask", Set.of()).contains("查看");
          case "anomaly" -> a.getOrDefault("anomaly", Set.of()).contains("查看");
          case "material" ->
              permissions.allowed("inventoryQuery", "查看")
                  && permissions.allowed("inventoryQuery", "查看金额");
          default -> false;
        };
    return domain
        && (!metric.id().equals("equipment_material_ranking")
            || a.getOrDefault("equipment", Set.of()).contains("查看"));
  }

  public void require(AnalyticsCatalog.Metric metric, String scope) {
    if (!permissions.menuActions().getOrDefault("workspace", Set.of()).contains("查看")
        || !available(metric)) deny();
    if (scope.equals("team")) permissions.require("workspace", "团队工作查看");
    else if (!scope.equals("personal"))
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.experience.message0011");
  }

  public boolean exportable(AnalyticsCatalog.Metric metric) {
    return switch (metric.domain()) {
      case "repair" -> permissions.allowed("repair", "导出");
      case "maintenance" -> permissions.allowed("maintenanceTask", "导出");
      case "inspection" ->
          (!permissions.menuActions().getOrDefault("inspectionDailyTask", Set.of()).contains("查看")
                  || permissions.allowed("inspectionDailyTask", "导出"))
              && (!permissions
                      .menuActions()
                      .getOrDefault("inspectionPeriodicTask", Set.of())
                      .contains("查看")
                  || permissions.allowed("inspectionPeriodicTask", "导出"));
      case "anomaly" -> permissions.allowed("anomaly", "导出");
      case "material" ->
          permissions.allowed("inventoryQuery", "导出")
              && permissions.allowed("inventoryQuery", "查看金额");
      default -> false;
    };
  }

  public PermissionScope scope(String menu) {
    try {
      return permissions.scope(menu, "查看");
    } catch (ResponseStatusException denied) {
      if (permissions.menuActions().getOrDefault(menu, Set.of()).contains("查看"))
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
            + scopeSql(permissions.scope("workspace", "团队工作查看"), company, department, owner, args)
        : "";
  }

  public List<Long> warehouses(boolean amount) {
    return repository.warehouses(auth.tenantId()).stream()
        .filter(
            w ->
                permissions.inventoryWarehouseAllowed(
                        "inventoryQuery", ((Number) w.get("id")).longValue(), "查看")
                    && (!amount
                        || permissions.inventoryWarehouseAllowed(
                            "inventoryQuery", ((Number) w.get("id")).longValue(), "查看金额")))
        .map(w -> ((Number) w.get("id")).longValue())
        .toList();
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
