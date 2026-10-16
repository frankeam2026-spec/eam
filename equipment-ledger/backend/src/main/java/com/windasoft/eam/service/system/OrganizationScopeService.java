package com.windasoft.eam.service.system;

import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.repository.system.AuthorizationRepository;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OrganizationScopeService {
  private final AuthContext auth;
  private final AuthorizationRepository repository;

  public OrganizationScopeService(AuthContext auth, AuthorizationRepository repository) {
    this.auth = auth;
    this.repository = repository;
  }

  public Long companyOfOrganization(Long orgId) {
    return companyOfOrganization(auth.tenantId(), orgId);
  }

  public Long companyOfOrganization(long tenant, Long orgId) {
    var visited = new HashSet<Long>();
    while (orgId != null && visited.add(orgId)) {
      var org = repository.organization(tenant, orgId);
      if (org == null) return null;
      if (Set.of("公司", "集团").contains(org.getOrgType())) return org.getId();
      orgId = org.getParentId();
    }
    return null;
  }

  public Long companyOfUser() {
    return companyOfOrganization(auth.user().getOrgId());
  }

  public Set<Long> companyDepartments(Long companyId) {
    if (companyId == null) return Set.of();
    var rows = repository.organizations(auth.tenantId());
    var result = new HashSet<Long>();
    var parents = new HashSet<Long>();
    parents.add(companyId);
    boolean changed;
    do {
      changed = false;
      for (var org : rows)
        if ("部门".equals(org.getOrgType())
            && parents.contains(org.getParentId())
            && result.add(org.getId())) {
          parents.add(org.getId());
          changed = true;
        }
    } while (changed);
    return result;
  }

  public OrgUnit requireDepartment(Long id) {
    var org = repository.activeDepartment(auth.tenantId(), id);
    if (org == null || companyOfOrganization(id) == null)
      throw new IllegalArgumentException("所属部门不存在、已停用或未归属公司");
    return org;
  }

  public OrgUnit requireDepartment(String companyName, String departmentName) {
    var company = repository.companyNamed(auth.tenantId(), companyName);
    if (company == null) throw new IllegalArgumentException("所属公司不在当前租户内");
    var matches =
        repository.departmentsNamed(auth.tenantId(), departmentName).stream()
            .filter(d -> Objects.equals(companyOfOrganization(d.getId()), company.getId()))
            .toList();
    if (matches.size() != 1) throw new IllegalArgumentException("请通过所属部门选择框选择唯一的部门");
    var department = matches.getFirst();
    return department;
  }

  public Long userIdByName(String name) {
    if (name == null || name.isBlank()) return null;
    var matches = repository.usersNamed(auth.tenantId(), name.trim());
    if (matches.size() > 1) throw new IllegalArgumentException("该姓名对应多个员工，请选择唯一的负责人");
    return matches.isEmpty() ? null : matches.getFirst().getId();
  }

  public Set<Long> companyTree(Long root) {
    return descendants(root, true);
  }

  public Set<Long> departmentTree(Long root) {
    return descendants(root, false);
  }

  private Set<Long> descendants(Long root, boolean companies) {
    if (root == null) return Set.of();
    var rows = repository.organizations(auth.tenantId());
    var rootNode = rows.stream().filter(o -> root.equals(o.getId())).findFirst().orElse(null);
    if (rootNode == null || companies == "部门".equals(rootNode.getOrgType())) return Set.of();
    Set<Long> result = new HashSet<>(Set.of(root));
    boolean changed;
    do {
      changed = false;
      for (var row : rows) {
        boolean company = Set.of("公司", "集团").contains(row.getOrgType());
        if (company == companies && result.contains(row.getParentId()) && result.add(row.getId()))
          changed = true;
      }
    } while (changed);
    return result;
  }
}
