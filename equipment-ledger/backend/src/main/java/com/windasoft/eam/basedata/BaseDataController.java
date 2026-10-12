package com.windasoft.eam.basedata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.equipment.Equipment;
import com.windasoft.eam.equipment.EquipmentMapper;
import com.windasoft.eam.repair.RepairOrder;
import com.windasoft.eam.repair.RepairOrderMapper;
import com.windasoft.eam.system.AuthContext;
import com.windasoft.eam.system.PermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/base-data")
public class BaseDataController {
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
  private final BaseItemMapper mapper;

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.jdbc.core.JdbcTemplate db;

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.system.OrgUnitMapper orgs;

  private final EquipmentMapper equipmentMapper;
  private final RepairOrderMapper repairOrderMapper;
  private final AuthContext auth;
  private final PermissionService permissions;

  public BaseDataController(
      BaseItemMapper mapper,
      EquipmentMapper equipmentMapper,
      RepairOrderMapper repairOrderMapper,
      AuthContext auth,
      PermissionService permissions) {
    this.mapper = mapper;
    this.equipmentMapper = equipmentMapper;
    this.repairOrderMapper = repairOrderMapper;
    this.auth = auth;
    this.permissions = permissions;
  }

  public record Form(
      @Size(max = 40, message = "编码最多40个字符") String code,
      @NotBlank(message = "名称必填") String name,
      Long parentId,
      Integer precision,
      Integer status,
      Integer sortNo,
      Long companyId) {}

  @GetMapping("/{resource}")
  public Map<String, Object> list(
      @PathVariable String resource, @RequestParam(required = false) Long companyId) {
    if (!permissions.allowed(menu(resource), "查看")
        && !permissions.allowed("equipment", "查看")
        && !(("material-categories".equals(resource) || "units".equals(resource))
            && permissions.menuActions().entrySet().stream()
                .anyMatch(e -> e.getKey().startsWith("inventory") && !e.getValue().isEmpty()))
        && !(("fault-types".equals(resource) || "fault-causes".equals(resource))
            && permissions.allowed("repair", "查看"))) permissions.require(menu(resource), "查看");
    String type = type(resource);
    var rows =
        mapper.selectList(
            new LambdaQueryWrapper<BaseItem>()
                .eq(BaseItem::getTenantId, auth.tenantId())
                .eq(BaseItem::getItemType, type)
                .eq(
                    "LOCATION".equals(type) && companyId != null,
                    BaseItem::getCompanyOrgId,
                    companyId)
                .orderByAsc(BaseItem::getSortNo)
                .orderByAsc(BaseItem::getId));
    return Map.of("data", rows.stream().map(BaseDataController::view).toList());
  }

  @PostMapping("/{resource}")
  public Map<String, Object> create(@PathVariable String resource, @Valid @RequestBody Form f) {
    permissions.require(menu(resource), "新增");
    String type = type(resource);
    f = withCode(type, f);
    if (type.equals("PROCESS_RESULT") && f.name().trim().equals("已解决"))
      throw new IllegalArgumentException("已解决是固定处理结果，不能自定义");
    if (mapper.selectCount(
            new LambdaQueryWrapper<BaseItem>()
                .eq(BaseItem::getTenantId, auth.tenantId())
                .eq(BaseItem::getItemType, type)
                .eq(BaseItem::getCode, f.code())
                .eq(BaseItem::getDeleted, 0))
        > 0) throw new IllegalArgumentException("编码已存在");
    validateCompany(type, f.companyId());
    validateParent(type, f.parentId(), null, f.companyId());
    var item = fromForm(type, f);
    item.setTenantId(auth.tenantId());
    mapper.insert(item);
    return Map.of("data", view(item));
  }

  @org.springframework.transaction.annotation.Transactional
  @PutMapping("/{resource}/{id}")
  public Map<String, Object> update(
      @PathVariable String resource, @PathVariable Long id, @Valid @RequestBody Form f) {
    permissions.require(menu(resource), "编辑");
    String type = type(resource);
    var old = require(type, id);
    f = withCode(type, f);
    if (type.equals("PROCESS_RESULT") && f.name().trim().equals("已解决"))
      throw new IllegalArgumentException("已解决是固定处理结果，不能自定义");
    if (mapper.selectCount(
            new LambdaQueryWrapper<BaseItem>()
                .eq(BaseItem::getTenantId, auth.tenantId())
                .eq(BaseItem::getItemType, type)
                .eq(BaseItem::getCode, f.code())
                .ne(BaseItem::getId, id)
                .eq(BaseItem::getDeleted, 0))
        > 0) throw new IllegalArgumentException("编码已存在");
    validateCompany(type, f.companyId());
    if ("LOCATION".equals(type) && !java.util.Objects.equals(old.getCompanyOrgId(), f.companyId()))
      throw new IllegalArgumentException("位置所属公司不能修改，请在对应公司下新增位置");
    validateParent(type, f.parentId(), id, f.companyId());
    old.setCode(f.code().trim());
    if (!old.getName().equals(f.name().trim())
        && (type.equals("MATERIAL_CATEGORY") || type.equals("UNIT"))) {
      String field = type.equals("UNIT") ? "unit" : "category";
      db.update(
          "UPDATE inv_material SET "
              + field
              + "=?,row_version=row_version+1 WHERE tenant_id=? AND "
              + field
              + "=?",
          f.name().trim(),
          auth.tenantId(),
          old.getName());
    }
    old.setName(f.name().trim());
    old.setParentId(f.parentId());
    old.setDecimalPrecision(f.precision() == null ? 0 : f.precision());
    old.setStatus(f.status() == null ? 1 : f.status());
    old.setSortNo(f.sortNo() == null ? 0 : f.sortNo());
    mapper.updateById(old);
    return Map.of("data", view(old));
  }

