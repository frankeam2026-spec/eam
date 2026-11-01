package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.windasoft.eam.entity.system.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class RoleManagementRepository {
  private final RoleRelationsRepository relations;
  private final SystemRoleMapper roles;
  private final UserRoleLinkMapper links;
  private final RoleMenuPermissionMapper grants;

  public RoleManagementRepository(RoleRelationsRepository relations, SystemRoleMapper roles,
      UserRoleLinkMapper links, RoleMenuPermissionMapper grants) {
    this.relations = relations;
    this.roles = roles;
    this.links = links;
    this.grants = grants;
  }

  public List<SystemRole> roles(long tenant) {
    return relations.roles(
        roles.selectList(
            new LambdaQueryWrapper<SystemRole>()
                .eq(SystemRole::getTenantId, tenant)
                .orderByAsc(SystemRole::getId)));
  }

  public SystemRole role(long tenant, Long id) {
    return relations.role(
        roles.selectOne(
            new LambdaQueryWrapper<SystemRole>()
                .eq(SystemRole::getTenantId, tenant)
                .eq(SystemRole::getId, id)));
  }

  public int insertRole(SystemRole x) {
    int changed = roles.insert(x);
    if (changed > 0) relations.saveRole(x);
    return changed;
  }

  public int updateRole(SystemRole x) {
    int changed = roles.updateById(x);
    if (changed > 0) relations.saveRole(x);
    return changed;
  }

  public int deleteRole(Long id) {
    return roles.deleteById(id);
  }

  public long roleCodeCount(long tenant, String code, Long current) {
    var q =
        new LambdaQueryWrapper<SystemRole>()
            .eq(SystemRole::getTenantId, tenant)
            .eq(SystemRole::getCode, code.trim());
    if (current != null) q.ne(SystemRole::getId, current);
    return roles.selectCount(q);
  }

  public long roleCount(long tenant, List<Long> ids) {
    return roles.selectCount(
        new LambdaQueryWrapper<SystemRole>()
            .eq(SystemRole::getTenantId, tenant)
            .in(SystemRole::getId, ids));
  }

  public long roleUserCount(long tenant, Long id) {
    return links.selectCount(
        new QueryWrapper<UserRoleLink>().eq("tenant_id", tenant).eq("role_id", id));
  }

  public List<UserRoleLink> userRoles(long tenant, Long id) {
    return links.selectList(
        new QueryWrapper<UserRoleLink>().eq("tenant_id", tenant).eq("user_id", id));
  }

  public void deleteUserRoles(long tenant, Long id) {
    links.delete(new QueryWrapper<UserRoleLink>().eq("tenant_id", tenant).eq("user_id", id));
  }

  public void insertUserRole(UserRoleLink link) {
    links.insert(link);
  }

  public List<RoleMenuPermission> menuPermissions(long tenant, Long id) {
    return relations.grants(
        grants.selectList(
            new LambdaQueryWrapper<RoleMenuPermission>()
                .eq(RoleMenuPermission::getTenantId, tenant)
                .eq(RoleMenuPermission::getRoleId, id)
                .orderByAsc(RoleMenuPermission::getId)));
  }

  public void deleteMenuPermissions(long tenant, Long id) {
    grants.delete(new QueryWrapper<RoleMenuPermission>().eq("tenant_id", tenant).eq("role_id", id));
  }

  public void insertMenuPermission(RoleMenuPermission row) {
    grants.insert(row);
    relations.saveGrant(row);
  }
}
