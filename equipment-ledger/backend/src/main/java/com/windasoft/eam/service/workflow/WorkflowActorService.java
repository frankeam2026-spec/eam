package com.windasoft.eam.service.workflow;

import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.system.RoleManagementRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import com.windasoft.eam.service.system.PermissionService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 节点配置有效性与进入节点时的公司候选解析；不授予业务办理或仓库权限。 */
@Service
@RequiredArgsConstructor
public class WorkflowActorService {
  private final SystemUserRepository users;
  private final RoleManagementRepository roles;
  private final PermissionService permissions;

  public void validate(long tenant, WorkflowDefinition definition) {
    var ids =
        definition.nodes().stream()
            .filter(n -> n.users() != null)
            .flatMap(n -> n.users().stream())
            .map(Long::valueOf)
            .distinct()
            .toList();
    var roleIds =
        definition.nodes().stream()
            .filter(n -> n.roles() != null)
            .flatMap(n -> n.roles().stream())
            .map(Long::valueOf)
            .collect(Collectors.toSet());
    fixedUsers(tenant, Long.parseLong(definition.companyId()), ids);
    if (!roles.activeRoleIds(tenant, roleIds).containsAll(roleIds)) {
      throw new IllegalArgumentException("审批角色不存在或已停用");
    }
  }

  /** 固定人员停用/跨公司须报错；角色只解析本公司当前有效成员，进入节点后另行保存快照。 */
  public Set<Long> candidates(
      long tenant, WorkflowDefinition definition, WorkflowDefinition.Node node) {
    long company = Long.parseLong(definition.companyId());
    var fixed =
        node.users() == null
            ? List.<Long>of()
            : node.users().stream().map(Long::valueOf).distinct().toList();
    var result = new TreeSet<>(fixedUsers(tenant, company, fixed));
    if (node.roles() == null || node.roles().isEmpty()) {
      return result;
    }
    var organizations = new HashSet<>(permissions.companyDepartments(company));
    organizations.add(company);
    organizations.removeIf(org -> !Objects.equals(permissions.companyOfOrganization(org), company));
    var roleIds = node.roles().stream().map(Long::valueOf).distinct().toList();
    users.activeRoleMembers(tenant, roleIds, List.copyOf(organizations)).stream()
        .map(SystemUser::getId)
        .forEach(result::add);
    return result;
  }

  private Set<Long> fixedUsers(long tenant, long company, List<Long> ids) {
    var rows = users.activeIdentities(tenant, ids);
    if (rows.size() != ids.size()) {
      throw new IllegalArgumentException("审批人员不存在或已停用");
    }
    Map<Long, Long> companies = new HashMap<>();
    for (var user : rows) {
      Long org = user.getOrgId();
      if (!companies.containsKey(org)) {
        companies.put(org, permissions.companyOfOrganization(org));
      }
      if (!Objects.equals(companies.get(org), company)) {
        throw new IllegalArgumentException("固定审批人必须属于模板适用公司");
      }
    }
    return rows.stream().map(SystemUser::getId).collect(Collectors.toSet());
  }
}
