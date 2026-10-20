package com.windasoft.eam.service.maintenance;

import com.windasoft.eam.dto.common.*;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.common.SqlPredicate;
import com.windasoft.eam.repository.maintenance.MaintenanceListRepository;
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
  private final com.windasoft.eam.repository.maintenance.MaintenanceRepository metadata;
  private final MaintenanceViewAssembler views;
  private final PermissionService permissions;
  private final OrganizationScopeService organizationScopes;
  private final AuthContext auth;
  private final BusinessQueryService customQuery;
  private final BusinessValueService values;

  public MaintenanceListService(
      com.windasoft.eam.repository.maintenance.MaintenanceRepository metadata,
      MaintenanceListRepository repository,
      MaintenanceViewAssembler views,
      PermissionService permissions,
      OrganizationScopeService organizationScopes,
      AuthContext auth,
      BusinessQueryService customQuery,
      BusinessValueService values) {
    this.metadata = metadata;
    this.repository = repository;
    this.views = views;
    this.permissions = permissions;
    this.organizationScopes = organizationScopes;
    this.auth = auth;
    this.customQuery = customQuery;
    this.values = values;
  }

  private PermissionScope scope(String kind, String action) {
    try {
      return permissions.maintenance(kind, action);
    } catch (ResponseStatusException error) {
      if (kind.equals("tasks")
          && action.equals("查看")
          && error.getStatusCode() == HttpStatus.FORBIDDEN)
        return new PermissionScope(false, null, Set.of(), false);
      throw error;
    }
  }

  @Transactional(readOnly = true)
  public Map<String, Object> context(String kind) {
    var scope = scope(kind, "查看");
    var organizations = metadata.listOrganizations(auth.tenantId());
    var keep = new HashSet<Long>();
    var byId = new HashMap<Long, Map<String, Object>>();
    for (var org : organizations) {
      long id = Long.parseLong(org.get("id").toString());
      byId.put(id, org);
      if (scope.matches(permissions.companyOfOrganization(id), id, null, auth.userId()))
        keep.add(id);
    }
    if (scope.owner() && auth.user().getOrgId() != null) keep.add(auth.user().getOrgId());
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
      while (node != null && seen.add(Long.parseLong(node.get("id").toString()))) {
        keep.add(Long.parseLong(node.get("id").toString()));
        var parent = node.get("parentId");
        node = parent == null ? null : byId.get(Long.parseLong(parent.toString()));
      }
    }
    return Map.of(
        "organizations",
        organizations.stream()
            .filter(row -> keep.contains(Long.parseLong(row.get("id").toString())))
            .toList());
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
        if (repository.referencesFile(selected, id)) return true;
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() != 403) throw denied;
      }
    }
    return false;
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> query(String kind, ListQuery query) {
    return query(kind, query, false);
  }

  public PageResult<Map<String, Object>> query(String kind, ListQuery query, boolean export) {
    var scope = scope(kind, export ? "导出" : "查看");
    String object =
        kind.equals("tasks")
            ? "maintenance_task"
            : kind.equals("plans") ? "maintenance_plan" : null;
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
              scope(kind, "查看"),
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
        if (denied.getStatusCode().value() != 403) throw denied;
        acceptance = new PermissionScope(false, null, Set.of(), false);
      }
      selected = repository.restrict(selected, repository.acceptance(acceptance, auth.userId()));
    }
    var rows =
        repository.page(selected, query).stream()
            .map(kind.equals("tasks") ? views::task : views::definition)
            .toList();
    values.attach(object, rows);
    return new PageResult<>(rows, query.page(), query.size(), repository.count(selected));
  }
}
