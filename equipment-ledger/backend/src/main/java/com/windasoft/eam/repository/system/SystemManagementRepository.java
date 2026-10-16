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
      JdbcTemplate db) {
    this.orgs = orgs;
    this.users = users;
    this.roles = roles;
    this.links = links;
    this.grants = grants;
    this.sessions = sessions;
    this.locations = locations;
    this.db = db;
  }

  public List<OrgUnit> organizations(long tenant) {
    return orgs.selectList(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .orderByAsc(OrgUnit::getId));
  }

  public List<SystemUser> users(long tenant) {
    return users.selectList(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .orderByAsc(SystemUser::getId));
  }

  public List<SystemRole> roles(long tenant) {
    return roles.selectList(
        new LambdaQueryWrapper<SystemRole>()
            .eq(SystemRole::getTenantId, tenant)
            .orderByAsc(SystemRole::getId));
  }

  public OrgUnit organization(long tenant, Long id) {
    return orgs.selectOne(
        new LambdaQueryWrapper<OrgUnit>().eq(OrgUnit::getTenantId, tenant).eq(OrgUnit::getId, id));
  }

  public OrgUnit organizationById(Long id) {
    return orgs.selectById(id);
  }

  public SystemUser user(long tenant, Long id) {
    return users.selectOne(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getId, id));
  }

  public SystemRole role(long tenant, Long id) {
    return roles.selectOne(
        new LambdaQueryWrapper<SystemRole>()
            .eq(SystemRole::getTenantId, tenant)
            .eq(SystemRole::getId, id));
  }

  public int insertOrganization(OrgUnit x) {
    return orgs.insert(x);
  }

  public int updateOrganization(OrgUnit x) {
    return orgs.updateById(x);
  }

  public int deleteOrganization(Long id) {
    return orgs.deleteById(id);
  }

  public int insertUser(SystemUser x) {
    return users.insert(x);
  }

  public int updateUser(SystemUser x) {
    return users.updateById(x);
  }

  public int deleteUser(Long id) {
    return users.deleteById(id);
  }

  public int insertRole(SystemRole x) {
    return roles.insert(x);
  }

  public int updateRole(SystemRole x) {
    return roles.updateById(x);
  }

  public int deleteRole(Long id) {
    return roles.deleteById(id);
  }

  public long childOrganizationCount(long tenant, Long id) {
    return orgs.selectCount(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getParentId, id));
  }

  public long organizationUserCount(long tenant, Long id) {
    return users.selectCount(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getOrgId, id));
  }

  public long locationCount(long tenant, Long id) {
    return locations.selectCount(
        new LambdaQueryWrapper<BaseItem>()
            .eq(BaseItem::getTenantId, tenant)
            .eq(BaseItem::getItemType, "LOCATION")
            .eq(BaseItem::getCompanyOrgId, id));
  }

  public long rootCount(long tenant, Long current) {
    var q =
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .isNull(OrgUnit::getParentId);
    if (current != null) q.ne(OrgUnit::getId, current);
    return orgs.selectCount(q);
  }

  public long orgCodeCount(long tenant, String code, Long current) {
    var q =
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getCode, code.trim());
    if (current != null) q.ne(OrgUnit::getId, current);
    return orgs.selectCount(q);
  }

  public long accountCount(long tenant, String account, Long current) {
    var q =
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getAccount, account.trim());
    if (current != null) q.ne(SystemUser::getId, current);
    return users.selectCount(q);
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
    return grants.selectList(
        new LambdaQueryWrapper<RoleMenuPermission>()
            .eq(RoleMenuPermission::getTenantId, tenant)
            .eq(RoleMenuPermission::getRoleId, id)
            .orderByAsc(RoleMenuPermission::getId));
  }

  public void deleteMenuPermissions(long tenant, Long id) {
    grants.delete(new QueryWrapper<RoleMenuPermission>().eq("tenant_id", tenant).eq("role_id", id));
  }

  public void insertMenuPermission(RoleMenuPermission row) {
    grants.insert(row);
  }

  public long organizationCount(long tenant, String type, List<Long> ids) {
    return orgs.selectCount(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getOrgType, type)
            .in(OrgUnit::getId, ids));
  }

  public long warehouseCount(long tenant, List<Long> ids) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_warehouse WHERE tenant_id=? AND id IN ("
            + String.join(",", Collections.nCopies(ids.size(), "?"))
            + ")",
        Long.class,
        java.util.stream.Stream.concat(java.util.stream.Stream.of(tenant), ids.stream()).toArray());
  }

  public List<Map<String, Object>> warehouseChoices(long tenant) {
    return db.queryForList(
        "SELECT CAST(w.id AS CHAR) id,CONCAT(o.name,' / ',w.name) name FROM inv_warehouse w JOIN"
            + " eam_org_unit o ON o.tenant_id=w.tenant_id AND o.id=w.company_id WHERE w.tenant_id=?"
            + " ORDER BY o.name,w.name",
        tenant);
  }

  public void lockOrganizations(long tenant) {
    db.queryForList(
        "SELECT id FROM eam_org_unit WHERE tenant_id=? AND deleted=0 ORDER BY id FOR UPDATE",
        tenant);
  }

  public void orgEvent(
      long tenant, Long id, long actor, String action, String before, String after) {
    db.update(
        "INSERT INTO eam_org_event(tenant_id,org_id,actor_id,action,before_json,after_json)"
            + " VALUES(?,?,?,?,?,?)",
        tenant,
        id,
        actor,
        action,
        before,
        after);
  }

  public boolean businessReferences(long tenant, Long id) {

    for (String query :
        List.of(
            "SELECT COUNT(*) FROM eam_equipment WHERE tenant_id=? AND (company_org_id=? OR"
                + " department_org_id=?)",
            "SELECT COUNT(*) FROM eam_repair_order WHERE tenant_id=? AND department_org_id=?",
            "SELECT COUNT(*) FROM eam_maintenance_task WHERE tenant_id=? AND (company_id=? OR"
                + " department_id=?)",
            "SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=? AND (company_id=? OR"
                + " department_id=?)",
            "SELECT COUNT(*) FROM eam_maintenance_definition WHERE tenant_id=? AND department_id=?",
            "SELECT COUNT(*) FROM eam_inspection_definition WHERE tenant_id=? AND department_id=?",
            "SELECT COUNT(*) FROM inv_warehouse WHERE tenant_id=? AND company_id=?",
            "SELECT COUNT(*) FROM eam_sys_role_menu_permission WHERE tenant_id=? AND"
                + " JSON_CONTAINS(org_ids_json,CAST(? AS JSON))")) {
      int count =
          query.contains(" OR ")
              ? db.queryForObject(query, Integer.class, tenant, id, id)
              : db.queryForObject(query, Integer.class, tenant, id);
      if (count > 0) return true;
    }
    return false;
  }
}
