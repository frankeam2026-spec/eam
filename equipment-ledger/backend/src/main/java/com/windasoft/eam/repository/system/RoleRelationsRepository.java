package com.windasoft.eam.repository.system;

import com.windasoft.eam.entity.system.RoleLegacyAction;
import com.windasoft.eam.entity.system.RoleMenuAction;
import com.windasoft.eam.entity.system.RoleMenuPermission;
import com.windasoft.eam.entity.system.RoleMenuScope;
import com.windasoft.eam.entity.system.RoleMenuTarget;
import com.windasoft.eam.entity.system.RoleOrganization;
import com.windasoft.eam.entity.system.SystemRole;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** 只批量读取关系并装配权限投影，不负责跨表写入，不再经JSON序列化往返。 */
@Repository
public class RoleRelationsRepository {
  private final RoleOrganizationRepository organizations;
  private final RoleLegacyActionRepository legacy;
  private final RoleMenuActionRepository actions;
  private final RoleMenuScopeRepository scopes;
  private final RoleMenuTargetRepository targets;

  public RoleRelationsRepository(
      RoleOrganizationRepository organizations,
      RoleLegacyActionRepository legacy,
      RoleMenuActionRepository actions,
      RoleMenuScopeRepository scopes,
      RoleMenuTargetRepository targets) {
    this.organizations = organizations;
    this.legacy = legacy;
    this.actions = actions;
    this.scopes = scopes;
    this.targets = targets;
  }

  public List<SystemRole> roles(List<SystemRole> rows) {
    for (var group :
        rows.stream().collect(Collectors.groupingBy(SystemRole::getTenantId)).entrySet()) {
      var ids = group.getValue().stream().map(SystemRole::getId).toList();
      var orgs =
          organizations.find(group.getKey(), ids).stream()
              .collect(
                  Collectors.groupingBy(
                      RoleOrganization::getRoleId,
                      Collectors.mapping(RoleOrganization::getOrgId, Collectors.toList())));
      var old =
          legacy.find(group.getKey(), ids).stream()
              .collect(
                  Collectors.groupingBy(
                      RoleLegacyAction::getRoleId,
                      Collectors.mapping(
                          v -> v.getMenuKey() + "." + v.getActionCode(), Collectors.toList())));
      for (var row : group.getValue()) {
        row.setOrganizationIds(orgs.getOrDefault(row.getId(), List.of()));
        row.setLegacyActions(old.getOrDefault(row.getId(), List.of()));
      }
    }
    return rows;
  }

  public SystemRole role(SystemRole row) {
    if (row != null) roles(List.of(row));
    return row;
  }

  public List<RoleMenuPermission> grants(List<RoleMenuPermission> rows) {
    for (var group :
        rows.stream().collect(Collectors.groupingBy(RoleMenuPermission::getTenantId)).entrySet()) {
      var ids = group.getValue().stream().map(RoleMenuPermission::getId).toList();
      var actionMap =
          actions.find(group.getKey(), ids).stream()
              .collect(
                  Collectors.groupingBy(
                      RoleMenuAction::getGrantId,
                      Collectors.mapping(RoleMenuAction::getActionName, Collectors.toList())));
      var scopeMap =
          scopes.find(group.getKey(), ids).stream()
              .collect(
                  Collectors.groupingBy(
                      RoleMenuScope::getGrantId,
                      Collectors.mapping(RoleMenuScope::getScopeCode, Collectors.toList())));
      var targetMap =
          targets.find(group.getKey(), ids).stream()
              .collect(
                  Collectors.groupingBy(
                      RoleMenuTarget::getGrantId,
                      Collectors.mapping(RoleMenuTarget::getTargetId, Collectors.toList())));
      for (var row : group.getValue()) {
        row.setActions(actionMap.getOrDefault(row.getId(), List.of()));
        row.setDataScopes(scopeMap.getOrDefault(row.getId(), List.of()));
        row.setTargetIds(targetMap.getOrDefault(row.getId(), List.of()));
      }
    }
    return rows;
  }
}
