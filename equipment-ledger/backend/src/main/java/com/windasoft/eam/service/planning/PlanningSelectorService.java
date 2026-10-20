package com.windasoft.eam.service.planning;

import com.windasoft.eam.dto.common.*;
import com.windasoft.eam.repository.common.*;
import com.windasoft.eam.repository.inspection.InspectionListRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceListRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.repository.planning.PlanningSelectorRepository;
import com.windasoft.eam.service.inspection.InspectionViewAssembler;
import com.windasoft.eam.service.maintenance.MaintenanceViewAssembler;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PlanningSelectorService {
  private final PlanningSelectorRepository repository;
  private final MaintenanceListRepository maintenance;
  private final InspectionListRepository inspection;
  private final MaintenanceRepository metadata;
  private final MaintenanceViewAssembler maintenanceViews;
  private final InspectionViewAssembler inspectionViews;
  private final PermissionService permissions;
  private final AuthContext auth;

  public PlanningSelectorService(
      PlanningSelectorRepository repository,
      MaintenanceListRepository maintenance,
      InspectionListRepository inspection,
      MaintenanceRepository metadata,
      MaintenanceViewAssembler maintenanceViews,
      InspectionViewAssembler inspectionViews,
      PermissionService permissions,
      AuthContext auth) {
    this.repository = repository;
    this.maintenance = maintenance;
    this.inspection = inspection;
    this.metadata = metadata;
    this.maintenanceViews = maintenanceViews;
    this.inspectionViews = inspectionViews;
    this.permissions = permissions;
    this.auth = auth;
  }

  private void access(String module) {
    if (module.equals("maintenance")) {
      if (!permissions.anyMaintenance("查看")) permissions.require("maintenanceTask", "查看");
    } else if (module.equals("inspection")) {
      if (!permissions.anyInspection("查看")) permissions.require("inspectionDailyTask", "查看");
    } else
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0054");
  }

  private SqlPredicate equipmentScope(String module) {
    access(module);
    var access = SqlPredicate.of("1=0");
    var kinds =
        module.equals("maintenance")
            ? List.of("standards", "plans", "tasks")
            : List.of("standards", "daily-plans", "periodic-plans", "daily", "periodic");
    for (String kind : kinds)
      try {
        var scope =
            module.equals("maintenance")
                ? permissions.maintenance(kind, "查看")
                : permissions.inspection(kind, "查看");
        access =
            access.or(
                SqlScopes.scope(
                    scope,
                    "t.company_org_id",
                    "t.department_org_id",
                    "t.owner_user_id",
                    auth.userId()));
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() != 403) throw denied;
      }
    return access;
  }

  public PageResult<Map<String, Object>> equipment(
      String module, ListQuery query, String category) {
    return repository.equipment(auth.tenantId(), query, equipmentScope(module), null, category);
  }

  public PageResult<Map<String, Object>> users(String module, ListQuery query) {
    access(module);
    return repository.users(auth.tenantId(), query, null);
  }

  public static Set<Long> ids(Object value) {
    if (value == null) return Set.of();
    if (!(value instanceof Collection<?> values) || values.size() > 200)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0055");
    var result = new LinkedHashSet<Long>();
    for (Object id : values)
      try {
        long n = Long.parseLong(id.toString());
        if (n <= 0) throw new NumberFormatException();
        result.add(n);
      } catch (Exception e) {
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0056");
      }
    return result;
  }

  public Map<String, Object> resolve(String module, Map<String, Object> body) {
    access(module);
    var q = ListQuery.from(Map.of("size", "200"));
    var equipmentIds = ids(body.get("equipmentIds"));
    var userIds = ids(body.get("userIds"));
    var standardIds = ids(body.get("standardIds"));
    return Map.of(
        "equipment",
        equipmentIds.isEmpty()
            ? List.of()
            : repository
                .equipment(auth.tenantId(), q, equipmentScope(module), equipmentIds, "")
                .data(),
        "users",
        userIds.isEmpty() ? List.of() : repository.users(auth.tenantId(), q, userIds).data(),
        "standards",
        standardIds.isEmpty()
                || !permissions.allowed(
                    module.equals("maintenance") ? "maintenanceStandard" : "inspectionStandard",
                    "查看")
            ? List.of()
            : standards(module, q, null, standardIds).data());
  }

  public PageResult<Map<String, Object>> standards(
      String module, ListQuery query, Long equipmentId, Set<Long> ids) {
    access(module);
    var extra = ids == null ? SqlPredicate.of("1=1") : SqlScopes.in("t.id", ids);
    if (equipmentId != null) {
      var found =
          repository.equipment(
              auth.tenantId(),
              ListQuery.from(Map.of("size", "1")),
              equipmentScope(module),
              Set.of(equipmentId),
              "");
      if (found.data().isEmpty())
        throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.FORBIDDEN, "设备不在可选范围内");
      var rows = repository.equipmentContext(auth.tenantId(), equipmentId);
      var ancestors = new LinkedHashSet<Long>();
      var parents = new HashMap<Long, Long>();
      for (var row : metadata.listCategoryParents(auth.tenantId())) {
        parents.put(
            ((Number) row.get("id")).longValue(),
            row.get("parent_id") == null ? null : ((Number) row.get("parent_id")).longValue());
      }
      Long category =
          rows.getFirst().get("category_id") == null
              ? null
              : ((Number) rows.getFirst().get("category_id")).longValue();
      while (category != null && ancestors.add(category)) category = parents.get(category);
      extra = extra.and(repository.applicable(equipmentId, ancestors));
    }
    if (module.equals("maintenance")) {
      var selected =
          maintenance.select(
              auth.tenantId(),
              auth.userId(),
              "standards",
              permissions.maintenance("standards", "查看"),
              false,
              null,
              query,
              extra,
              null);
      return new PageResult<>(
          maintenance.page(selected, query).stream().map(maintenanceViews::definition).toList(),
          query.page(),
          query.size(),
          maintenance.count(selected));
    }
    var scope = permissions.inspection("standards", "查看");
    var selected =
        inspection.select(
            auth.tenantId(),
            auth.userId(),
            "standards",
            scope,
            scope,
            false,
            null,
            query,
            extra,
            null);
    return new PageResult<>(
        inspection.page(selected, query).stream().map(inspectionViews::definition).toList(),
        query.page(),
        query.size(),
        inspection.count(selected));
  }

  /**
   * Existing automatic project defaults need every matching standard, never an arbitrary first
   * page.
   */
  public List<Map<String, Object>> defaults(String module, Map<String, Object> body) {
    var result = new LinkedHashMap<String, Map<String, Object>>();
    var q = ListQuery.from(Map.of());
    for (long equipment : ids(body.get("equipmentIds")))
      for (int page = 1; ; page++) {
        var batch = standards(module, q.at(page, 200), equipment, null);
        for (var row : batch.data()) result.put(row.get("id").toString(), row);
        if ((long) page * 200 >= batch.page().total()) break;
      }
    return new ArrayList<>(result.values());
  }
}
