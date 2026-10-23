package com.windasoft.eam.service.system;

import com.windasoft.eam.dto.common.*;
import com.windasoft.eam.dto.system.*;
import com.windasoft.eam.repository.system.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserListService {
  private final UserListRepository repository;
  private final SystemManagementRepository metadata;
  private final PermissionService permissions;
  private final OrganizationScopeService organizationScopes;
  private final AuthContext auth;

  public UserListService(
      UserListRepository repository,
      SystemManagementRepository metadata,
      PermissionService permissions,
      OrganizationScopeService organizationScopes,
      AuthContext auth) {
    this.repository = repository;
    this.metadata = metadata;
    this.permissions = permissions;
    this.organizationScopes = organizationScopes;
    this.auth = auth;
  }

  private UserListRepository.Selection select(ListQuery query, boolean export) {
    var scope = permissions.scope("user", "查看");
    var visible = new HashSet<>(scope.departmentIds());
    for (var org : metadata.organizations(auth.tenantId()))
      if (permissions.companyOfOrganization(org.getId()) != null
          && scope.companyIds().contains(permissions.companyOfOrganization(org.getId())))
        visible.add(org.getId());
    Set<Long> selected = null;
    if (!query.organization().isBlank()) {
      try {
        long id = Long.parseLong(query.organization());
        selected = new HashSet<>(organizationScopes.queryTree(id));
        selected.add(id);
      } catch (NumberFormatException e) {
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.inspection.message0015");
      }
    }
    if (export) {
      var exportScope = permissions.scope("user", "导出");
      if (!exportScope.all()) {
        var exportOrgs = new HashSet<>(exportScope.departmentIds());
        for (var org : metadata.organizations(auth.tenantId()))
          if (permissions.companyOfOrganization(org.getId()) != null
              && exportScope.companyIds().contains(permissions.companyOfOrganization(org.getId())))
            exportOrgs.add(org.getId());
        if (scope.all()) visible = exportOrgs;
        else visible.retainAll(exportOrgs);
        scope = new PermissionScope(false, null, visible, false);
      }
    }
    return repository.select(
        auth.tenantId(), query, scope.all(), visible, selected, permissions.allowed("role", "查看"));
  }

  @Transactional(readOnly = true)
  public PageResult<UserListView> query(ListQuery query) {
    var selection = select(query, false);
    return page(selection, query);
  }

  private PageResult<UserListView> page(UserListRepository.Selection selection, ListQuery query) {
    var raw = repository.page(selection, query);
    var ids = raw.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
    var roles = new HashMap<Long, List<String>>();
    if (!ids.isEmpty())
      for (var link : repository.roleLinks(auth.tenantId(), ids))
        roles
            .computeIfAbsent(((Number) link.get("user_id")).longValue(), key -> new ArrayList<>())
            .add(link.get("role_id").toString());
    var rows =
        raw.stream()
            .map(
                row ->
                    new UserListView(
                        text(row, "id"),
                        text(row, "account"),
                        text(row, "display_name"),
                        text(row, "employee_no"),
                        text(row, "phone"),
                        text(row, "org_id"),
                        roles.getOrDefault(((Number) row.get("id")).longValue(), List.of()),
                        Integer.parseInt(text(row, "status")) == 1 ? "启用" : "停用",
                        text(row, "department"),
                        text(row, "roles"), text(row, "email"), ((Number)row.get("email_version")).intValue()))
            .toList();
    return new PageResult<>(rows, query.page(), query.size(), repository.count(selection));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> summary() {
    try {
      return Map.of("roleCounts", repository.roleCounts(select(ListQuery.from(Map.of()), false)));
    } catch (ResponseStatusException denied) {
      if (denied.getStatusCode().value() != 403) throw denied;
      return Map.of("roleCounts", Map.of());
    }
  }

  @Transactional(readOnly = true)
  public List<UserListView> export(ListQuery query) {
    var selection = select(query, true);
    var result = new ArrayList<UserListView>();
    for (int number = 1; ; number++) {
      var batch = page(selection, query.at(number, 200));
      result.addAll(batch.data());
      if ((long) number * 200 >= batch.page().total()) return result;
    }
  }

  private static String text(Map<String, Object> row, String key) {
    return Objects.toString(row.get(key), "");
  }
}
