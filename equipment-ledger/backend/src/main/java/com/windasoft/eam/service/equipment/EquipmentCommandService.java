package com.windasoft.eam.service.equipment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.dto.equipment.EquipmentBatchForm;
import com.windasoft.eam.dto.equipment.EquipmentForm;
import com.windasoft.eam.dto.equipment.EquipmentView;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.equipment.EquipmentWriteRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class EquipmentCommandService {
  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.business.BusinessValueService custom;

  @org.springframework.beans.factory.annotation.Autowired
  private EquipmentInputValidation inputValidation;

  private final EquipmentRelationsService relations;
  private final EquipmentFileService files;
  private final EquipmentQueryService queries;
  private final EquipmentWriteRepository repository;
  private final ObjectMapper objectMapper;
  private final AuthContext auth;
  private final PermissionService permissions;

  @Value("${eam.equipment.states}")
  private String equipmentStateConfig;

  public EquipmentCommandService(
      EquipmentRelationsService relations,
      EquipmentFileService files,
      EquipmentQueryService queries,
      EquipmentWriteRepository repository,
      ObjectMapper objectMapper,
      AuthContext auth,
      PermissionService permissions) {
    this.relations = relations;
    this.files = files;
    this.queries = queries;
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.auth = auth;
    this.permissions = permissions;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public EquipmentView create(EquipmentForm form) {
    inputValidation.validate(form);
    validateState(form.state());
    relations.enter(
        java.util.Collections.singletonList(
            permissions.companyOfOrganization(form.departmentOrgId())));
    var e = fromForm(form);
    setAccessFields(e, form);
    inputValidation.validateEntity(e);
    permissions.checkEquipment(e, "新增");
    String code = has(form.code()) ? form.code().trim() : nextCode();
    assertCodeAvailable(code, null);
    e.setEquipmentCode(code);
    e.setRowVersion(0);
    e.setDeleted(0);
    e.setCreatedBy(auth.userId());
    e.setUpdatedBy(auth.userId());
    repository.insert(e);
    saveOwners(e);
    relations.save(e, form.children(), form.relatedSpares());
    custom.save(
        "equipment",
        e.getId(),
        form.customFields(),
        form.customSchemaVersion(),
        String.valueOf(e.getCategoryId()),
        true);
    return toView(require(e.getId()));
  }

  /**
   * Called only inside the import transaction after batch-wide reference and permission validation.
   */
  public void insertImported(
      java.util.List<com.windasoft.eam.dto.imports.PreparedEquipmentImport> rows) {
    if (!org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive()) throw new IllegalStateException("导入必须在事务中执行");
    var reserved =
        new java.util.HashSet<>(
            repository
                .importCodeKeys(
                    rows.stream()
                        .map(r -> r.form().code())
                        .filter(c -> c != null && !c.isBlank())
                        .toList())
                .values());
    for (var row : rows) {
      var e = row.entity();
      String code = e.getEquipmentCode();
      if (!has(code)) {
        do {
          code = nextCode();
        } while (reserved.contains(repository.importCodeKeys(java.util.List.of(code)).get(code)));
      }
      // Explicit codes were checked in one batch after acquiring the original equipment mutex.
      e.setEquipmentCode(code);
      e.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
      repository.insertImported(e);
      if (e.getOwnerIds() != null)
        for (int i = 0; i < e.getOwnerIds().size(); i++) {
          // Names were resolved and locked with the original system accounts during revalidation.
          repository.addOwner(
              auth.tenantId(),
              e.getId(),
              e.getOwnerIds().get(i),
              row.ownerNames().get(e.getOwnerIds().get(i)),
              i);
        }
      custom.savePreparedNew("equipment", e.getId(), row.custom());
    }
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public EquipmentView update(Long id, EquipmentForm form, int rowVersion) {
    return updateInternal(id, form, rowVersion, true);
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public EquipmentView updateDetails(Long id, EquipmentForm form, int rowVersion) {
    return updateInternal(id, form, rowVersion, false);
  }

  private EquipmentView updateInternal(Long id, EquipmentForm form, int rowVersion, boolean replaceRelations) {
    validateState(form.state());
    var old = require(id);
    relations.enter(
        java.util.Arrays.asList(
            old.getCompanyOrgId(), permissions.companyOfOrganization(form.departmentOrgId())));
    old = require(id);
    permissions.checkEquipment(old, "编辑");
    VersionGuard.check(old.getRowVersion(), rowVersion);
    var e = fromForm(form);
    e.setId(id);
    setAccessFields(e, form, old);
    permissions.checkEquipment(e, "编辑");
    relations.checkMove(old, e);
    String code = has(form.code()) ? form.code().trim() : old.getEquipmentCode();
    assertCodeAvailable(code, id);
    e.setEquipmentCode(code);
    e.setRowVersion(rowVersion);
    e.setUpdatedBy(auth.userId());
    VersionGuard.updated(repository.update(e));
    saveOwners(e);
    if (replaceRelations) relations.save(e, form.children(), form.relatedSpares());
    custom.save(
        "equipment",
        e.getId(),
        form.customFields(),
        form.customSchemaVersion(),
        String.valueOf(e.getCategoryId()),
        false);
    return toView(require(id));
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public EquipmentView updateRelations(Long id, JsonNode rows, int rowVersion, boolean children) {
    var parent = require(id);
    relations.enter(java.util.Collections.singletonList(parent.getCompanyOrgId()));
    parent = require(id);
    permissions.checkEquipment(parent, "编辑");
    VersionGuard.check(parent.getRowVersion(), rowVersion);
    VersionGuard.updated(repository.advanceVersion(auth.tenantId(), id, rowVersion, auth.userId()));
    if (children) relations.saveChildren(parent, rows);
    else relations.saveBom(parent, rows);
    return toView(require(id));
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public boolean delete(Long id) {
    var e = require(id);
    relations.enter(java.util.Collections.singletonList(e.getCompanyOrgId()));
    e = require(id);
    permissions.checkEquipment(e, "删除");
    relations.checkDelete(id);
    relations.cleanup(id);
    if (repository.delete(id) != 1)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0064");
    repository.deleteOwners(auth.tenantId(), id);
    return true;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public int batch(EquipmentBatchForm form) {
    var ids = form.ids().stream().distinct().toList();
    if (ids.size() > 1000)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0038");
    String op = form.operation();
    if (!List.of("location", "category", "department", "owner", "state", "delete").contains(op))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0039");
    var targets = ids.stream().map(this::require).toList();
    relations.enter(targets.stream().map(Equipment::getCompanyOrgId).toList());
    targets = ids.stream().map(this::require).toList();
    for (var e : targets) permissions.checkEquipment(e, op.equals("delete") ? "删除" : "编辑");
    String value = form.value() == null ? "" : form.value().trim();
    if (!op.equals("delete") && !op.equals("owner") && value.isEmpty())
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0040");
    var base =
        (op.equals("location") || op.equals("category"))
            ? repository.activeBase(
                auth.tenantId(), value, op.equals("location") ? "LOCATION" : "CATEGORY")
            : null;
    if ((op.equals("location") || op.equals("category")) && base == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0041");
    for (var e : targets) {
      switch (op) {
        case "delete" -> {
          relations.checkDelete(e.getId());
          relations.cleanup(e.getId());
          if (repository.delete(e.getId()) != 1)
            throw new com.windasoft.eam.common.LocalizedArgumentException("ui.equipment.text0278");
          repository.deleteOwners(auth.tenantId(), e.getId());
          continue;
        }
        case "category" -> {
          e.setCategoryId(base.getId());
          e.setCategory(base.getName());
        }
        case "location" -> {
          e.setFunctionLocationId(base.getId());
          e.setFunctionLocation(locationPath(base.getId(), e.getCompanyOrgId(), true));
        }
        case "department" -> {
          var dept = repository.activeDepartment(auth.tenantId(), value);
          if (dept == null)
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.equipment.message0042");
          permissions.checkEquipmentDepartment(dept.getId(), "编辑");
          var company = repository.organization(permissions.companyOfOrganization(dept.getId()));
          var original = require(e.getId());
          var moved = new Equipment();
          moved.setCompanyOrgId(company.getId());
          relations.checkMove(original, moved);
          e.setCompany(company.getName());
          if (e.getFunctionLocationId() != null
              && !java.util.Objects.equals(e.getCompanyOrgId(), company.getId()))
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.equipment.message0043");
          e.setCompanyOrgId(company.getId());
          e.setDepartment(dept.getName());
          e.setDepartmentOrgId(dept.getId());
          e.setDepartmentPath(organizationPath(dept.getId()));
        }
        case "owner" -> {
          var ownerIds =
              form.ownerIds() != null
                  ? form.ownerIds()
                  : (value.isEmpty() ? List.<Long>of() : List.of(Long.valueOf(value)));
          assignOwners(e, ownerIds, e);
        }
        case "state" -> {
          validateState(value);
          e.setEquipmentStatus(value);
        }
      }
      permissions.checkEquipment(e, "编辑");
      e.setUpdatedBy(auth.userId());
      if (repository.update(e) != 1)
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0044");
      if (op.equals("owner")) saveOwners(e);
    }
    return ids.size();
  }

  private Equipment require(Long id) {
    var e = repository.find(auth.tenantId(), id);
    if (e == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0064");
    return e;
  }

  private void assertCodeAvailable(String code, Long currentId) {
    if (repository.countCode(auth.tenantId(), code, currentId) > 0)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0045");
  }

  private String nextCode() {
    String year = String.valueOf(LocalDate.now().getYear());
    long minimum = 0;
    while (true) {
      repository.nextEquipmentNumber(year, minimum);
      long sequence = repository.currentValue();
      String code = "EQ-" + year + "-" + String.format("%04d", sequence);
      if (repository.countCode(auth.tenantId(), code, null) == 0) return code;
      // Recover a missing/stale counter in the same transaction instead of retrying the same code.
      minimum = Math.max(sequence, repository.maxEquipmentNumber(year));
    }
  }

  private static boolean has(String s) {
    return s != null && !s.isBlank();
  }

  private void validateState(String state) {
    List<String> allowed =
        Arrays.stream(equipmentStateConfig.split(",")).map(String::trim).toList();
    if (!allowed.contains(state))
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.equipment.message0046", String.join("、", allowed));
  }

  private String locationPath(Long id, Long company, boolean required) {
    if (id == null) {
      if (required)
        throw new com.windasoft.eam.common.LocalizedArgumentException("ui.equipment.text0109");
      return null;
    }
    var parts = new java.util.ArrayList<String>();
    var visited = new java.util.HashSet<Long>();
    var node = repository.baseItem(id);
    if (node == null) {
      if (required)
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0030");
      return null;
    }
    while (node != null) {
      if (!visited.add(node.getId()))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0031");
      if (!node.getTenantId().equals(auth.tenantId())
          || !"LOCATION".equals(node.getItemType())
          || !java.util.Objects.equals(node.getCompanyOrgId(), company))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0032");
      if (required && node.getId().equals(id) && !Integer.valueOf(1).equals(node.getStatus()))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0033");
      parts.addFirst(node.getName());
      node = node.getParentId() == null ? null : repository.baseItem(node.getParentId());
    }
    return String.join(" > ", parts);
  }

  private String baseName(Long id, String type, boolean required) {
    if (id == null) {
      if (required)
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0047");
      return "";
    }
    var item = repository.baseItem(id);
    if (item == null
        || !java.util.Objects.equals(auth.tenantId(), item.getTenantId())
        || !type.equals(item.getItemType())
        || !Integer.valueOf(1).equals(item.getStatus()))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0048");
    return item.getName();
  }

  private String organizationPath(Long id) {
    var parts = new java.util.ArrayList<String>();
    var visited = new java.util.HashSet<Long>();
    var node = repository.organization(id);
    while (node != null) {
      if (!visited.add(node.getId())
          || !java.util.Objects.equals(auth.tenantId(), node.getTenantId()))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0049");
      parts.addFirst(node.getName());
      node = node.getParentId() == null ? null : repository.organization(node.getParentId());
    }
    return String.join(" > ", parts);
  }

  private Equipment fromForm(EquipmentForm f) {
    var e = new Equipment();
    e.setName(f.name().trim());
    e.setCategoryId(f.categoryId());
    e.setCategory(baseName(f.categoryId(), "CATEGORY", true));
    e.setModel(f.model());
    e.setSerialNo(f.serial());
    e.setCompany(f.company());
    e.setDepartment(f.department());
    e.setOwnerName(f.owner());
    e.setBrand(f.brand());
    e.setEquipmentStatus(f.state());
    e.setUseStatusId(f.useStateId());
    e.setUseStatus(baseName(f.useStateId(), "USE_STATUS", true));
    if (f.sourceId() == null && has(f.source()))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0050");
    e.setSourceId(f.sourceId());
    e.setSource(baseName(f.sourceId(), "SOURCE", false));
    e.setAcquiredAt(f.acquiredAt());
    e.setAcquiredAmount(f.amount());
    e.setWarrantyUntil(f.warranty());
    e.setDescription(f.remark());
    e.setAssetNo(f.assetNo());
    e.setTagCode(f.tagCode());
    e.setUnitId(f.unitId());
    e.setUnitName(baseName(f.unitId(), "UNIT", true));
    e.setSupplier(f.supplier());
    e.setPurchaseDate(f.purchaseDate());
    e.setInServiceDate(f.inServiceDate());
    e.setExpectedRetirementDate(f.retirementDate());
    e.setEquipmentLevel(f.level());
    e.setFunctionLocationId(f.locationId());
    e.setSpecialEquipment(f.specialEquipment());
    e.setMetrologyEquipment(f.metrologyEquipment());
    e.setDepreciationEnabled(f.depreciationEnabled());
    e.setTechnicalParams(f.technicalParams());
    var images = files.validateImages(f.images(), f.imageFileId(), f.imageName());
    e.setImagesJson(jsonString(images));
    e.setImageName(images.isEmpty() ? "" : images.get(0).path("name").asText());
    e.setImageFileId(images.isEmpty() ? "" : images.get(0).path("id").asText());
    e.setCalibrationDate(f.calibrationDate());
    e.setCalibrationValidUntil(f.calibrationValidUntil());
    e.setInspectorName(f.inspectorName());
    e.setDocumentsJson(jsonString(files.validate(f.documents())));
    return e;
  }

  private void setAccessFields(Equipment e, EquipmentForm f) {
    setAccessFields(e, f, null);
  }

  private void setAccessFields(Equipment e, EquipmentForm f, Equipment previous) {
    var department =
        f.departmentOrgId() != null
            ? permissions.requireDepartment(f.departmentOrgId())
            : permissions.requireDepartment(f.company(), f.department());
    e.setTenantId(auth.tenantId());
    e.setCompanyOrgId(permissions.companyOfOrganization(department.getId()));
    e.setCompany(repository.organization(e.getCompanyOrgId()).getName());
    e.setDepartment(department.getName());
    e.setDepartmentOrgId(department.getId());
    e.setDepartmentPath(organizationPath(department.getId()));
    if (previous == null
        || !java.util.Objects.equals(previous.getDepartmentOrgId(), department.getId()))
      permissions.checkEquipmentDepartment(department.getId(), previous == null ? "新增" : "编辑");
    var selectedOwners =
        f.ownerIds() != null
            ? f.ownerIds()
            : (f.ownerId() == null ? List.<Long>of() : List.of(f.ownerId()));
    assignOwners(e, selectedOwners, previous);
    if (selectedOwners.isEmpty()
        && previous != null
        && previous.getOwnerUserId() == null
        && ownerRows(previous.getId()).isEmpty()
        && safe(f.owner()).equals(safe(previous.getOwnerName())))
      e.setOwnerName(safe(previous.getOwnerName()));
    e.setFunctionLocation(locationPath(f.locationId(), e.getCompanyOrgId(), true));
  }

  private List<Map<String, Object>> ownerRows(Long id) {
    if (id == null) return List.of();
    return repository.owners(auth.tenantId(), id);
  }

  private void assignOwners(Equipment e, List<Long> selected, Equipment previous) {
    if (selected.stream().anyMatch(java.util.Objects::isNull))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0051");
    var ids = selected.stream().distinct().toList();
    if (ids.size() > 50)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0001");
    var old = previous == null ? List.<Map<String, Object>>of() : ownerRows(previous.getId());
    var names = new java.util.ArrayList<String>();
    for (Long id : ids) {
      var prior = old.stream().filter(x -> id.toString().equals(x.get("id"))).findFirst();
      var user = repository.user(auth.tenantId(), id);
      if (prior.isEmpty() && (user == null || !Integer.valueOf(1).equals(user.getStatus())))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0052");
      names.add(user == null ? prior.orElseThrow().get("name").toString() : user.getDisplayName());
    }
    e.setOwnerIds(ids);
    e.setOwnerUserId(
        ids.isEmpty() ? null : ids.getFirst()); // Compatibility for older read-only integrations.
    e.setOwnerName(String.join("、", names));
  }

  private void saveOwners(Equipment e) {
    var prior = ownerRows(e.getId());
    repository.deleteOwners(auth.tenantId(), e.getId());
    var ids = e.getOwnerIds();
    if (ids == null || ids.isEmpty()) return;
    for (int i = 0; i < ids.size(); i++) {
      var user = repository.user(auth.tenantId(), ids.get(i));
      Long ownerId = ids.get(i);
      String name =
          user == null
              ? prior.stream()
                  .filter(o -> ownerId.toString().equals(o.get("id")))
                  .findFirst()
                  .orElseThrow()
                  .get("name")
                  .toString()
              : user.getDisplayName();
      repository.addOwner(auth.tenantId(), e.getId(), ids.get(i), name, i);
    }
  }

  private String jsonString(JsonNode node) {
    try {
      return node == null || node.isNull() ? "[]" : objectMapper.writeValueAsString(node);
    } catch (Exception ignored) {
      return "[]";
    }
  }

  private com.windasoft.eam.dto.equipment.EquipmentView toView(Equipment e) {
    return queries.view(e);
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }
}
