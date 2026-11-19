package com.windasoft.eam.service.planning;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.planning.PlanningCandidate.DefaultsRequest;
import com.windasoft.eam.dto.planning.PlanningCandidate.Equipment;
import com.windasoft.eam.dto.planning.PlanningCandidate.ResolveRequest;
import com.windasoft.eam.dto.planning.PlanningCandidate.Resolved;
import com.windasoft.eam.dto.planning.PlanningCandidate.Standard;
import com.windasoft.eam.dto.planning.PlanningCandidate.User;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.repository.common.SqlPredicate;
import com.windasoft.eam.repository.common.SqlScopes;
import com.windasoft.eam.repository.inspection.InspectionListRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceListRepository;
import com.windasoft.eam.repository.planning.PlanningSelectorRepository;
import com.windasoft.eam.service.basedata.BaseDataType;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PlanningSelectorService {
  private static final int ID_LIMIT = 200;
  private static final int DEFAULTS_BATCH_SIZE = 200;

  private final PlanningSelectorRepository repository;
  private final MaintenanceListRepository maintenance;
  private final InspectionListRepository inspection;
  private final BaseItemRepository metadata;
  private final PermissionService permissions;
  private final AuthContext auth;

  public PlanningSelectorService(
      PlanningSelectorRepository repository,
      MaintenanceListRepository maintenance,
      InspectionListRepository inspection,
      BaseItemRepository metadata,
      PermissionService permissions,
      AuthContext auth) {
    this.repository = repository;
    this.maintenance = maintenance;
    this.inspection = inspection;
    this.metadata = metadata;
    this.permissions = permissions;
    this.auth = auth;
  }

  private void access(String module) {
    if (module.equals("maintenance")) {
      if (!permissions.anyMaintenance(PermissionAction.VIEW)) permissions.require("maintenanceTask", PermissionAction.VIEW);
    } else if (module.equals("inspection")) {
      if (!permissions.anyInspection(PermissionAction.VIEW)) permissions.require("inspectionDailyTask", PermissionAction.VIEW);
    } else throw new LocalizedArgumentException("server.common.message0054");
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
                ? permissions.maintenance(kind, PermissionAction.VIEW)
                : permissions.inspection(kind, PermissionAction.VIEW);
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

  public PageResult<Equipment> equipment(String module, ListQuery query, String category) {
    return repository.equipment(auth.tenantId(), query, equipmentScope(module), null, category);
  }

  public PageResult<User> users(String module, ListQuery query) {
    access(module);
    return repository.users(auth.tenantId(), query, null);
  }

  public static Set<Long> ids(Object value) {
    if (value == null) return Set.of();
    if (!(value instanceof Collection<?> values) || values.size() > ID_LIMIT)
      throw new LocalizedArgumentException("server.common.message0055");
    var result = new LinkedHashSet<Long>();
    for (Object id : values)
      try {
        long n = Long.parseLong(id.toString());
        if (n <= 0) throw new NumberFormatException();
        result.add(n);
      } catch (Exception e) {
        throw new LocalizedArgumentException("server.common.message0056");
      }
    return result;
  }

  /** 回填既有选择，不因不可见标准扩大授权，也不为按ID读取的结果另行计数。 */
  public Resolved resolve(String module, ResolveRequest request) {
    access(module);
    var query = ListQuery.from(Map.of("size", Integer.toString(ID_LIMIT)));
    var equipmentIds = ids(request.equipmentIds());
    var userIds = ids(request.userIds());
    var standardIds = ids(request.standardIds());
    return new Resolved(
        equipmentIds.isEmpty()
            ? List.of()
            : repository
                .equipment(auth.tenantId(), query, equipmentScope(module), equipmentIds, "")
                .data(),
        userIds.isEmpty() ? List.of() : repository.users(auth.tenantId(), query, userIds).data(),
        standardIds.isEmpty()
                || !permissions.allowed(
                    module.equals("maintenance") ? "maintenanceStandard" : "inspectionStandard",
                    PermissionAction.VIEW)
            ? List.of()
            : List.copyOf(
                selectStandards(module, query, SqlScopes.in("t.id", standardIds))
                    .rows()
                    .apply(query)));
  }

  public PageResult<Standard> standards(
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
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "设备不在可选范围内");
      extra = extra.and(applicable(found.data().getFirst(), categoryParents()));
    }
    var selection = selectStandards(module, query, extra);
    return new PageResult<>(
        List.copyOf(selection.rows().apply(query)),
        query.page(),
        query.size(),
        selection.count().getAsLong());
  }

  private Map<Long, Long> categoryParents() {
    var parents = new HashMap<Long, Long>();
    for (var row : metadata.itemsByType(auth.tenantId(), BaseDataType.CATEGORY.code(), false)) {
      parents.put(row.getId(), row.getParentId());
    }
    return parents;
  }

  private SqlPredicate applicable(Equipment equipment, Map<Long, Long> parents) {
    var ancestors = new LinkedHashSet<Long>();
    String categoryId = equipment.categoryId();
    Long category = categoryId.isEmpty() ? null : Long.valueOf(categoryId);
    while (category != null && ancestors.add(category)) {
      category = parents.get(category);
    }
    return repository.applicable(Long.parseLong(equipment.id()), ancestors);
  }

  private record StandardSelection(
      LongSupplier count, Function<ListQuery, List<? extends Standard>> rows) {}

  private StandardSelection selectStandards(String module, ListQuery query, SqlPredicate extra) {
    if (module.equals("maintenance")) {
      var selected =
          maintenance.select(
              auth.tenantId(),
              auth.userId(),
              "standards",
              permissions.maintenance("standards", PermissionAction.VIEW),
              false,
              null,
              query,
              extra,
              null);
      return new StandardSelection(
          () -> maintenance.count(selected), page -> maintenance.standardRows(selected, page));
    }
    var scope = permissions.inspection("standards", PermissionAction.VIEW);
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
    return new StandardSelection(
        () -> inspection.count(selected), page -> inspection.standardRows(selected, page));
  }

  /** 按设备请求顺序合并适用标准。默认值不返回总数，读到不足一页即结束。 */
  public List<Standard> defaults(String module, DefaultsRequest request) {
    var requested = ids(request.equipmentIds());
    if (requested.isEmpty()) {
      return List.of();
    }
    var query = ListQuery.from(Map.of());
    var equipment =
        repository
            .equipment(
                auth.tenantId(), query.at(1, ID_LIMIT), equipmentScope(module), requested, "")
            .data();
    if (equipment.size() != requested.size()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "设备不在可选范围内");
    }
    var byId = new HashMap<Long, Equipment>();
    for (var row : equipment) {
      byId.put(Long.valueOf(row.id()), row);
    }
    var parents = categoryParents();
    var result = new LinkedHashMap<String, Standard>();
    for (long id : requested) {
      var selected = selectStandards(module, query, applicable(byId.get(id), parents));
      for (int page = 1; ; page++) {
        var batch = selected.rows().apply(query.at(page, DEFAULTS_BATCH_SIZE));
        for (var row : batch) {
          result.putIfAbsent(row.id(), row);
        }
        if (batch.size() < DEFAULTS_BATCH_SIZE) {
          break;
        }
      }
    }
    return new ArrayList<>(result.values());
  }
}
