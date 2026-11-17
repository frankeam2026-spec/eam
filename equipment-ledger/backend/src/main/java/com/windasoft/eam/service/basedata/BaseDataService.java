package com.windasoft.eam.service.basedata;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.dto.basedata.BaseItemForm;
import com.windasoft.eam.dto.basedata.BaseItemStatusForm;
import com.windasoft.eam.dto.basedata.BaseItemView;
import com.windasoft.eam.dto.system.OrganizationView;
import com.windasoft.eam.entity.basedata.BaseItem;
import com.windasoft.eam.repository.basedata.BaseDataRepository;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.repository.inventory.MaterialRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BaseDataService {
  private final BaseDataRepository repository;
  private final BaseItemRepository items;
  private final OrgUnitRepository organizations;
  private final MaterialRepository materials;
  private final AuthContext auth;
  private final PermissionService permissions;

  public BaseDataService(
      BaseDataRepository repository,
      BaseItemRepository items,
      OrgUnitRepository organizations,
      MaterialRepository materials,
      AuthContext auth,
      PermissionService permissions) {
    this.repository = repository;
    this.items = items;
    this.organizations = organizations;
    this.materials = materials;
    this.auth = auth;
    this.permissions = permissions;
  }

  public List<BaseItemView> list(String resource, Long companyId) {
    var type = BaseDataType.fromResource(resource);
    if (!permissions.allowed(type.menu(), "查看")
        && !(type == BaseDataType.CATEGORY && permissions.allowed("base", "查看"))
        && !permissions.allowed("equipment", "查看")
        && !((type == BaseDataType.MATERIAL_CATEGORY || type == BaseDataType.UNIT)
            && permissions.menuActions().entrySet().stream()
                .anyMatch(e -> e.getKey().startsWith("inventory") && !e.getValue().isEmpty()))
        && !((type == BaseDataType.FAULT_TYPE || type == BaseDataType.FAULT_CAUSE)
            && permissions.allowed("repair", "查看"))) permissions.require(type.menu(), "查看");
    var rows = items.list(auth.tenantId(), type.code(), companyId);
    return rows.stream().map(BaseDataService::view).toList();
  }

  public BaseItemView create(String resource, BaseItemForm f) {
    requireDataAccess(resource, "新增");
    BaseDataType type = BaseDataType.fromResource(resource);
    f = withCode(type, f);
    validateReservedResult(type, f);
    if (items.codeCount(auth.tenantId(), type.code(), f.code(), null) > 0)
      throw new LocalizedArgumentException("server.common.message0033");
    validateCompany(type, f.companyId());
    validateParent(type, f.parentId(), null, f.companyId());
    var item = fromForm(type, f);
    item.setTenantId(auth.tenantId());
    items.insert(item);
    return view(item);
  }

  /** 先锁基础项再同步物料名称，保持原锁顺序；同一事务中同步物料版本并保存字典。 物料按关联ID同步，历史仅有名称的记录不在这里猜测归属，失败须一起回滚。 */
  @Transactional
  public BaseItemView update(String resource, Long id, BaseItemForm f) {
    requireDataAccess(resource, "编辑");
    BaseDataType type = BaseDataType.fromResource(resource);
    var old = requireLocked(type.code(), id);
    protectFixed(old);
    f = withCode(type, f);
    validateReservedResult(type, f);
    if (items.codeCount(auth.tenantId(), type.code(), f.code(), id) > 0)
      throw new LocalizedArgumentException("server.common.message0033");
    validateCompany(type, f.companyId());
    if (type == BaseDataType.LOCATION && !Objects.equals(old.getCompanyOrgId(), f.companyId()))
      throw new LocalizedArgumentException("server.common.message0034");
    validateParent(type, f.parentId(), id, f.companyId());
    old.setCode(f.code().trim());
    if (!old.getName().equals(f.name().trim())
        && (type == BaseDataType.MATERIAL_CATEGORY || type == BaseDataType.UNIT)) {
      if (type == BaseDataType.UNIT)
        materials.renameUnit(auth.tenantId(), old.getId(), f.name().trim());
      else materials.renameCategory(auth.tenantId(), old.getId(), f.name().trim());
    }
    old.setName(f.name().trim());
    old.setParentId(f.parentId());
    old.setDecimalPrecision(f.precision() == null ? 0 : f.precision());
    old.setStatus(f.status() == null ? 1 : f.status());
    old.setSortNo(f.sortNo() == null ? 0 : f.sortNo());
    items.update(old);
    return view(old);
  }

  @Transactional
  public BaseItemView status(String resource, Long id, BaseItemStatusForm body) {
    requireDataAccess(resource, "编辑");
    var item = requireLocked(BaseDataType.fromResource(resource).code(), id);
    protectFixed(item);
    item.setStatus(body.status());
    items.update(item);
    return view(item);
  }

  /** 删除保留READ_COMMITTED及基础项行锁，引用检查包含真实ID和原历史名称回退，不能仅查新关联。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void delete(String resource, Long id) {
    requireDataAccess(resource, "删除");
    BaseDataType type = BaseDataType.fromResource(resource);
    var item = requireLocked(type.code(), id);
    protectFixed(item);
    if (repository.dictionaryReferenceCount(auth.tenantId(), type.code(), id, item.getName()) > 0)
      throw new LocalizedArgumentException("server.common.message0035");
    if (type.hierarchical()) {
      if (items.childCount(auth.tenantId(), type.code(), id) > 0)
        throw new LocalizedArgumentException("server.common.message0036");
    }
    var equipmentReference = type.equipmentReferenceColumn();
    if (equipmentReference != null
        && repository.equipmentReferenceCount(auth.tenantId(), equipmentReference, id) > 0)
      throw new LocalizedArgumentException("server.common.message0037");
    if (type == BaseDataType.CATEGORY
        && repository.categoryEquipmentCount(auth.tenantId(), item.getName()) > 0)
      throw new LocalizedArgumentException("server.common.message0038");
    if (type == BaseDataType.LOCATION
        && repository.locationEquipmentCount(auth.tenantId(), item) > 0)
      throw new LocalizedArgumentException("server.common.message0039");
    items.delete(id);
  }

  public List<OrganizationView> companies() {
    if (!permissions.allowed("location", "查看") && !permissions.allowed("base", "查看"))
      permissions.require("equipment", "查看");
    var rows = organizations.companies(auth.tenantId());
    return rows.stream()
        .map(
            x ->
                new OrganizationView(
                    String.valueOf(x.getId()),
                    x.getName(),
                    x.getCode(),
                    x.getDisplayType() == null ? x.getOrgType() : x.getDisplayType(),
                    x.getParentId() == null ? "" : String.valueOf(x.getParentId()),
                    x.getManager() == null ? "" : x.getManager(),
                    x.getStatus() == 1 ? "启用" : "停用"))
        .toList();
  }

  private void validateReservedResult(BaseDataType type, BaseItemForm form) {
    if (type == BaseDataType.PROCESS_RESULT
        && (form.name().trim().equals("已解决")
            || BaseDataReferenceService.RESOLVED_CODE.equalsIgnoreCase(form.code())))
      throw new LocalizedArgumentException("server.common.message0032");
  }

  private void validateCompany(BaseDataType type, Long companyId) {
    if (type != BaseDataType.LOCATION) return;
    if (companyId == null) throw new LocalizedArgumentException("server.common.message0040");
    var company = organizations.company(auth.tenantId(), companyId);
    if (company == null || company.getStatus() != 1)
      throw new LocalizedArgumentException("server.common.message0041");
  }

  private void requireDataAccess(String resource, String action) {
    var type = BaseDataType.fromResource(resource);
    if (type == BaseDataType.CATEGORY && permissions.allowed("base", action)) return;
    permissions.require(type.menu(), action);
  }

  private BaseItem requireLocked(String type, Long id) {
    var item = items.locked(auth.tenantId(), type, id);
    if (item == null) throw new LocalizedArgumentException("server.common.message0043");
    return item;
  }

  private void protectFixed(BaseItem item) {
    if (BaseDataReferenceService.fixed(item))
      throw new LocalizedArgumentException("server.common.message0044");
  }

  private BaseItem require(String type, Long id) {
    var item = items.find(auth.tenantId(), type, id);
    if (item == null) throw new LocalizedArgumentException("server.common.message0043");
    return item;
  }

  private void validateParent(BaseDataType type, Long parentId, Long currentId, Long companyId) {
    if (parentId == null) return;
    var p = items.find(auth.tenantId(), type.code(), parentId);
    if (p == null) throw new LocalizedArgumentException("server.common.message0045");
    if (type == BaseDataType.LOCATION && !Objects.equals(p.getCompanyOrgId(), companyId))
      throw new LocalizedArgumentException("server.common.message0046");
    Set<Long> seen = new HashSet<>();
    while (p != null) {
      if (!seen.add(p.getId()) || Objects.equals(currentId, p.getId()))
        throw new LocalizedArgumentException("server.common.message0047");
      p = p.getParentId() == null ? null : require(type.code(), p.getParentId());
    }
  }

  private static BaseItemForm withCode(BaseDataType type, BaseItemForm form) {
    String code = form.code() == null ? "" : form.code().trim();
    if (code.isBlank()) code = type.prefix() + "-" + IdWorker.getIdStr();
    return new BaseItemForm(
        code,
        form.name(),
        form.parentId(),
        form.precision(),
        form.status(),
        form.sortNo(),
        form.companyId());
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

  private static BaseItemView view(BaseItem x) {
    return new BaseItemView(
        BaseDataReferenceService.fixed(x),
        x.getCompanyOrgId() == null ? "" : String.valueOf(x.getCompanyOrgId()),
        String.valueOf(x.getId()),
        x.getCode(),
        x.getName(),
        x.getParentId() == null ? "" : String.valueOf(x.getParentId()),
        x.getDecimalPrecision() == null ? 0 : x.getDecimalPrecision(),
        x.getStatus() != null && x.getStatus() == 1 ? "启用" : "停用",
        x.getSortNo() == null ? 0 : x.getSortNo());
  }
}
