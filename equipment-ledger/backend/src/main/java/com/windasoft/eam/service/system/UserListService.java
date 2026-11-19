package com.windasoft.eam.service.system;

import com.windasoft.eam.domain.common.ActivationStatus;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.dto.system.UserListView;
import com.windasoft.eam.dto.system.UserQueryRow;
import com.windasoft.eam.dto.system.UserSummary;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.repository.system.RoleManagementRepository;
import com.windasoft.eam.repository.system.UserListRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 用户列表查询：统一筛选、分页、汇总及导出，不执行账号状态变更。 */
@Service
public class UserListService {
  private final UserListRepository repository;
  private final RoleManagementRepository roles;
  private final OrgUnitRepository orgUnits;
  private final PermissionService permissions;
  private final OrganizationScopeService organizationScopes;
  private final AuthContext auth;

  public UserListService(
      UserListRepository repository,
      RoleManagementRepository roles,
      OrgUnitRepository orgUnits,
      PermissionService permissions,
      OrganizationScopeService organizationScopes,
      AuthContext auth) {
    this.repository = repository;
    this.roles = roles;
    this.orgUnits = orgUnits;
    this.permissions = permissions;
    this.organizationScopes = organizationScopes;
    this.auth = auth;
  }

  /** 公司范围沿最近所属公司展开；选中组织和导出范围只与查看权限取交集，不能扩大结果。 */
  private UserListRepository.Selection select(ListQuery query, boolean export) {
    var scope = permissions.scope("user", "查看");
    var visible = new HashSet<>(scope.departmentIds());
    // 同次查看/导出复用一个可信组织快照，避免逐组织及逐祖先访问数据库。
    var organizations = orgUnits.organizations(auth.tenantId());
    var companies = organizationScopes.companiesForOrganizations(organizations);
    for (var org : organizations) {
      Long company = companies.get(org.getId());
      if (company != null && scope.companyIds().contains(company)) {
        visible.add(org.getId());
      }
    }
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
        for (var org : organizations) {
          Long company = companies.get(org.getId());
          if (company != null && exportScope.companyIds().contains(company)) {
            exportOrgs.add(org.getId());
          }
        }
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
    var ids = raw.stream().map(UserQueryRow::id).toList();
    var links = roles.userRolesForUsers(auth.tenantId(), ids);
    var rows =
        raw.stream()
            .map(
                row ->
                    new UserListView(
                        Long.toString(row.id()),
                        row.account(),
                        row.name(),
                        row.employeeNo(),
                        row.phone(),
                        row.orgId() == null ? "" : row.orgId().toString(),
                        links.getOrDefault(row.id(), List.of()).stream()
                            .map(link -> link.getRoleId().toString())
                            .toList(),
                        ActivationStatus.fromFlag(row.status()).label(),
                        row.department(),
                        row.roles(),
                        row.email(),
                        row.emailVersion()))
            .toList();
    return new PageResult<>(rows, query.page(), query.size(), repository.count(selection));
  }

  @Transactional(readOnly = true)
  public UserSummary summary() {
    try {
      return new UserSummary(repository.roleCounts(select(ListQuery.from(Map.of()), false)));
    } catch (ResponseStatusException denied) {
      if (denied.getStatusCode().value() != 403) throw denied;
      return new UserSummary(Map.of());
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
}
