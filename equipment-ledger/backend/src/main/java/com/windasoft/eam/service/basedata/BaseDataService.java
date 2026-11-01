package com.windasoft.eam.service.basedata;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.dto.basedata.BaseItemForm;
import com.windasoft.eam.entity.basedata.BaseItem;
import com.windasoft.eam.repository.basedata.BaseDataRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.Map;

@org.springframework.stereotype.Service
public class BaseDataService {
  private final BaseDataRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;

  public BaseDataService(
      BaseDataRepository repository, AuthContext auth, PermissionService permissions) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
  }

  public Map<String, Object> list(String resource, Long companyId) {
    var type = BaseDataType.fromResource(resource);
    if (!permissions.allowed(type.menu(), "查看")
        && !(type == BaseDataType.CATEGORY && permissions.allowed("base", "查看"))
        && !permissions.allowed("equipment", "查看")
        && !((type == BaseDataType.MATERIAL_CATEGORY || type == BaseDataType.UNIT)
            && permissions.menuActions().entrySet().stream()
                .anyMatch(e -> e.getKey().startsWith("inventory") && !e.getValue().isEmpty()))
        && !((type == BaseDataType.FAULT_TYPE || type == BaseDataType.FAULT_CAUSE)
            && permissions.allowed("repair", "查看"))) permissions.require(type.menu(), "查看");
    var rows = repository.list(auth.tenantId(), type.code(), companyId);
    return Map.of("data", rows.stream().map(BaseDataService::view).toList());
  }

  public Map<String, Object> create(String resource, BaseItemForm f) {
    requireDataAccess(resource, "新增");
    BaseDataType type = BaseDataType.fromResource(resource);
    f = withCode(type, f);
    if (type == BaseDataType.PROCESS_RESULT
        && (f.name().trim().equals("已解决")
            || BaseDataReferenceService.RESOLVED_CODE.equalsIgnoreCase(f.code())))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0032");
    if (repository.codeCount(auth.tenantId(), type.code(), f.code(), null) > 0)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0033");
    validateCompany(type, f.companyId());
    validateParent(type, f.parentId(), null, f.companyId());
    var item = fromForm(type, f);
    item.setTenantId(auth.tenantId());
    repository.insert(item);
    return Map.of("data", view(item));
  }

  @org.springframework.transaction.annotation.Transactional
  public Map<String, Object> update(String resource, Long id, BaseItemForm f) {
    requireDataAccess(resource, "编辑");
    BaseDataType type = BaseDataType.fromResource(resource);
    var old = requireLocked(type.code(), id);
    protectFixed(old);
    f = withCode(type, f);
    if (type == BaseDataType.PROCESS_RESULT
        && (f.name().trim().equals("已解决")
            || BaseDataReferenceService.RESOLVED_CODE.equalsIgnoreCase(f.code())))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0032");
    if (repository.codeCount(auth.tenantId(), type.code(), f.code(), id) > 0)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0033");
    validateCompany(type, f.companyId());
    if (type == BaseDataType.LOCATION && !java.util.Objects.equals(old.getCompanyOrgId(), f.companyId()))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0034");
    validateParent(type, f.parentId(), id, f.companyId());
    old.setCode(f.code().trim());
    if (!old.getName().equals(f.name().trim())
        && (type == BaseDataType.MATERIAL_CATEGORY || type == BaseDataType.UNIT)) {
      String field = type == BaseDataType.UNIT ? "unit" : "category";
      repository.renameMaterialField(field, old.getId(), f.name().trim(), auth.tenantId());
    }
    old.setName(f.name().trim());
    old.setParentId(f.parentId());
    old.setDecimalPrecision(f.precision() == null ? 0 : f.precision());
    old.setStatus(f.status() == null ? 1 : f.status());
    old.setSortNo(f.sortNo() == null ? 0 : f.sortNo());
    repository.update(old);
    return Map.of("data", view(old));
  }

  @org.springframework.transaction.annotation.Transactional
  public Map<String, Object> status(String resource, Long id, Map<String, Integer> body) {
    requireDataAccess(resource, "编辑");
    var item = requireLocked(type(resource), id);
    protectFixed(item);
    item.setStatus(body.getOrDefault("status", 0));
    repository.update(item);
    return Map.of("data", view(item));
  }

  @org.springframework.transaction.annotation.Transactional(
      isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Map<String, Object> delete(String resource, Long id) {
    requireDataAccess(resource, "删除");
    BaseDataType type = BaseDataType.fromResource(resource);
    var item = requireLocked(type.code(), id);
    protectFixed(item);
    if (repository.dictionaryReferenceCount(auth.tenantId(), type.code(), id, item.getName()) > 0)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0035");
    if (type.hierarchical()) {
      if (repository.childCount(auth.tenantId(), type.code(), id) > 0)
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0036");
    }
    var equipmentReference = type.equipmentReferenceColumn();
    if (equipmentReference != null
        && repository.equipmentReferenceCount(auth.tenantId(), equipmentReference, id) > 0)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0037");
    if (type == BaseDataType.CATEGORY
        && repository.categoryEquipmentCount(auth.tenantId(), item.getName()) > 0)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0038");
    if (type == BaseDataType.LOCATION && repository.locationEquipmentCount(auth.tenantId(), item) > 0)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0039");
    repository.delete(id);
    return Map.of("data", Map.of("deleted", true));
  }

  public Map<String, Object> companies() {
    if (!permissions.allowed("location", "查看") && !permissions.allowed("base", "查看"))
      permissions.require("equipment", "查看");
    var rows = repository.companies(auth.tenantId());
    return Map.of(
        "data",
        rows.stream()
            .map(
                x ->
                    Map.of(
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
                        x.getStatus() == 1 ? "启用" : "停用"))
            .toList());
  }

  private void validateCompany(BaseDataType type, Long companyId) {
    if (!type == BaseDataType.LOCATION) return;
    if (companyId == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0040");
    var company = repository.company(auth.tenantId(), companyId);
    if (company == null || company.getStatus() != 1)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0041");
  }

  private void requireDataAccess(String resource, String action) {
    var type = BaseDataType.fromResource(resource);
    if (type == BaseDataType.CATEGORY && permissions.allowed("base", action)) return;
    permissions.require(type.menu(), action);
  }

  private BaseItem requireLocked(String type, Long id) {
    var item = repository.locked(auth.tenantId(), type, id);
    if (item == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0043");
    return item;
  }

  private void protectFixed(BaseItem item) {
    if (BaseDataReferenceService.fixed(item))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0044");
  }

  private BaseItem require(String type, Long id) {
    var item = repository.find(auth.tenantId(), type, id);
    if (item == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0043");
    return item;
  }

  private void validateParent(BaseDataType type, Long parentId, Long currentId, Long companyId) {
    if (parentId == null) return;
    var p = repository.find(auth.tenantId(), type.code(), parentId);
    if (p == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0045");
    if (type == BaseDataType.LOCATION && !java.util.Objects.equals(p.getCompanyOrgId(), companyId))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0046");
    java.util.Set<Long> seen = new java.util.HashSet<>();
    while (p != null) {
      if (!seen.add(p.getId()) || java.util.Objects.equals(currentId, p.getId()))
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0047");
      p = p.getParentId() == null ? null : require(type.code(), p.getParentId());
    }
    if (parentId.equals(currentId))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0048");
  }

  private static BaseItemForm withCode(BaseDataType type, BaseItemForm form) {
    String code = form.code() == null ? "" : form.code().trim();
    if (code.isBlank()) code = type.prefix() + "-" + IdWorker.getIdStr();
    return new BaseItemForm(
        code, form.name(), form.parentId(), form.precision(),
        form.status(), form.sortNo(), form.companyId());
  }

  private static BaseItem fromForm(BaseDataType type, BaseItemForm f) {
    var x = new BaseItem();
    x.setItemType(type.code());
    x.setCompanyOrgId(type == BaseDataType.LOCATION ? f.companyId() : null);
    x.setCode(f.code().trim());
    x.setName(f.name().trim());
    x.setParentId(f.parentId());
    x.setDecimalPrecision(f.precision() == null ? 0 : f.precision());
    x.setStatus(f.status() == null ? 1 : f.status());
    x.setSortNo(f.sortNo() == null ? 0 : f.sortNo());
    x.setDeleted(0);
    return x;
  }

  private static Map<String, Object> view(BaseItem x) {
    return Map.of(
        "builtin",
        BaseDataReferenceService.fixed(x),
        "companyId",
        x.getCompanyOrgId() == null ? "" : String.valueOf(x.getCompanyOrgId()),
        "id",
        String.valueOf(x.getId()),
        "code",
        x.getCode(),
        "name",
        x.getName(),
        "parentId",
        x.getParentId() == null ? "" : String.valueOf(x.getParentId()),
        "precision",
        x.getDecimalPrecision() == null ? 0 : x.getDecimalPrecision(),
        "status",
        x.getStatus() != null && x.getStatus() == 1 ? "启用" : "停用",
        "sortNo",
        x.getSortNo() == null ? 0 : x.getSortNo());
  }
}
