package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.*;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.entity.system.RoleMenuPermission;
import com.windasoft.eam.entity.system.SystemRole;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.entity.system.UserRoleLink;
import com.windasoft.eam.repository.repair.RepairWorkflowMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthorizationRepository {
  private final RoleRelationsRepository relations;
  private final UserRoleLinkMapper links;
  private final SystemRoleMapper roles;
  private final RoleMenuPermissionMapper grants;
  private final OrgUnitMapper orgs;
  private final SystemUserMapper users;
  private final RepairWorkflowMapper workflow;
  private final JdbcTemplate db;

  public AuthorizationRepository(
      UserRoleLinkMapper links,
      SystemRoleMapper roles,
      RoleMenuPermissionMapper grants,
      OrgUnitMapper orgs,
      SystemUserMapper users,
      RepairWorkflowMapper workflow,
      JdbcTemplate db,
      RoleRelationsRepository relations) {
    this.relations = relations;
    this.links = links;
    this.roles = roles;
    this.grants = grants;
    this.orgs = orgs;
    this.users = users;
    this.workflow = workflow;
    this.db = db;
  }

  public boolean hasWorkflow() {
    return workflow != null;
  }

  public boolean hasTaskDatabase() {
    return db != null;
  }

  public boolean personalRepairAccess(long tenant, long user) {
    return workflow.personalAccess(tenant, user);
  }

  public List<UserRoleLink> userRoles(long tenant, long user) {
    return links.selectList(
        new QueryWrapper<UserRoleLink>().eq("tenant_id", tenant).eq("user_id", user));
  }

  public List<SystemRole> activeRoles(long tenant, List<Long> ids) {
    return relations.roles(
        roles.selectList(
            new LambdaQueryWrapper<SystemRole>()
                .eq(SystemRole::getTenantId, tenant)
                .eq(SystemRole::getStatus, 1)
                .in(SystemRole::getId, ids)));
  }

  public List<RoleMenuPermission> grants(long tenant, Long role, String menu) {
    var q =
        new LambdaQueryWrapper<RoleMenuPermission>()
            .eq(RoleMenuPermission::getTenantId, tenant)
            .eq(RoleMenuPermission::getRoleId, role);
    if (menu != null) q.eq(RoleMenuPermission::getMenuKey, menu);
    return relations.grants(grants.selectList(q));
  }

  public SystemUser activeUser(long tenant, long id) {
    return users.selectOne(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getId, id)
            .eq(SystemUser::getStatus, 1));
  }

  public List<SystemUser> usersNamed(long tenant, String name) {
    return users.selectList(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getDisplayName, name));
  }

  public OrgUnit organization(long tenant, Long id) {
    return orgs.selectOne(
        new LambdaQueryWrapper<OrgUnit>().eq(OrgUnit::getTenantId, tenant).eq(OrgUnit::getId, id));
  }

  public List<OrgUnit> organizations(long tenant) {
    return orgs.selectList(new LambdaQueryWrapper<OrgUnit>().eq(OrgUnit::getTenantId, tenant));
  }

  public OrgUnit activeDepartment(long tenant, Long id) {
    return orgs.selectOne(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getId, id)
            .eq(OrgUnit::getOrgType, "部门")
            .eq(OrgUnit::getStatus, 1));
  }

  public OrgUnit companyNamed(long tenant, String name) {
    return orgs.selectOne(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getOrgType, "公司")
            .eq(OrgUnit::getName, name));
  }

  public List<OrgUnit> departmentsNamed(long tenant, String name) {
    return orgs.selectList(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getOrgType, "部门")
            .eq(OrgUnit::getName, name));
  }

  public int maintenanceParticipation(long tenant, long user) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_maintenance_task WHERE tenant_id=? AND"
            + " (JSON_CONTAINS(JSON_EXTRACT(snapshot,'$.owners'),JSON_ARRAY(CAST(? AS CHAR))) OR"
            + " JSON_CONTAINS(JSON_EXTRACT(snapshot,'$.reviewers'),JSON_ARRAY(CAST(? AS CHAR))))",
        Integer.class,
        tenant,
        user,
        user);
  }

  public int inspectionParticipation(long tenant, long user, String kind) {
    String filter = kind == null ? "" : " AND kind=?";
    var args = new ArrayList<Object>();
    args.add(tenant);
    if (kind != null) args.add(kind);
    args.add(user);
    args.add(user);
    args.add(user);
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_inspection_task WHERE tenant_id=? AND deleted=0"
            + filter
            + " AND (JSON_CONTAINS(JSON_EXTRACT(snapshot,'$.owners'),JSON_ARRAY(CAST(? AS CHAR)))"
            + " OR JSON_CONTAINS(JSON_EXTRACT(snapshot,'$.reviewers'),JSON_ARRAY(CAST(? AS CHAR)))"
            + " OR owner_id=? OR JSON_UNQUOTE(JSON_EXTRACT(snapshot,'$.personMode'))='任何人可点检')",
        Integer.class,
        args.toArray());
  }

  public int repairPlanParticipation(long tenant, long user) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_single_repair_plan WHERE tenant_id=? AND (owner_id=? OR"
            + " creator_id=?)",
        Integer.class,
        tenant,
        user,
        user);
  }

  public int equipmentResponsibility(long tenant, Long id, long user) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_equipment_owner WHERE tenant_id=? AND equipment_id=? AND"
            + " user_id=?",
        Integer.class,
        tenant,
        id,
        user);
  }

  public List<Map<String, Object>> userOrganization(long tenant, long user) {
    return db.queryForList(
        "SELECT org_id,status FROM eam_sys_user WHERE tenant_id=? AND id=? AND deleted=0",
        tenant,
        user);
  }

  public List<Map<String, Object>> warehouse(long tenant, long id) {
    return db.queryForList(
        "SELECT company_id FROM inv_warehouse WHERE tenant_id=? AND id=?", tenant, id);
  }
}
