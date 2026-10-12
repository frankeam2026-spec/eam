package com.windasoft.eam.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.system.AuthContext;
import com.windasoft.eam.system.PermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentController {
  private final EquipmentMapper mapper;
  private final EquipmentFiles files;
  private final com.windasoft.eam.basedata.BaseItemMapper baseItems;
  private final com.windasoft.eam.system.OrgUnitMapper orgUnits;
  private final com.windasoft.eam.system.SystemUserMapper users;
  private final com.windasoft.eam.repair.RepairOrderMapper repairs;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final NumberCounterMapper counter;
  private final ObjectMapper objectMapper;
  private final AuthContext auth;
  private final PermissionService permissions;

  @Value("${eam.equipment.states}")
  private String equipmentStateConfig;

  public EquipmentController(
      EquipmentFiles files,
      EquipmentMapper mapper,
      NumberCounterMapper counter,
      ObjectMapper objectMapper,
      AuthContext auth,
      PermissionService permissions,
      com.windasoft.eam.basedata.BaseItemMapper baseItems,
      com.windasoft.eam.system.OrgUnitMapper orgUnits,
      com.windasoft.eam.system.SystemUserMapper users,
      com.windasoft.eam.repair.RepairOrderMapper repairs,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.files = files;
    this.baseItems = baseItems;
    this.orgUnits = orgUnits;
    this.users = users;
    this.repairs = repairs;
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.counter = counter;
    this.objectMapper = objectMapper;
    this.auth = auth;
    this.permissions = permissions;
  }

  public record Form(
      @Size(max = 32, message = "设备编号不能超过32个字符") String code,
      @NotBlank(message = "设备名称必填") String name,
      @NotBlank(message = "设备分类必选") String category,
      String model,
      String serial,
      @NotBlank(message = "所属公司必填") String company,
      @NotBlank(message = "所属部门必填") String department,
      String owner,
      String brand,
      @NotBlank(message = "设备状态必选") String state,
      @NotBlank(message = "使用状态必选") String useState,
      String source,
      LocalDate acquiredAt,
      BigDecimal amount,
      LocalDate warranty,
      String remark,
      String assetNo,
      String tagCode,
      String supplier,
      @NotBlank(message = "单位必选") String unit,
      LocalDate purchaseDate,
      LocalDate inServiceDate,
      LocalDate retirementDate,
      String level,
      @NotBlank(message = "功能位置必选") String functionLocation,
      String specialEquipment,
      String metrologyEquipment,
      String depreciationEnabled,
      String technicalParams,
      String imageName,
      String imageFileId,
      JsonNode documents,
      JsonNode children,
      JsonNode relatedSpares,
      LocalDate calibrationDate,
      LocalDate calibrationValidUntil,
      @Size(max = 100) String inspectorName,
      Long departmentOrgId) {}

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "10") long size,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String department,
      @RequestParam(required = false) String company,
      @RequestParam(required = false) String model,
      @RequestParam(required = false) String serial,
      @RequestParam(required = false) String owner,
      @RequestParam(required = false) String brand,
      @RequestParam(required = false) String useState,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) LocalDate acquiredFrom,
      @RequestParam(required = false) LocalDate acquiredTo,
      @RequestParam(required = false) LocalDate warrantyBefore,
      @RequestParam(defaultValue = "createdAt") String sortBy,
      @RequestParam(defaultValue = "desc") String direction) {
    var q =
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, auth.tenantId())
            .eq(Equipment::getDeleted, 0)
            .and(
                has(keyword),
                x ->
                    x.like(Equipment::getEquipmentCode, keyword)
                        .or()
                        .like(Equipment::getAssetNo, keyword)
                        .or()
                        .like(Equipment::getTagCode, keyword)
                        .or()
                        .like(Equipment::getName, keyword)
                        .or()
                        .like(Equipment::getModel, keyword)
                        .or()
                        .like(Equipment::getSerialNo, keyword)
                        .or()
                        .like(Equipment::getDepartment, keyword)
                        .or()
                        .like(Equipment::getOwnerName, keyword))
            .eq(has(category), Equipment::getCategory, category)
            .eq(has(state), Equipment::getEquipmentStatus, state)
            .eq(has(department), Equipment::getDepartment, department)
            .like(has(company), Equipment::getCompany, company)
            .like(has(model), Equipment::getModel, model)
            .like(has(serial), Equipment::getSerialNo, serial)
            .like(has(owner), Equipment::getOwnerName, owner)
            .like(has(brand), Equipment::getBrand, brand)
            .eq(has(useState), Equipment::getUseStatus, useState)
            .eq(has(source), Equipment::getSource, source)
            .ge(acquiredFrom != null, Equipment::getAcquiredAt, acquiredFrom)
            .le(acquiredTo != null, Equipment::getAcquiredAt, acquiredTo)
            .le(warrantyBefore != null, Equipment::getWarrantyUntil, warrantyBefore);
    permissions.filterEquipment(q);
    switch (sortBy) {
      case "equipmentCode" -> order(q, direction, Equipment::getEquipmentCode);
      case "name" -> order(q, direction, Equipment::getName);
      case "category" -> order(q, direction, Equipment::getCategory);
      case "model" -> order(q, direction, Equipment::getModel);
      case "serialNo" -> order(q, direction, Equipment::getSerialNo);
      case "department" -> order(q, direction, Equipment::getDepartment);
      case "ownerName" -> order(q, direction, Equipment::getOwnerName);
      case "brand" -> order(q, direction, Equipment::getBrand);
      case "equipmentStatus" -> order(q, direction, Equipment::getEquipmentStatus);
      case "useStatus" -> order(q, direction, Equipment::getUseStatus);
      case "source" -> order(q, direction, Equipment::getSource);
      case "acquiredAt" -> order(q, direction, Equipment::getAcquiredAt);
      case "acquiredAmount" -> order(q, direction, Equipment::getAcquiredAmount);
      case "warrantyUntil" -> order(q, direction, Equipment::getWarrantyUntil);
      default -> order(q, direction, Equipment::getCreatedAt);
    }
    var result =
        mapper.selectPage(new Page<>(Math.max(1, page), Math.min(10000, Math.max(1, size))), q);
    return Map.of(
        "data",
        result.getRecords().stream().map(this::toView).toList(),
        "page",
        Map.of(
            "number", result.getCurrent(), "size", result.getSize(), "total", result.getTotal()));
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable Long id) {
    var e = require(id);
    permissions.checkEquipment(e, "查看");
    return Map.of("data", toView(e));
  }

  @GetMapping(value = "/{id}/qrcode", produces = "image/png")
  public org.springframework.http.ResponseEntity<byte[]> qrCode(@PathVariable Long id)
      throws Exception {
    var equipment = require(id);
    permissions.checkEquipment(equipment, "查看");
    var matrix =
        new com.google.zxing.MultiFormatWriter()
            .encode(
                equipment.getEquipmentCode(),
                com.google.zxing.BarcodeFormat.QR_CODE,
                240,
                240,
                Map.of(
                    com.google.zxing.EncodeHintType.CHARACTER_SET,
                    "UTF-8",
                    com.google.zxing.EncodeHintType.MARGIN,
                    2));
    var bytes = new java.io.ByteArrayOutputStream();
    com.google.zxing.client.j2se.MatrixToImageWriter.writeToStream(matrix, "PNG", bytes);
    return org.springframework.http.ResponseEntity.ok()
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(bytes.toByteArray());
  }

  @PostMapping
  @Transactional
  public Map<String, Object> create(@Valid @RequestBody Form form) {
    validateState(form.state());
    var e = fromForm(form);
    setAccessFields(e, form);
    permissions.checkEquipment(e, "新增");
    String code = has(form.code()) ? form.code().trim() : nextCode();
    assertCodeAvailable(code, null);
    e.setEquipmentCode(code);
    e.setRowVersion(0);
    e.setDeleted(0);
    e.setCreatedBy(auth.userId());
    e.setUpdatedBy(auth.userId());
    mapper.insert(e);
    return Map.of("data", toView(e));
  }

  @PutMapping("/{id}")
  public Map<String, Object> update(
      @PathVariable Long id, @Valid @RequestBody Form form, @RequestParam int rowVersion) {
    validateState(form.state());
    var old = require(id);
    permissions.checkEquipment(old, "编辑");
    VersionGuard.check(old.getRowVersion(), rowVersion);
    var e = fromForm(form);
    e.setId(id);
    setAccessFields(e, form);
    permissions.checkEquipment(e, "编辑");
    String code = has(form.code()) ? form.code().trim() : old.getEquipmentCode();
    assertCodeAvailable(code, id);
    e.setEquipmentCode(code);
    e.setRowVersion(rowVersion);
    e.setUpdatedBy(auth.userId());
    VersionGuard.updated(mapper.updateById(e));
    return Map.of("data", toView(require(id)));
  }

  @DeleteMapping("/{id}")
  public Map<String, Object> delete(@PathVariable Long id) {
    var e = require(id);
    permissions.checkEquipment(e, "删除");
    if (mapper.deleteById(id) != 1) throw new IllegalArgumentException("设备不存在");
    return Map.of("data", Map.of("deleted", true));
  }

  @GetMapping("/batch-options")
  public Map<String, Object> batchOptions() {
    if (!permissions.allowed("equipment", "新增")) permissions.require("equipment", "编辑");
    var departments =
        orgUnits
            .selectList(
                new LambdaQueryWrapper<com.windasoft.eam.system.OrgUnit>()
                    .eq(com.windasoft.eam.system.OrgUnit::getTenantId, auth.tenantId())
                    .eq(com.windasoft.eam.system.OrgUnit::getStatus, 1))
            .stream()
            .map(
                o ->
                    Map.of(
                        "id",
                        String.valueOf(o.getId()),
                        "name",
                        o.getName(),
                        "type",
                        o.getOrgType(),
                        "parentId",
                        o.getParentId() == null ? "" : String.valueOf(o.getParentId())))
            .toList();
    var employees =
        users
            .selectList(
                new LambdaQueryWrapper<com.windasoft.eam.system.SystemUser>()
                    .eq(com.windasoft.eam.system.SystemUser::getTenantId, auth.tenantId())
                    .eq(com.windasoft.eam.system.SystemUser::getStatus, 1))
            .stream()
            .map(
                u ->
                    Map.of(
                        "id",
                        String.valueOf(u.getId()),
                        "name",
                        u.getDisplayName(),
                        "account",
                        u.getAccount()))
            .toList();
    return Map.of("departments", departments, "users", employees);
  }

  public record BatchForm(
      @jakarta.validation.constraints.NotEmpty List<Long> ids,
      @NotBlank String operation,
      String value) {}

  @PostMapping("/batch")
  @Transactional
  public Map<String, Object> batch(@Valid @RequestBody BatchForm form) {
    var ids = form.ids().stream().distinct().toList();
    if (ids.size() > 1000) throw new IllegalArgumentException("单次最多操作1000台设备");
    String op = form.operation();
    if (!List.of("location", "category", "department", "owner", "state", "delete").contains(op))
      throw new IllegalArgumentException("不支持的批量操作");
    var targets = ids.stream().map(this::require).toList();
    for (var e : targets) permissions.checkEquipment(e, op.equals("delete") ? "删除" : "编辑");
    String value = form.value() == null ? "" : form.value().trim();
    if (!op.equals("delete") && value.isEmpty()) throw new IllegalArgumentException("请选择修改后的值");
    var base =
        (op.equals("location") || op.equals("category"))
            ? baseItems.selectOne(
                new LambdaQueryWrapper<com.windasoft.eam.basedata.BaseItem>()
                    .eq(com.windasoft.eam.basedata.BaseItem::getTenantId, auth.tenantId())
                    .eq(com.windasoft.eam.basedata.BaseItem::getId, value)
                    .eq(
                        com.windasoft.eam.basedata.BaseItem::getItemType,
                        op.equals("location") ? "LOCATION" : "CATEGORY")
                    .eq(com.windasoft.eam.basedata.BaseItem::getStatus, 1))
            : null;
    if ((op.equals("location") || op.equals("category")) && base == null)
      throw new IllegalArgumentException("基础数据不存在或已停用");
    for (var e : targets) {
      switch (op) {
        case "delete" -> {
          if (mapper.deleteById(e.getId()) != 1) throw new IllegalArgumentException("删除失败");
          continue;
        }
        case "category" -> e.setCategory(base.getName());
        case "location" -> {
          var parts = new java.util.ArrayList<String>();
          var node = base;
          while (node != null) {
            parts.addFirst(node.getName());
            node =
                node.getParentId() == null
                    ? null
                    : baseItems.selectOne(
                        new LambdaQueryWrapper<com.windasoft.eam.basedata.BaseItem>()
                            .eq(com.windasoft.eam.basedata.BaseItem::getTenantId, auth.tenantId())
                            .eq(com.windasoft.eam.basedata.BaseItem::getId, node.getParentId()));
            if (parts.size() > 100) throw new IllegalArgumentException("位置树结构异常");
          }
          e.setFunctionLocation(String.join("·", parts));
        }
        case "department" -> {
          var dept =
              orgUnits.selectOne(
                  new LambdaQueryWrapper<com.windasoft.eam.system.OrgUnit>()
                      .eq(com.windasoft.eam.system.OrgUnit::getTenantId, auth.tenantId())
                      .eq(com.windasoft.eam.system.OrgUnit::getId, value)
                      .eq(com.windasoft.eam.system.OrgUnit::getOrgType, "部门")
                      .eq(com.windasoft.eam.system.OrgUnit::getStatus, 1));
          if (dept == null) throw new IllegalArgumentException("部门不存在或已停用");
          var company = orgUnits.selectById(permissions.companyOfOrganization(dept.getId()));
          e.setCompany(company.getName());
          e.setCompanyOrgId(company.getId());
          e.setDepartment(dept.getName());
          e.setDepartmentOrgId(dept.getId());
        }
        case "owner" -> {
          var user =
              users.selectOne(
                  new LambdaQueryWrapper<com.windasoft.eam.system.SystemUser>()
                      .eq(com.windasoft.eam.system.SystemUser::getTenantId, auth.tenantId())
                      .eq(com.windasoft.eam.system.SystemUser::getId, value)
                      .eq(com.windasoft.eam.system.SystemUser::getStatus, 1));
          if (user == null) throw new IllegalArgumentException("负责人不存在或已停用");
          e.setOwnerName(user.getDisplayName());
          e.setOwnerUserId(user.getId());
        }
        case "state" -> {
          validateState(value);
          e.setEquipmentStatus(value);
        }
      }
      permissions.checkEquipment(e, "编辑");
      e.setUpdatedBy(auth.userId());
      if (mapper.updateById(e) != 1) throw new IllegalArgumentException("设备已被他人修改，请刷新重试");
    }
    return Map.of("data", Map.of("count", ids.size()));
  }

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.repair.RepairWorkflowMapper repairWorkflow;

  @GetMapping("/{id}/history")
  public Map<String, Object> history(@PathVariable Long id) {
    var equipment = require(id);
    permissions.checkEquipment(equipment, "查看");
    var repairRows =
        repairs.selectList(
            new LambdaQueryWrapper<com.windasoft.eam.repair.RepairOrder>()
                .eq(com.windasoft.eam.repair.RepairOrder::getTenantId, auth.tenantId())
                .eq(
                    com.windasoft.eam.repair.RepairOrder::getEquipmentCode,
                    equipment.getEquipmentCode())
                .orderByDesc(com.windasoft.eam.repair.RepairOrder::getFaultAt));
    var repairHistory =
        repairRows.stream()
            .filter(
                r -> {
                  try {
                    permissions.checkRepair(r, "查看");
                    return true;
                  } catch (org.springframework.web.server.ResponseStatusException denied) {
                    return false;
                  }
                })
            .map(
                r ->
                    Map.ofEntries(
                        Map.entry("number", safe(r.getOrderNo())),
                        Map.entry("time", r.getFaultAt() == null ? "" : r.getFaultAt().toString()),
                        Map.entry("description", safe(r.getDescription())),
                        Map.entry("person", safe(r.getAssignee())),
                        Map.entry("status", safe(r.getStatus())),
                        Map.entry("result", safe(r.getRepairAction()))))
            .toList();
    var records =
        jdbc.queryForList(
            "SELECT record_type AS type,record_no AS number,occurred_at AS"
                + " time,description,operator_name AS person,status,result FROM"
                + " eam_equipment_history WHERE tenant_id=? AND equipment_id=? ORDER BY occurred_at"
                + " DESC,id DESC",
            auth.tenantId(),
            id);
    var installed =
        new java.util.ArrayList<Map<String, Object>>(repairWorkflow.installed(auth.tenantId(), id));
    installed.addAll(
        jdbc.queryForList(
            "SELECT CAST(u.id AS CHAR)"
                + " id,m.code,m.name,m.model,u.quantity,u.status,DATE_FORMAT(u.created_at,'%Y-%m-%d"
                + " %H:%i') installedAt FROM inv_usage u JOIN inv_material m ON"
                + " m.tenant_id=u.tenant_id AND m.id=u.material_id WHERE u.tenant_id=? AND"
                + " u.equipment_id=? AND u.status='在用' ORDER BY u.created_at DESC,u.id DESC",
            auth.tenantId(), id));
    return Map.of(
        "installedSpares",
        installed,
        "data",
        toView(equipment),
        "repairs",
        repairHistory,
        "maintenance",
        records.stream().filter(r -> "MAINTENANCE".equals(r.get("type"))).toList(),
        "inspections",
        records.stream().filter(r -> "INSPECTION".equals(r.get("type"))).toList(),
        "spareChanges",
        records.stream().filter(r -> "SPARE_CHANGE".equals(r.get("type"))).toList());
  }

  private Equipment require(Long id) {
    var e =
        mapper.selectOne(
            new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getTenantId, auth.tenantId())
                .eq(Equipment::getId, id));
    if (e == null) throw new IllegalArgumentException("设备不存在");
    return e;
  }

  private void assertCodeAvailable(String code, Long currentId) {
    var q =
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, auth.tenantId())
            .eq(Equipment::getEquipmentCode, code);
    if (currentId != null) q.ne(Equipment::getId, currentId);
    if (mapper.selectCount(q) > 0) throw new IllegalArgumentException("设备编号已存在");
  }

  private String nextCode() {
    String year = String.valueOf(LocalDate.now().getYear());
    counter.nextEquipmentNumber(year);
    return "EQ-" + year + "-" + String.format("%04d", counter.currentValue());
  }

  private static boolean has(String s) {
    return s != null && !s.isBlank();
  }

  private void validateState(String state) {
    List<String> allowed =
        Arrays.stream(equipmentStateConfig.split(",")).map(String::trim).toList();
    if (!allowed.contains(state))
      throw new IllegalArgumentException("设备状态无效，可选：" + String.join("、", allowed));
  }

  private static <T> void order(
      LambdaQueryWrapper<Equipment> q,
      String direction,
      com.baomidou.mybatisplus.core.toolkit.support.SFunction<Equipment, T> col) {
    if ("asc".equalsIgnoreCase(direction)) q.orderByAsc(col);
    else q.orderByDesc(col);
  }

  private Equipment fromForm(Form f) {
    var e = new Equipment();
    e.setName(f.name().trim());
    e.setCategory(f.category());
    e.setModel(f.model());
    e.setSerialNo(f.serial());
    e.setCompany(f.company());
    e.setDepartment(f.department());
    e.setOwnerName(f.owner());
    e.setBrand(f.brand());
    e.setEquipmentStatus(f.state());
    e.setUseStatus(f.useState());
    e.setSource(f.source());
    e.setAcquiredAt(f.acquiredAt());
    e.setAcquiredAmount(f.amount());
    e.setWarrantyUntil(f.warranty());
    e.setDescription(f.remark());
    e.setAssetNo(f.assetNo());
    e.setTagCode(f.tagCode());
    e.setUnitName(f.unit());
    e.setSupplier(f.supplier());
    e.setPurchaseDate(f.purchaseDate());
    e.setInServiceDate(f.inServiceDate());
    e.setExpectedRetirementDate(f.retirementDate());
    e.setEquipmentLevel(f.level());
    e.setFunctionLocation(f.functionLocation());
    e.setSpecialEquipment(f.specialEquipment());
    e.setMetrologyEquipment(f.metrologyEquipment());
    e.setDepreciationEnabled(f.depreciationEnabled());
    e.setTechnicalParams(f.technicalParams());
    e.setImageName(f.imageName());
    e.setImageFileId(files.validateImage(f.imageFileId()));
    e.setCalibrationDate(f.calibrationDate());
    e.setCalibrationValidUntil(f.calibrationValidUntil());
    e.setInspectorName(f.inspectorName());
    e.setDocumentsJson(jsonString(files.validate(f.documents())));
    e.setChildrenJson(jsonString(f.children()));
    e.setRelatedSparesJson(jsonString(f.relatedSpares()));
    return e;
  }

  private void setAccessFields(Equipment e, Form f) {
    var department =
        f.departmentOrgId() != null
            ? permissions.requireDepartment(f.departmentOrgId())
            : permissions.requireDepartment(f.company(), f.department());
    e.setTenantId(auth.tenantId());
    e.setCompanyOrgId(permissions.companyOfOrganization(department.getId()));
    e.setCompany(orgUnits.selectById(e.getCompanyOrgId()).getName());
    e.setDepartment(department.getName());
    e.setDepartmentOrgId(department.getId());
    e.setOwnerUserId(permissions.userIdByName(f.owner()));
  }

  private String jsonString(JsonNode node) {
    try {
      return node == null || node.isNull() ? "[]" : objectMapper.writeValueAsString(node);
    } catch (Exception ignored) {
      return "[]";
    }
  }

  private Map<String, Object> toView(Equipment e) {
    return Map.ofEntries(
        Map.entry("id", String.valueOf(e.getId())),
        Map.entry("code", safe(e.getEquipmentCode())),
        Map.entry("name", safe(e.getName())),
        Map.entry("category", safe(e.getCategory())),
        Map.entry("model", safe(e.getModel())),
        Map.entry("serial", safe(e.getSerialNo())),
        Map.entry("company", safe(e.getCompany())),
        Map.entry("department", safe(e.getDepartment())),
        Map.entry(
            "departmentOrgId",
            e.getDepartmentOrgId() == null ? "" : e.getDepartmentOrgId().toString()),
        Map.entry("owner", safe(e.getOwnerName())),
        Map.entry("brand", safe(e.getBrand())),
        Map.entry("state", safe(e.getEquipmentStatus())),
        Map.entry("useState", safe(e.getUseStatus())),
        Map.entry("source", safe(e.getSource())),
        Map.entry(
            "acquiredAt",
            date(e.getPurchaseDate() != null ? e.getPurchaseDate() : e.getAcquiredAt())),
        Map.entry(
            "amount", e.getAcquiredAmount() == null ? "" : e.getAcquiredAmount().toPlainString()),
        Map.entry("warranty", e.getWarrantyUntil() == null ? "" : e.getWarrantyUntil().toString()),
        Map.entry("remark", safe(e.getDescription())),
        Map.entry("rowVersion", e.getRowVersion() == null ? 0 : e.getRowVersion()),
        Map.entry("assetNo", safe(e.getAssetNo())),
        Map.entry("tagCode", safe(e.getTagCode())),
        Map.entry("unit", safe(e.getUnitName())),
        Map.entry("supplier", safe(e.getSupplier())),
        Map.entry("purchaseDate", date(e.getPurchaseDate())),
        Map.entry("inServiceDate", date(e.getInServiceDate())),
        Map.entry("retirementDate", date(e.getExpectedRetirementDate())),
        Map.entry("level", safe(e.getEquipmentLevel())),
        Map.entry("functionLocation", safe(e.getFunctionLocation())),
        Map.entry("specialEquipment", safe(e.getSpecialEquipment())),
        Map.entry("metrologyEquipment", safe(e.getMetrologyEquipment())),
        Map.entry("depreciationEnabled", safe(e.getDepreciationEnabled())),
        Map.entry("technicalParams", safe(e.getTechnicalParams())),
        Map.entry("calibrationDate", date(e.getCalibrationDate())),
        Map.entry("calibrationValidUntil", date(e.getCalibrationValidUntil())),
        Map.entry("inspectorName", safe(e.getInspectorName())),
        Map.entry("imageName", safe(e.getImageName())),
        Map.entry("imageFileId", safe(e.getImageFileId())),
        Map.entry("documents", json(e.getDocumentsJson())),
        Map.entry("children", json(e.getChildrenJson())),
        Map.entry("relatedSpares", json(e.getRelatedSparesJson())));
  }

  private static String date(LocalDate d) {
    return d == null ? "" : d.toString();
  }

  private Object json(String value) {
    try {
      return value == null ? objectMapper.createArrayNode() : objectMapper.readTree(value);
    } catch (Exception ignored) {
      return objectMapper.createArrayNode();
    }
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }
}
