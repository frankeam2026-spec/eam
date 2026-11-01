package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.windasoft.eam.entity.basedata.BaseItem;
import com.windasoft.eam.entity.system.AuthSession;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.entity.system.RoleMenuPermission;
import com.windasoft.eam.entity.system.SystemRole;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.entity.system.UserRoleLink;
import com.windasoft.eam.repository.basedata.BaseItemMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SystemManagementRepository {
  public Map<String, Object> emailProfile(long tenant, long user) {
    return db.queryForMap(
        "SELECT email,email_version FROM eam_sys_user WHERE tenant_id=? AND id=?",
        tenant,
        user);
  }

  public int updateEmail(long tenant, long user, String email, int expectedVersion) {
    return db.update(
        "UPDATE eam_sys_user SET email=?,email_version=email_version+1"
            + " WHERE tenant_id=? AND id=? AND email_version=? AND deleted=0",
        email,
        tenant,
        user,
        expectedVersion);
  }

  private final RoleRelationsRepository relations;
  private final OrgUnitMapper orgs;
  private final SystemUserMapper users;
  private final SystemRoleMapper roles;
  private final UserRoleLinkMapper links;
  private final RoleMenuPermissionMapper grants;
  private final AuthSessionMapper sessions;
  private final BaseItemMapper locations;
  private final JdbcTemplate db;

  public SystemManagementRepository(
      OrgUnitMapper orgs,
      SystemUserMapper users,
      SystemRoleMapper roles,
      UserRoleLinkMapper links,
      RoleMenuPermissionMapper grants,
      AuthSessionMapper sessions,
      BaseItemMapper locations,
      JdbcTemplate db,
      RoleRelationsRepository relations) {
    this.relations = relations;
    this.orgs = orgs;
    this.users = users;
    this.roles = roles;
    this.links = links;
    this.grants = grants;
    this.sessions = sessions;
    this.locations = locations;
    this.db = db;
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





  public long locationCount(long tenant, Long id) {
    return locations.selectCount(
        new LambdaQueryWrapper<BaseItem>()
            .eq(BaseItem::getTenantId, tenant)
            .eq(BaseItem::getItemType, "LOCATION")
            .eq(BaseItem::getCompanyOrgId, id));
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

  public void revokeSessions(long tenant, Long id) {
    sessions.delete(new QueryWrapper<AuthSession>().eq("tenant_id", tenant).eq("user_id", id));
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



  public boolean businessReferences(long tenant, Long id) {

    for (String query :
        List.of(
            "SELECT COUNT(*) FROM eam_equipment WHERE tenant_id=? AND (company_org_id=? OR"
                + " department_org_id=?)",
            "SELECT COUNT(*) FROM eam_repair_order WHERE tenant_id=? AND department_org_id=?",
            "SELECT COUNT(*) FROM v_maintenance_task WHERE tenant_id=? AND (company_id=? OR"
                + " department_id=?)",
            "SELECT COUNT(*) FROM v_inspection_task WHERE tenant_id=? AND (company_id=? OR"
                + " department_id=?)",
            "SELECT COUNT(*) FROM v_maintenance_definition WHERE tenant_id=? AND department_id=?",
            "SELECT COUNT(*) FROM v_inspection_definition WHERE tenant_id=? AND department_id=?",
            "SELECT COUNT(*) FROM inv_warehouse WHERE tenant_id=? AND company_id=?",
            "SELECT COUNT(*) FROM eam_role_menu_target WHERE tenant_id=? AND target_kind IN"
                + " ('company','department') AND target_id=?")) {
      int count =
          query.contains(" OR ")
              ? db.queryForObject(query, Integer.class, tenant, id, id)
              : db.queryForObject(query, Integer.class, tenant, id);
      if (count > 0) return true;
    }
    return false;
  }
}
