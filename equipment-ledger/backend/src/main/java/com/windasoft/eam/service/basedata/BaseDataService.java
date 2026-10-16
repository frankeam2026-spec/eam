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
  private static final Map<String, String> TYPES =
      Map.of(
          "categories",
          "CATEGORY",
          "material-categories",
          "MATERIAL_CATEGORY",
          "units",
          "UNIT",
          "locations",
          "LOCATION",
          "use-statuses",
          "USE_STATUS",
          "sources",
          "SOURCE",
          "fault-types",
          "FAULT_TYPE",
          "processing-results",
          "PROCESS_RESULT",
          "fault-causes",
          "FAULT_CAUSE");
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
    if (!permissions.allowed(menu(resource), "查看")
        && !("categories".equals(resource) && permissions.allowed("base", "查看"))
        && !permissions.allowed("equipment", "查看")
        && !(("material-categories".equals(resource) || "units".equals(resource))
            && permissions.menuActions().entrySet().stream()
                .anyMatch(e -> e.getKey().startsWith("inventory") && !e.getValue().isEmpty()))
        && !(("fault-types".equals(resource) || "fault-causes".equals(resource))
            && permissions.allowed("repair", "查看"))) permissions.require(menu(resource), "查看");
    String type = type(resource);
    var rows = repository.list(auth.tenantId(), type, companyId);
    return Map.of("data", rows.stream().map(BaseDataService::view).toList());
  }

  public Map<String, Object> create(String resource, BaseItemForm f) {
    requireDataAccess(resource, "新增");
    String type = type(resource);
    f = withCode(type, f);
    if (type.equals("PROCESS_RESULT") && f.name().trim().equals("已解决"))
      throw new IllegalArgumentException("已解决是固定处理结果，不能自定义");
    if (repository.codeCount(auth.tenantId(), type, f.code(), null) > 0)
      throw new IllegalArgumentException("编码已存在");
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
    String type = type(resource);
    var old = require(type, id);
    f = withCode(type, f);
    if (type.equals("PROCESS_RESULT") && f.name().trim().equals("已解决"))
      throw new IllegalArgumentException("已解决是固定处理结果，不能自定义");
    if (repository.codeCount(auth.tenantId(), type, f.code(), id) > 0)
      throw new IllegalArgumentException("编码已存在");
    validateCompany(type, f.companyId());
    if ("LOCATION".equals(type) && !java.util.Objects.equals(old.getCompanyOrgId(), f.companyId()))
      throw new IllegalArgumentException("位置所属公司不能修改，请在对应公司下新增位置");
    validateParent(type, f.parentId(), id, f.companyId());
    old.setCode(f.code().trim());
    if (!old.getName().equals(f.name().trim())
        && (type.equals("MATERIAL_CATEGORY") || type.equals("UNIT"))) {
      String field = type.equals("UNIT") ? "unit" : "category";
      repository.renameMaterialField(field, f.name().trim(), auth.tenantId(), old.getName());
    }
    old.setName(f.name().trim());
    old.setParentId(f.parentId());
    old.setDecimalPrecision(f.precision() == null ? 0 : f.precision());
    old.setStatus(f.status() == null ? 1 : f.status());
    old.setSortNo(f.sortNo() == null ? 0 : f.sortNo());
    repository.update(old);
    return Map.of("data", view(old));
  }

  public Map<String, Object> status(String resource, Long id, Map<String, Integer> body) {
    requireDataAccess(resource, "编辑");
    var item = require(type(resource), id);
    item.setStatus(body.getOrDefault("status", 0));
    repository.update(item);
    return Map.of("data", view(item));
  }

  public Map<String, Object> delete(String resource, Long id) {
    requireDataAccess(resource, "删除");
    String type = type(resource);
    var item = require(type, id);
    if (type.equals("MATERIAL_CATEGORY") || type.equals("UNIT")) {
      String field = type.equals("UNIT") ? "unit" : "category";
      if (repository.materialReferenceCount(auth.tenantId(), field, item.getName()) > 0)
        throw new IllegalArgumentException("该基础数据已被物料使用，不能删除");
    }
    if (type.equals("CATEGORY")
        || type.equals("MATERIAL_CATEGORY")
        || type.equals("LOCATION")
        || type.equals("FAULT_TYPE")) {
      if (repository.childCount(auth.tenantId(), type, id) > 0)
        throw new IllegalArgumentException("该节点下还有子节点，不能删除");
    }
    var equipmentReference =
        Map.of(
                "CATEGORY",
                "category_id",
                "UNIT",
                "unit_id",
                "USE_STATUS",
                "use_status_id",
                "SOURCE",
                "source_id",
                "LOCATION",
                "function_location_id")
            .get(type);
    if (equipmentReference != null
        && repository.equipmentReferenceCount(auth.tenantId(), equipmentReference, id) > 0)
      throw new IllegalArgumentException("该基础数据已被设备关联，不能删除");
    if (type.equals("CATEGORY")
        && repository.categoryEquipmentCount(auth.tenantId(), item.getName()) > 0)
      throw new IllegalArgumentException("该分类已有设备使用，不能删除");
    if (type.equals("LOCATION") && repository.locationEquipmentCount(auth.tenantId(), item) > 0)
      throw new IllegalArgumentException("该位置已有设备使用，不能删除");
    if (type.equals("FAULT_TYPE")
        && repository.repairReferenceCount(auth.tenantId(), item.getName(), false) > 0)
      throw new IllegalArgumentException("该故障类型已有维修工单使用，不能删除");
    if (type.equals("FAULT_CAUSE")
        && repository.repairReferenceCount(auth.tenantId(), item.getName(), true) > 0)
      throw new IllegalArgumentException("该故障原因已有维修工单使用，不能删除");
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

  private void validateCompany(String type, Long companyId) {
    if (!"LOCATION".equals(type)) return;
    if (companyId == null) throw new IllegalArgumentException("请先选择所属公司");
    var company = repository.company(auth.tenantId(), companyId);
    if (company == null || company.getStatus() != 1)
      throw new IllegalArgumentException("所属公司不存在或已停用");
  }

  private String type(String resource) {
    var type = TYPES.get(resource);
    if (type == null) throw new IllegalArgumentException("不支持的基础数据类型");
    return type;
  }

  private void requireDataAccess(String resource, String action) {
    if ("categories".equals(resource) && permissions.allowed("base", action)) return;
    permissions.require(menu(resource), action);
  }

  private String menu(String resource) {
    type(resource);
    return "locations".equals(resource)
        ? "location"
        : "categories".equals(resource) ? "category" : "base";
  }

  private BaseItem require(String type, Long id) {
    var item = repository.find(auth.tenantId(), type, id);
    if (item == null) throw new IllegalArgumentException("基础数据不存在");
    return item;
  }

  private void validateParent(String type, Long parentId, Long currentId, Long companyId) {
    if (parentId == null) return;
    var p = repository.find(auth.tenantId(), type, parentId);
    if (p == null) throw new IllegalArgumentException("上级节点不存在");
    if ("LOCATION".equals(type) && !java.util.Objects.equals(p.getCompanyOrgId(), companyId))
      throw new IllegalArgumentException("上级位置必须属于同一公司");
    java.util.Set<Long> seen = new java.util.HashSet<>();
    while (p != null) {
      if (!seen.add(p.getId()) || java.util.Objects.equals(currentId, p.getId()))
        throw new IllegalArgumentException("不能选择自身或下级节点作为上级");
      p = p.getParentId() == null ? null : require(type, p.getParentId());
    }
    if (currentId != null && parentId.equals(currentId))
      throw new IllegalArgumentException("不能将节点设置为自己的上级");
  }

  private static BaseItemForm withCode(String type, BaseItemForm form) {
    String code = form.code() == null ? "" : form.code().trim();
    if (code.isBlank()) {
      String prefix =
          switch (type) {
            case "CATEGORY" -> "CAT";
            case "MATERIAL_CATEGORY" -> "MC";
            case "UNIT" -> "UNIT";
            case "LOCATION" -> "LOC";
            case "USE_STATUS" -> "USE";
            case "SOURCE" -> "SRC";
            case "FAULT_TYPE" -> "FT";
            case "FAULT_CAUSE" -> "FC";
            case "PROCESS_RESULT" -> "PR";
            default -> "BASE";
          };
      code = prefix + "-" + IdWorker.getIdStr();
    }
    return new BaseItemForm(
        code,
        form.name(),
        form.parentId(),
        form.precision(),
        form.status(),
        form.sortNo(),
        form.companyId());
  }

  private static BaseItem fromForm(String type, BaseItemForm f) {
    var x = new BaseItem();
    x.setItemType(type);
    x.setCompanyOrgId("LOCATION".equals(type) ? f.companyId() : null);
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
