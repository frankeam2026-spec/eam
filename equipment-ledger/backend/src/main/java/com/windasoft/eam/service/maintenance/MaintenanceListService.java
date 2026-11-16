package com.windasoft.eam.service.maintenance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.common.*;
import com.windasoft.eam.dto.knowledge.AiBusinessPlan;
import com.windasoft.eam.dto.maintenance.MaintenanceAssistantRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceAssistantResult;
import com.windasoft.eam.dto.maintenance.MaintenanceCustomFields;
import com.windasoft.eam.dto.maintenance.MaintenanceListContext;
import com.windasoft.eam.dto.maintenance.MaintenanceOrganizationOption;
import com.windasoft.eam.dto.maintenance.MaintenancePlanRow;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskView;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.common.SqlPredicate;
import com.windasoft.eam.repository.maintenance.MaintenanceListRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.service.business.*;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MaintenanceListService {
  private final MaintenanceListRepository repository;
  private final OrgUnitRepository metadata;
  private final MaintenanceViewAssembler views;
  private final PermissionService permissions;
  private final OrganizationScopeService organizationScopes;
  private final AuthContext auth;
  private final BusinessQueryService customQuery;
  private final BusinessValueService values;
  private final ObjectMapper json;

  public MaintenanceListService(
      OrgUnitRepository metadata,
      MaintenanceListRepository repository,
      MaintenanceViewAssembler views,
      PermissionService permissions,
      OrganizationScopeService organizationScopes,
      AuthContext auth,
      BusinessQueryService customQuery,
      BusinessValueService values,
      ObjectMapper json) {
    this.metadata = metadata;
    this.repository = repository;
    this.views = views;
    this.permissions = permissions;
    this.organizationScopes = organizationScopes;
    this.auth = auth;
    this.customQuery = customQuery;
    this.values = values;
    this.json = json;
  }

  private PermissionScope scope(String kind, String action) {
    try {
      return permissions.maintenance(kind, action);
    } catch (ResponseStatusException error) {
      if (kind.equals("tasks")
          && action.equals("查看")
          && error.getStatusCode() == HttpStatus.FORBIDDEN) {
        return new PermissionScope(false, null, Set.of(), false);
      }
      throw error;
    }
  }

  @Transactional(readOnly = true)
  public MaintenanceListContext context(String kind) {
    var scope = scope(kind, "查看");
    var organizations =
        metadata.organizations(auth.tenantId(), true).stream()
            .map(row -> views.organization(row, false))
            .toList();
    var keep = new HashSet<Long>();
    var byId = new HashMap<Long, MaintenanceOrganizationOption>();
    for (var org : organizations) {
      long id = Long.parseLong(org.id());
      byId.put(id, org);
      if (scope.matches(permissions.companyOfOrganization(id), id, null, auth.userId())) {
        keep.add(id);
      }
    }
    if (scope.owner() && auth.user().getOrgId() != null) {
      keep.add(auth.user().getOrgId());
    }
    if (kind.equals("tasks")) {
      var q = ListQuery.from(Map.of());
      var selected =
          repository.select(
              auth.tenantId(),
              auth.userId(),
              kind,
              scope,
              false,
              null,
              q,
              com.windasoft.eam.repository.common.SqlPredicate.of("1=1"),
              null);
      keep.addAll(repository.taskOrganizationIds(selected));
    }
    for (long id : new ArrayList<>(keep)) {
      var seen = new HashSet<Long>();
      var node = byId.get(id);
      while (node != null && seen.add(Long.parseLong(node.id()))) {
        keep.add(Long.parseLong(node.id()));
        var parent = node.parentId();
        node = parent == null ? null : byId.get(Long.parseLong(parent.toString()));
      }
    }
    return new MaintenanceListContext(
        organizations.stream().filter(row -> keep.contains(Long.parseLong(row.id()))).toList());
  }

  @Transactional(readOnly = true)
  public boolean hasFile(String id) {
    for (String kind : List.of("tasks", "standards", "plans")) {
      try {
        var scope = scope(kind, "查看");
        var selected =
            repository.select(
                auth.tenantId(),
                auth.userId(),
                kind,
                scope,
                false,
                null,
                ListQuery.from(Map.of()),
                SqlPredicate.of("1=1"),
                null);
        if (repository.referencesFile(selected, id)) {
          return true;
        }
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() != 403) {
          throw denied;
        }
      }
    }
    return false;
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> query(String kind, ListQuery query) {
    return query(kind, query, false);
  }

  /** 与保养列表共用查看范围；内部计划也重新校验白名单，不能通过直接构造请求绕过。 */
  @Transactional(readOnly = true)
  public MaintenanceAssistantResult queryAssistant(MaintenanceAssistantRequest request) {
    var plan = AiBusinessPlan.parse(json.valueToTree(request.plan()));
    var verified =
        new MaintenanceAssistantRequest(
            plan,
            request.equipmentId(),
            request.page(),
            request.size(),
            request.evaluatedAt(),
            request.start(),
            request.end());
    String kind = verified.kind();
    permissions.require(PermissionService.maintenanceMenu(kind), "查看");
    var selected =
        repository.select(
            auth.tenantId(),
            auth.userId(),
            kind,
            scope(kind, "查看"),
            false,
            null,
            ListQuery.from(Map.of()),
            SqlPredicate.of("1=1"),
            null);
    return repository.queryAssistant(selected, verified, auth.tenantId(), auth.userId());
  }

  /** 权限与组织筛选的事实；不传SQL，供逐步类型化的列表链复用同一授权口径。 */
  public record Access(PermissionScope operation, PermissionScope read, Set<Long> organizations) {
    public Access {
      if (organizations != null) {
        organizations = Set.copyOf(organizations);
      }
    }
  }

  public Access access(String kind, ListQuery query, boolean export) {
    var scope = scope(kind, export ? "导出" : "查看");
    Set<Long> organizations = null;
    if (!query.organization().isBlank()) {
      try {
        organizations =
            new HashSet<>(organizationScopes.queryTree(Long.parseLong(query.organization())));
        organizations.add(Long.parseLong(query.organization()));
      } catch (NumberFormatException e) {
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.inspection.message0015");
      }
    }
    return new Access(scope, export ? scope(kind, "查看") : null, organizations);
  }

  public PageResult<Map<String, Object>> query(String kind, ListQuery query, boolean export) {
    if (kind.equals("plans")) {
      var result = queryPlans(query, export);
      // 旧聚合/CSV仍接受Map，仅在兼容输出边界转换，查询及扩展字段批量读取使用明确类型。
      var rows =
          result.data().stream()
              .map(row -> json.convertValue(row, new TypeReference<Map<String, Object>>() {}))
              .toList();
      return new PageResult<>(rows, result.page());
    }
    if (kind.equals("tasks")) {
      var result = queryTasks(query, export);
      return new PageResult<>(
          result.data().stream()
              .map(row -> json.convertValue(row, new TypeReference<Map<String, Object>>() {}))
              .toList(),
          result.page());
    }
    var selected = select(kind, query, export);
    var rows = repository.page(selected, query).stream().map(views::definition).toList();
    values.attach(objectType(kind), rows);
    return new PageResult<>(rows, query.page(), query.size(), repository.count(selected));
  }

  /** 任务列表与导出复用原授权选择器，扩展字段按整页批量取得，兼容转换只在旧CSV输出边界。 */
  @Transactional(readOnly = true)
  public PageResult<MaintenanceTaskView> queryTasks(ListQuery query, boolean export) {
    var selected = select("tasks", query, export);
    var rows = repository.taskRows(selected, query);
    var extra =
        rows.isEmpty()
            ? Map.<Long, BusinessValueService.Values>of()
            : values.batch("maintenance_task", rows.stream().map(row -> row.id()).toList());
    var result =
        rows.stream()
            .map(
                row -> {
                  var view = views.task(row);
                  var value = extra.get(row.id());
                  if (value != null) {
                    view.setCustom(
                        new MaintenanceCustomFields(
                            json.valueToTree(value.customFields()),
                            json.valueToTree(value.customFieldLabels()),
                            value.customSchemaVersion()));
                  }
                  return view;
                })
            .toList();
    return new PageResult<>(result, query.page(), query.size(), repository.count(selected));
  }

  /** 计划固定行模型沿用原授权/筛选/排序；一次批量加载扩展字段，不逐行查询。 */
  @Transactional(readOnly = true)
  public PageResult<MaintenancePlanRow> queryPlans(ListQuery query, boolean export) {
    var selected = select("plans", query, export);
    var rows = repository.planRows(selected, query);
    var extra =
        rows.isEmpty()
            ? Map.<Long, BusinessValueService.Values>of()
            : values.batch(
                "maintenance_plan",
                rows.stream().map(row -> Long.parseLong(row.definition().getId())).toList());
    var result =
        rows.stream()
            .map(
                row -> {
                  var value = extra.get(Long.parseLong(row.definition().getId()));
                  return row.withCustom(
                      value == null
                          ? null
                          : new MaintenanceCustomFields(
                              json.valueToTree(value.customFields()),
                              json.valueToTree(value.customFieldLabels()),
                              value.customSchemaVersion()));
                })
            .toList();
    return new PageResult<>(result, query.page(), query.size(), repository.count(selected));
  }

  private static String objectType(String kind) {
    return kind.equals("tasks")
        ? "maintenance_task"
        : kind.equals("plans") ? "maintenance_plan" : null;
  }

  private MaintenanceListRepository.Selection select(String kind, ListQuery query, boolean export) {
    var access = access(kind, query, export);
    var scope = access.operation();
    var organizations = access.organizations();
    String object = objectType(kind);
    var selected =
        repository.select(
            auth.tenantId(),
            auth.userId(),
            kind,
            scope,
            export,
            organizations,
            query,
            customQuery.predicate(object, query.conditions(), "t"),
            object == null ? null : customQuery.orderExpression(object, query.sortBy(), "t"));
    if (export) {
      var read =
          repository.select(
              auth.tenantId(),
              auth.userId(),
              kind,
              access.read(),
              false,
              organizations,
              query,
              com.windasoft.eam.repository.common.SqlPredicate.of("1=1"),
              null);
      selected = repository.restrict(selected, read.predicate());
    }
    if (kind.equals("tasks") && query.queue().equals("待我验收")) {
      PermissionScope acceptance;
      try {
        acceptance = permissions.maintenance("验收");
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() != 403) {
          throw denied;
        }
        acceptance = new PermissionScope(false, null, Set.of(), false);
      }
      selected = repository.restrict(selected, repository.acceptance(acceptance, auth.userId()));
    }
    return selected;
  }
}
