package com.windasoft.eam.service.inspection;

import com.windasoft.eam.dto.common.*;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.common.SqlPredicate;
import com.windasoft.eam.repository.inspection.*;
import com.windasoft.eam.service.business.*;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InspectionListService {
  private final InspectionListRepository repository;
  private final InspectionRepository metadata;
  private final InspectionViewAssembler views;
  private final PermissionService permissions;
  private final OrganizationScopeService organizationScopes;
  private final AuthContext auth;
  private final BusinessQueryService customQuery;
  private final BusinessValueService values;

  public InspectionListService(
      InspectionListRepository repository,
      InspectionRepository metadata,
      InspectionViewAssembler views,
      PermissionService permissions,
      OrganizationScopeService organizationScopes,
      AuthContext auth,
      BusinessQueryService customQuery,
      BusinessValueService values) {
    this.repository = repository;
    this.metadata = metadata;
    this.views = views;
    this.permissions = permissions;
    this.organizationScopes = organizationScopes;
    this.auth = auth;
    this.customQuery = customQuery;
    this.values = values;
  }

  private PermissionScope scope(String kind, String action) {
    try {
      return permissions.inspection(kind, action);
    } catch (ResponseStatusException e) {
      if (e.getStatusCode().value() == 403
          && (kind.endsWith("tasks") || Set.of("daily", "periodic").contains(kind))
          && Set.of("查看", "审核").contains(action))
        return new PermissionScope(false, null, Set.of(), false);
      throw e;
    }
  }

  private String permissionKind(String kind) {
    return kind.endsWith("tasks") ? (kind.startsWith("daily") ? "daily" : "periodic") : kind;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> context(String kind) {
    String permissionKind = permissionKind(kind);
    var scope = scope(permissionKind, "查看");
    var organizations = metadata.listOrganizations(auth.tenantId());
    var byId = new HashMap<Long, Map<String, Object>>();
    var keep = new HashSet<Long>();
    for (var org : organizations) {
      long id = Long.parseLong(org.get("id").toString());
      byId.put(id, org);
      if (scope.matches(permissions.companyOfOrganization(id), id, null, auth.userId()))
        keep.add(id);
    }
    if (scope.owner() && auth.user().getOrgId() != null) keep.add(auth.user().getOrgId());
    if (kind.endsWith("tasks")) {
      var q = ListQuery.from(Map.of());
      var selected =
          repository.select(
              auth.tenantId(),
              auth.userId(),
              kind,
              scope,
              scope(permissionKind, "审核"),
              false,
              null,
              q,
              SqlPredicate.of("1=1"),
              null);
      keep.addAll(repository.taskOrganizationIds(selected));
    }
    for (long id : new ArrayList<>(keep)) {
      var seen = new HashSet<Long>();
      var node = byId.get(id);
      while (node != null && seen.add(Long.parseLong(node.get("id").toString()))) {
        keep.add(Long.parseLong(node.get("id").toString()));
        var parent = Objects.toString(node.get("parentId"), "");
        node = parent.isBlank() ? null : byId.get(Long.parseLong(parent));
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
    for (String kind :
        List.of("daily-tasks", "periodic-tasks", "standards", "daily-plans", "periodic-plans")) {
      try {
        String permissionKind = permissionKind(kind);
        var scope = scope(permissionKind, "查看");
        var selected =
            repository.select(
                auth.tenantId(),
                auth.userId(),
                kind,
                scope,
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
    return query(kind, query, export, null);
  }

  public PageResult<Map<String, Object>> query(
      String kind, ListQuery query, boolean export, Set<Long> selectedIds) {
    String permissionKind = permissionKind(kind), object = BusinessObjects.inspection(kind);
    var scope = scope(permissionKind, export ? "导出" : "查看");
    Set<Long> organizations = null;
    if (!query.organization().isBlank()) {
      try {
        long id = Long.parseLong(query.organization());
        organizations = new HashSet<>(organizationScopes.queryTree(id));
        organizations.add(id);
      } catch (NumberFormatException e) {
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.inspection.message0015");
      }
    }
    var review = kind.endsWith("tasks") ? scope(permissionKind, "审核") : scope;
    var selected =
        repository.select(
            auth.tenantId(),
            auth.userId(),
            kind,
            scope,
            review,
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
              scope(permissionKind, "查看"),
              review,
              false,
              organizations,
              query,
              SqlPredicate.of("1=1"),
              null);
      selected = repository.restrict(selected, read.predicate());
    }
    if (selectedIds != null)
      selected =
          repository.restrict(
              selected, com.windasoft.eam.repository.common.SqlScopes.in("t.id", selectedIds));
    var rows =
        repository.page(selected, query).stream()
            .map(kind.endsWith("tasks") ? views::task : views::definition)
            .toList();
    values.attach(object, rows);
    return new PageResult<>(rows, query.page(), query.size(), repository.count(selected));
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> deviceTasks(
      String equipmentId, String code, int page, int size) {
    var scope = permissions.inspection("daily", "执行");
    var query =
        ListQuery.from(
            Map.of(
                "page",
                Integer.toString(Math.max(1, page)),
                "size",
                Integer.toString(Math.max(1, Math.min(100, size)))));
    com.windasoft.eam.common.inspection.InspectionSupport.require(
        !equipmentId.isBlank() || !code.isBlank(),
        com.windasoft.eam.common.MessageContext.text("staff.deviceRequired"));
    SqlPredicate match = repository.deviceMatch(equipmentId, code);
    var selected =
        repository.select(
            auth.tenantId(),
            auth.userId(),
            "daily-tasks",
            scope,
            scope,
            false,
            null,
            query,
            match,
            null);
    return new PageResult<>(
        repository.page(selected, query).stream().map(views::task).toList(),
        query.page(),
        query.size(),
        repository.count(selected));
  }
}
