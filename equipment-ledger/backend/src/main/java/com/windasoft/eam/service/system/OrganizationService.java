package com.windasoft.eam.service.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.OrgForm;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.repository.system.SystemManagementRepository;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {
  private final SystemManagementRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

  public OrganizationService(
      SystemManagementRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
  }

  public Map<String, Object> organizations() {
    var rows = repository.organizations(auth.tenantId());
    return Map.of(
        "data", permissions.visibleOrganizations(rows).stream().map(this::orgView).toList());
  }

  @Transactional
  public Map<String, Object> createOrg(OrgForm f) {
    permissions.require("org", "新增");
    lockOrganizations();
    checkOrgCode(f.code(), null);
    validateOrg(f.type(), f.parentId(), null);
    var x = new OrgUnit();
    x.setTenantId(auth.tenantId());
    applyOrg(x, f);
    if (f.parentId() != null) permissions.checkOrg("org", "新增", requireOrg(f.parentId()));
    else if (!permissions.scope("org", "新增").all())
      throw new IllegalArgumentException("创建根公司需要全部组织范围");
    repository.insertOrganization(x);
    orgEvent(x.getId(), "新增", null, orgView(x));
    return Map.of("data", orgView(x));
  }

  @Transactional
  public Map<String, Object> updateOrg(Long id, OrgForm f) {
    lockOrganizations();
    var x = requireOrg(id);
    permissions.checkOrg("org", "编辑", x);
    checkOrgCode(f.code(), id);
    if ("部门".equals(f.type()) && hasLocations(id))
      throw new IllegalArgumentException("该公司仍有存放地点，不能修改为其他组织类型");
    var before = orgView(x);
    if (!Objects.equals(x.getOrgType(), "部门".equals(f.type()) ? "部门" : "公司"))
      throw new IllegalArgumentException("公司与部门不能直接互换类型，请建立新组织并按业务迁移");
    if ("部门".equals(x.getOrgType())
        && !Objects.equals(
            permissions.companyOfOrganization(id), permissions.companyOfOrganization(f.parentId())))
      throw new IllegalArgumentException("部门不能直接跨公司移动，请先完成业务归属迁移");
    if (f.parentId() != null) permissions.checkOrg("org", "编辑", requireOrg(f.parentId()));
    validateOrg(f.type(), f.parentId(), id);
    if (f.parentId() != null && isDescendant(f.parentId(), id))
      throw new IllegalArgumentException("不能将组织移动到自己的下级");
    applyOrg(x, f);
    permissions.checkOrg("org", "编辑", x);
    repository.updateOrganization(x);
    orgEvent(id, "编辑", before, orgView(x));
    return Map.of("data", orgView(x));
  }

  @Transactional
  public Map<String, Object> deleteOrg(Long id) {
    lockOrganizations();
    var x = requireOrg(id);
    permissions.checkOrg("org", "删除", x);
    if (repository.childOrganizationCount(auth.tenantId(), id) > 0)
      throw new IllegalArgumentException("请先移除下级组织");
    if (repository.organizationUserCount(auth.tenantId(), id) > 0)
      throw new IllegalArgumentException("该组织仍有用户，不能删除");
    if (hasLocations(id)) throw new IllegalArgumentException("该公司仍有存放地点，不能删除");
    if (businessReferences(id)) throw new IllegalArgumentException("该组织已有业务或权限引用，不能删除");
    orgEvent(id, "删除", orgView(x), null);
    repository.deleteOrganization(x.getId());
    return Map.of("data", Map.of("deleted", true));
  }

  private boolean hasLocations(Long id) {
    return repository.locationCount(auth.tenantId(), id) > 0;
  }

  private void applyOrg(OrgUnit x, OrgForm f) {
    x.setName(f.name().trim());
    x.setCode(f.code().trim());
    x.setOrgType("部门".equals(f.type()) ? "部门" : "公司");
    x.setDisplayType("集团".equals(f.type()) ? "集团" : null);
    x.setParentId(f.parentId());
    x.setManager(f.manager() == null ? "" : f.manager().trim());
    x.setStatus(f.status() == null ? 1 : f.status());
  }

  private void lockOrganizations() {
    repository.lockOrganizations(auth.tenantId());
  }

  private void orgEvent(Long id, String action, Object before, Object after) {
    repository.orgEvent(
        auth.tenantId(),
        id,
        auth.userId(),
        action,
        before == null ? null : write(before),
        after == null ? null : write(after));
  }

  private boolean businessReferences(Long id) {
    return repository.businessReferences(auth.tenantId(), id);
  }

  private void validateOrg(String type, Long parentId, Long current) {
    if (!Set.of("集团", "公司", "部门").contains(type)) throw new IllegalArgumentException("组织类型无效");
    if (parentId == null) {
      if ("部门".equals(type)) throw new IllegalArgumentException("部门必须选择上级组织");
      if (repository.rootCount(auth.tenantId(), current) > 0)
        throw new IllegalArgumentException("每个客户只能有一个根公司，请选择上级公司");
    } else {
      var parent = requireOrg(parentId);
      if (parent.getStatus() != 1) throw new IllegalArgumentException("上级组织已停用");
      if (!"部门".equals(type) && "部门".equals(parent.getOrgType()))
        throw new IllegalArgumentException("公司不能建立在部门下面");
    }
    if (current != null
        && (current.equals(parentId) || parentId != null && isDescendant(parentId, current)))
      throw new IllegalArgumentException("不能将组织移动到自己或自己的下级");
  }

  private boolean isDescendant(Long candidate, Long parent) {
    var n = repository.organizationById(candidate);
    var seen = new HashSet<Long>();
    while (n != null && n.getParentId() != null && seen.add(n.getId())) {
      if (parent.equals(n.getParentId())) return true;
      n = repository.organizationById(n.getParentId());
    }
    return false;
  }

  private void checkOrgCode(String code, Long id) {
    if (repository.orgCodeCount(auth.tenantId(), code, id) > 0)
      throw new IllegalArgumentException("组织编码已存在");
  }

  private OrgUnit requireOrg(Long id) {
    var x = repository.organization(auth.tenantId(), id);
    if (x == null) throw new IllegalArgumentException("组织不存在");
    return x;
  }

  private Map<String, Object> orgView(OrgUnit x) {
    return Map.of(
        "id",
        String.valueOf(x.getId()),
        "name",
        x.getName(),
        "code",
        x.getCode(),
        "type",
        x.getDisplayType() == null ? x.getOrgType() : x.getDisplayType(),
        "parentId",
        x.getParentId() == null ? "" : String.valueOf(x.getParentId()),
        "manager",
        x.getManager() == null ? "" : x.getManager(),
        "status",
        x.getStatus() == 1 ? "启用" : "停用");
  }

  private String write(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