  @PatchMapping("/{resource}/{id}/status")
  public Map<String, Object> status(
      @PathVariable String resource,
      @PathVariable Long id,
      @RequestBody Map<String, Integer> body) {
    permissions.require(menu(resource), "编辑");
    var item = require(type(resource), id);
    item.setStatus(body.getOrDefault("status", 0));
    mapper.updateById(item);
    return Map.of("data", view(item));
  }

  @DeleteMapping("/{resource}/{id}")
  public Map<String, Object> delete(@PathVariable String resource, @PathVariable Long id) {
    permissions.require(menu(resource), "删除");
    String type = type(resource);
    var item = require(type, id);
    if (type.equals("MATERIAL_CATEGORY") || type.equals("UNIT")) {
      String field = type.equals("UNIT") ? "unit" : "category";
      if (db.queryForObject(
              "SELECT COUNT(*) FROM inv_material WHERE tenant_id=? AND " + field + "=?",
              Integer.class,
              auth.tenantId(),
              item.getName())
          > 0) throw new IllegalArgumentException("该基础数据已被物料使用，不能删除");
    }
    if (type.equals("CATEGORY")
        || type.equals("MATERIAL_CATEGORY")
        || type.equals("LOCATION")
        || type.equals("FAULT_TYPE")) {
      if (mapper.selectCount(
              new LambdaQueryWrapper<BaseItem>()
                  .eq(BaseItem::getTenantId, auth.tenantId())
                  .eq(BaseItem::getItemType, type)
                  .eq(BaseItem::getParentId, id)
                  .eq(BaseItem::getDeleted, 0))
          > 0) throw new IllegalArgumentException("该节点下还有子节点，不能删除");
    }
    if (type.equals("CATEGORY")
        && equipmentMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Equipment>()
                    .eq(Equipment::getTenantId, auth.tenantId())
                    .eq(Equipment::getCategory, item.getName()))
            > 0) throw new IllegalArgumentException("该分类已有设备使用，不能删除");
    if (type.equals("LOCATION")
        && equipmentMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Equipment>()
                    .eq(Equipment::getTenantId, auth.tenantId())
                    .eq(Equipment::getCompanyOrgId, item.getCompanyOrgId())
                    .like(Equipment::getFunctionLocation, item.getName()))
            > 0) throw new IllegalArgumentException("该功能位置已有设备使用，不能删除");
    if (type.equals("FAULT_TYPE")
        && repairOrderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RepairOrder>()
                    .eq(RepairOrder::getTenantId, auth.tenantId())
                    .eq(RepairOrder::getFaultType, item.getName())
                    .eq(RepairOrder::getDeleted, 0))
            > 0) throw new IllegalArgumentException("该故障类型已有维修工单使用，不能删除");
    if (type.equals("FAULT_CAUSE")
        && repairOrderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>()
                    .eq(RepairOrder::getTenantId, auth.tenantId())
                    .eq(RepairOrder::getFaultCause, item.getName())
                    .eq(RepairOrder::getDeleted, 0))
            > 0) throw new IllegalArgumentException("该故障原因已有维修工单使用，不能删除");
    mapper.deleteById(id);
    return Map.of("data", Map.of("deleted", true));
  }

  @GetMapping("/location-companies")
  public Map<String, Object> companies() {
    if (!permissions.allowed("location", "查看") && !permissions.allowed("base", "查看"))
      permissions.require("equipment", "查看");
    var rows =
        orgs.selectList(
            new LambdaQueryWrapper<com.windasoft.eam.system.OrgUnit>()
                .eq(com.windasoft.eam.system.OrgUnit::getTenantId, auth.tenantId())
                .in(com.windasoft.eam.system.OrgUnit::getOrgType, "集团", "公司")
                .orderByAsc(com.windasoft.eam.system.OrgUnit::getId));
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
                        x.getOrgType(),
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
    var company =
        orgs.selectOne(
            new LambdaQueryWrapper<com.windasoft.eam.system.OrgUnit>()
                .eq(com.windasoft.eam.system.OrgUnit::getTenantId, auth.tenantId())
                .eq(com.windasoft.eam.system.OrgUnit::getId, companyId)
                .eq(com.windasoft.eam.system.OrgUnit::getOrgType, "公司"));
    if (company == null || company.getStatus() != 1)
      throw new IllegalArgumentException("所属公司不存在或已停用");
  }

  private String type(String resource) {
    var type = TYPES.get(resource);
    if (type == null) throw new IllegalArgumentException("不支持的基础数据类型");
    return type;
  }

  private String menu(String resource) {
    type(resource);
    return "locations".equals(resource)
        ? "location"
        : "categories".equals(resource) ? "category" : "base";
  }

  private BaseItem require(String type, Long id) {
    var item =
        mapper.selectOne(
            new LambdaQueryWrapper<BaseItem>()
                .eq(BaseItem::getTenantId, auth.tenantId())
                .eq(BaseItem::getId, id)
                .eq(BaseItem::getItemType, type));
    if (item == null) throw new IllegalArgumentException("基础数据不存在");
    return item;
  }

  private void validateParent(String type, Long parentId, Long currentId, Long companyId) {
    if (parentId == null) return;
    var p =
        mapper.selectOne(
            new LambdaQueryWrapper<BaseItem>()
                .eq(BaseItem::getTenantId, auth.tenantId())
                .eq(BaseItem::getId, parentId)
                .eq(BaseItem::getItemType, type));
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

  private static Form withCode(String type, Form form) {
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
    return new Form(
        code,
        form.name(),
        form.parentId(),
        form.precision(),
        form.status(),
        form.sortNo(),
        form.companyId());
  }

  private static BaseItem fromForm(String type, Form f) {
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
