package com.windasoft.eam.service.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.common.ActivationStatus;
import com.windasoft.eam.dto.system.OrgForm;
import com.windasoft.eam.dto.system.OrganizationView;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.entity.system.OrganizationEvent;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.repository.system.OrganizationEventRepository;
import com.windasoft.eam.repository.system.OrganizationReferenceRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 组织管理服务：验证组织层级与业务引用，避免删除仍被业务使用的组织。 */
@Service
public class OrganizationService {
  private final BaseItemRepository locations;
  private final OrganizationReferenceRepository references;
  private final OrgUnitRepository orgUnits;
  private final OrganizationEventRepository events;
  private final SystemUserRepository systemUsers;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;

  public OrganizationService(
      BaseItemRepository locations,
      OrganizationReferenceRepository references,
      OrgUnitRepository orgUnits,
      OrganizationEventRepository events,
      SystemUserRepository systemUsers,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json) {
    this.locations = locations;
    this.references = references;
    this.orgUnits = orgUnits;
    this.events = events;
    this.systemUsers = systemUsers;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
  }

  public List<OrganizationView> organizations() {
    var rows = orgUnits.organizations(auth.tenantId());
    return permissions.visibleOrganizations(rows).stream().map(this::orgView).toList();
  }

  @Transactional
  public OrganizationView createOrg(OrgForm f) {
    permissions.require("org", "新增");
    lockOrganizations();
    checkOrgCode(f.code(), null);
    validateOrg(f.type(), f.parentId(), null);
    var x = new OrgUnit();
    x.setTenantId(auth.tenantId());
    applyOrg(x, f);
    if (f.parentId() != null) permissions.checkOrg("org", "新增", requireOrg(f.parentId()));
    else if (!permissions.scope("org", "新增").all())
      throw new LocalizedArgumentException("server.system.message0018");
    orgUnits.insertOrganization(x);
    orgEvent(x.getId(), "新增", null, orgView(x));
    return orgView(x);
  }

  @Transactional
  public OrganizationView updateOrg(Long id, OrgForm f) {
    lockOrganizations();
    var x = requireOrg(id);
    permissions.checkOrg("org", "编辑", x);
    checkOrgCode(f.code(), id);
    if ("部门".equals(f.type()) && hasLocations(id))
      throw new LocalizedArgumentException("server.system.message0019");
    var before = orgView(x);
    if (!Objects.equals(x.getOrgType(), "部门".equals(f.type()) ? "部门" : "公司"))
      throw new LocalizedArgumentException("server.system.message0020");
    if ("部门".equals(x.getOrgType())
        && !Objects.equals(
            permissions.companyOfOrganization(id), permissions.companyOfOrganization(f.parentId())))
      throw new LocalizedArgumentException("server.system.message0021");
    if (f.parentId() != null) permissions.checkOrg("org", "编辑", requireOrg(f.parentId()));
    validateOrg(f.type(), f.parentId(), id);
    applyOrg(x, f);
    permissions.checkOrg("org", "编辑", x);
    orgUnits.updateOrganization(x);
    orgEvent(id, "编辑", before, orgView(x));
    return orgView(x);
  }

  /** 组织删除在原组织锁内检查下级、人员、功能位置及业务引用；审计与逻辑删除一起提交。 */
  @Transactional
  public void deleteOrg(Long id) {
    lockOrganizations();
    var x = requireOrg(id);
    permissions.checkOrg("org", "删除", x);
    if (orgUnits.childOrganizationCount(auth.tenantId(), id) > 0)
      throw new LocalizedArgumentException("ui.equipment.text0904");
    if (systemUsers.organizationUserCount(auth.tenantId(), id) > 0)
      throw new LocalizedArgumentException("ui.equipment.text0905");
    if (hasLocations(id)) throw new LocalizedArgumentException("server.system.message0023");
    if (businessReferences(id)) throw new LocalizedArgumentException("server.system.message0024");
    orgEvent(id, "删除", orgView(x), null);
    orgUnits.deleteOrganization(x.getId());
  }

  private boolean hasLocations(Long id) {
    return locations.locationCount(auth.tenantId(), id) > 0;
  }

  private void applyOrg(OrgUnit x, OrgForm f) {
    x.setName(f.name().trim());
    x.setCode(f.code().trim());
    x.setOrgType("部门".equals(f.type()) ? "部门" : "公司");
    x.setDisplayType("集团".equals(f.type()) ? "集团" : null);
    x.setParentId(f.parentId());
    x.setManager(f.manager() == null ? "" : f.manager().trim());
    x.setStatus(f.status() == null ? ActivationStatus.ENABLED.code() : f.status());
  }

  private void lockOrganizations() {
    orgUnits.lockOrganizations(auth.tenantId());
  }

  private void orgEvent(Long id, String action, OrganizationView before, OrganizationView after) {
    // 组织主体和审计在同一个用例事务中保存，审计失败不能留下未追溯的组织变更。
    var event = new OrganizationEvent();
    event.setTenantId(auth.tenantId());
    event.setOrgId(id);
    event.setActorId(auth.userId());
    event.setAction(action);
    event.setBeforeJson(before == null ? null : write(before));
    event.setAfterJson(after == null ? null : write(after));
    events.insert(event);
  }

  private boolean businessReferences(Long id) {
    return references.businessReferences(auth.tenantId(), id);
  }

  private void validateOrg(String type, Long parentId, Long current) {
    if (!Set.of("集团", "公司", "部门").contains(type))
      throw new LocalizedArgumentException("server.system.message0025");
    if (parentId == null) {
      if ("部门".equals(type)) throw new LocalizedArgumentException("server.system.message0026");
      if (orgUnits.rootCount(auth.tenantId(), current) > 0)
        throw new LocalizedArgumentException("server.system.message0027");
    } else {
      var parent = requireOrg(parentId);
      if (!ActivationStatus.isEnabled(parent.getStatus()))
        throw new LocalizedArgumentException("server.system.message0028");
      if (!"部门".equals(type) && "部门".equals(parent.getOrgType()))
        throw new LocalizedArgumentException("server.system.message0029");
    }
    if (current != null
        && (current.equals(parentId) || parentId != null && isDescendant(parentId, current)))
      throw new LocalizedArgumentException("server.system.message0030");
  }

  private boolean isDescendant(Long candidate, Long parent) {
    var n = orgUnits.organization(auth.tenantId(), candidate);
    var seen = new HashSet<Long>();
    while (n != null && n.getParentId() != null && seen.add(n.getId())) {
      if (parent.equals(n.getParentId())) return true;
      n = orgUnits.organization(auth.tenantId(), n.getParentId());
    }
    return false;
  }

  private void checkOrgCode(String code, Long id) {
    if (orgUnits.orgCodeCount(auth.tenantId(), code, id) > 0)
      throw new LocalizedArgumentException("ui.equipment.text0900");
  }

  private OrgUnit requireOrg(Long id) {
    var x = orgUnits.organization(auth.tenantId(), id);
    if (x == null) throw new LocalizedArgumentException("server.system.message0031");
    return x;
  }

  private OrganizationView orgView(OrgUnit x) {
    return new OrganizationView(
        String.valueOf(x.getId()),
        x.getName(),
        x.getCode(),
        x.getDisplayType() == null ? x.getOrgType() : x.getDisplayType(),
        x.getParentId() == null ? "" : String.valueOf(x.getParentId()),
        x.getManager() == null ? "" : x.getManager(),
        ActivationStatus.fromFlag(x.getStatus()).label());
  }

  private String write(OrganizationView o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
