package com.windasoft.eam.service.equipment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.equipment.EquipmentHistoryView;
import com.windasoft.eam.dto.equipment.EquipmentListQuery;
import com.windasoft.eam.dto.equipment.EquipmentNavigationNode;
import com.windasoft.eam.dto.equipment.EquipmentOptions;
import com.windasoft.eam.dto.equipment.EquipmentOwnerView;
import com.windasoft.eam.dto.equipment.EquipmentPage;
import com.windasoft.eam.dto.equipment.EquipmentView;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class EquipmentQueryService {
  private final EquipmentQueryRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper objectMapper;

  public EquipmentQueryService(
      EquipmentQueryRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.objectMapper = objectMapper;
  }

  public EquipmentPage list(EquipmentListQuery request) {
    var result = repository.list(auth.tenantId(), request, permissions::filterEquipment);
    return new EquipmentPage(
        result.getRecords().stream().map(this::view).toList(),
        new EquipmentPage.PageInfo(result.getCurrent(), result.getSize(), result.getTotal()));
  }

  public EquipmentView get(long id) {
    var e = require(id);
    permissions.checkEquipment(e, "查看");
    return view(e);
  }

  public byte[] qrCode(long id) throws Exception {
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
    return bytes.toByteArray();
  }

  public List<EquipmentNavigationNode> navigationTree() {
    permissions.require("equipment", "查看");
    var visible = new java.util.HashSet<Long>();
    for (var e : repository.visibleOrganizations(auth.tenantId(), permissions::filterEquipment)) {
      if (e.getCompanyOrgId() != null) visible.add(e.getCompanyOrgId());
      if (e.getDepartmentOrgId() != null) visible.add(e.getDepartmentOrgId());
    }
    var all = repository.organizations(auth.tenantId(), false);
    boolean changed;
    do {
      changed = false;
      for (var o : all)
        if (visible.contains(o.getId()) && o.getParentId() != null)
          changed |= visible.add(o.getParentId());
    } while (changed);
    return all.stream()
        .filter(o -> visible.contains(o.getId()))
        .map(
            o ->
                new EquipmentNavigationNode(
                    String.valueOf(o.getId()),
                    o.getName(),
                    o.getParentId() == null ? "" : String.valueOf(o.getParentId()),
                    o.getOrgType()))
        .toList();
  }

  public EquipmentOptions batchOptions(String action) {
    if (!List.of("新增", "编辑").contains(action)) throw new IllegalArgumentException("无效的操作");
    permissions.require("equipment", action);
    var allOrgs = repository.organizations(auth.tenantId(), true);
    var selectable =
        allOrgs.stream()
            .filter(
                o ->
                    "部门".equals(o.getOrgType())
                        && permissions.equipmentDepartmentAllowed(o.getId(), action))
            .map(com.windasoft.eam.entity.system.OrgUnit::getId)
            .collect(java.util.stream.Collectors.toSet());
    var visible = new java.util.HashSet<Long>(selectable);
    var orgMap =
        allOrgs.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    com.windasoft.eam.entity.system.OrgUnit::getId, o -> o));
    for (Long id : selectable) {
      var node = orgMap.get(id);
      var visited = new java.util.HashSet<Long>();
      while (node != null && visited.add(node.getId())) {
        visible.add(node.getId());
        node = orgMap.get(node.getParentId());
      }
    }
    var departments =
        repository.organizations(auth.tenantId(), true).stream()
            .filter(o -> visible.contains(o.getId()))
            .map(
                o ->
                    new EquipmentOptions.Department(
                        !selectable.contains(o.getId()),
                        String.valueOf(o.getId()),
                        o.getName(),
                        o.getDisplayType() == null ? o.getOrgType() : o.getDisplayType(),
                        o.getParentId() == null ? "" : String.valueOf(o.getParentId())))
            .toList();
    var employees =
        repository.employees(auth.tenantId()).stream()
            .map(
                u ->
                    new EquipmentOptions.Employee(
                        String.valueOf(u.getId()), u.getDisplayName(), u.getAccount()))
            .toList();
    return new EquipmentOptions(departments, employees);
  }

  public EquipmentHistoryView history(long id) {
    var equipment = require(id);
    permissions.checkEquipment(equipment, "查看");
    var repairRows = repository.repairs(auth.tenantId(), equipment.getEquipmentCode());
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
                    Map.<String, Object>ofEntries(
                        Map.entry("number", safe(r.getOrderNo())),
                        Map.entry("time", r.getReportedAt() == null ? "" : r.getReportedAt().toString()),
                        Map.entry("description", safe(r.getDescription())),
                        Map.entry("person", safe(r.getAssignee())),
                        Map.entry("status", safe(r.getStatus())),
                        Map.entry("result", safe(r.getRepairAction()))))
            .toList();
    var records = repository.history(auth.tenantId(), id);
    return new EquipmentHistoryView(
        repository.installedSpares(auth.tenantId(), id),
        view(equipment),
        repairHistory,
        records.stream().filter(r -> "MAINTENANCE".equals(r.get("type"))).toList(),
        records.stream().filter(r -> "INSPECTION".equals(r.get("type"))).toList(),
        records.stream().filter(r -> "SPARE_CHANGE".equals(r.get("type"))).toList());
  }

  private Equipment require(long id) {
    var e = repository.find(auth.tenantId(), id);
    if (e == null) throw new IllegalArgumentException("设备不存在");
    return e;
  }

  private String locationPath(Long id, Long company, boolean required) {
    if (id == null) {
      if (required) throw new IllegalArgumentException("请选择位置");
      return null;
    }
    var parts = new java.util.ArrayList<String>();
    var visited = new java.util.HashSet<Long>();
    var node = repository.baseItem(id);
    if (node == null) {
      if (required) throw new IllegalArgumentException("位置不存在");
      return null;
    }
    while (node != null) {
      if (!visited.add(node.getId())) throw new IllegalArgumentException("位置树结构异常");
      if (!node.getTenantId().equals(auth.tenantId())
          || !"LOCATION".equals(node.getItemType())
          || !java.util.Objects.equals(node.getCompanyOrgId(), company))
        throw new IllegalArgumentException("位置不属于设备所属公司");
      if (required && node.getId().equals(id) && !Integer.valueOf(1).equals(node.getStatus()))
        throw new IllegalArgumentException("位置已停用");
      parts.addFirst(node.getName());
      node = node.getParentId() == null ? null : repository.baseItem(node.getParentId());
    }
    return String.join(" · ", parts);
  }

  private String currentBaseName(Long id, String type, String snapshot) {
    if (id == null) return safe(snapshot);
    var node = repository.baseItem(id);
    return node != null
            && java.util.Objects.equals(node.getTenantId(), auth.tenantId())
            && type.equals(node.getItemType())
        ? node.getName()
        : safe(snapshot);
  }

  private static String date(LocalDate d) {
    return d == null ? "" : d.toString();
  }

  private JsonNode json(String value) {
    try {
      return value == null ? objectMapper.createArrayNode() : objectMapper.readTree(value);
    } catch (Exception ignored) {
      return objectMapper.createArrayNode();
    }
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  public EquipmentView view(Equipment e) {
    var owners = repository.owners(auth.tenantId(), e.getId());
    return new EquipmentView(
        String.valueOf(e.getId()),
        safe(e.getEquipmentCode()),
        safe(e.getName()),
        currentBaseName(e.getCategoryId(), "CATEGORY", e.getCategory()),
        e.getCategoryId() == null ? "" : String.valueOf(e.getCategoryId()),
        e.getUnitId() == null ? "" : String.valueOf(e.getUnitId()),
        e.getUseStatusId() == null ? "" : String.valueOf(e.getUseStatusId()),
        e.getSourceId() == null ? "" : String.valueOf(e.getSourceId()),
        owners,
        owners.stream().map(EquipmentOwnerView::id).toList(),
        e.getOwnerUserId() == null ? "" : String.valueOf(e.getOwnerUserId()),
        safe(e.getDepartmentPath()),
        safe(e.getModel()),
        safe(e.getSerialNo()),
        safe(e.getCompany()),
        e.getCompanyOrgId() == null ? "" : String.valueOf(e.getCompanyOrgId()),
        safe(e.getDepartment()),
        e.getDepartmentOrgId() == null ? "" : e.getDepartmentOrgId().toString(),
        safe(e.getOwnerName()),
        safe(e.getBrand()),
        safe(e.getEquipmentStatus()),
        currentBaseName(e.getUseStatusId(), "USE_STATUS", e.getUseStatus()),
        currentBaseName(e.getSourceId(), "SOURCE", e.getSource()),
        date(e.getPurchaseDate() != null ? e.getPurchaseDate() : e.getAcquiredAt()),
        e.getAcquiredAmount() == null ? "" : e.getAcquiredAmount().toPlainString(),
        e.getWarrantyUntil() == null ? "" : e.getWarrantyUntil().toString(),
        safe(e.getDescription()),
        e.getRowVersion() == null ? 0 : e.getRowVersion(),
        safe(e.getAssetNo()),
        safe(e.getTagCode()),
        currentBaseName(e.getUnitId(), "UNIT", e.getUnitName()),
        safe(e.getSupplier()),
        date(e.getPurchaseDate()),
        date(e.getInServiceDate()),
        date(e.getExpectedRetirementDate()),
        safe(e.getEquipmentLevel()),
        e.getFunctionLocationId() == null ? "" : String.valueOf(e.getFunctionLocationId()),
        safe(
            e.getFunctionLocationId() == null
                ? e.getFunctionLocation()
                : java.util.Objects.requireNonNullElse(
                    locationPath(e.getFunctionLocationId(), e.getCompanyOrgId(), false),
                    safe(e.getFunctionLocation()))),
        safe(e.getSpecialEquipment()),
        safe(e.getMetrologyEquipment()),
        safe(e.getDepreciationEnabled()),
        safe(e.getTechnicalParams()),
        date(e.getCalibrationDate()),
        date(e.getCalibrationValidUntil()),
        safe(e.getInspectorName()),
        safe(e.getImageName()),
        safe(e.getImageFileId()),
        json(e.getImagesJson()),
        json(e.getDocumentsJson()),
        repository.children(auth.tenantId(), e.getId()),
        repository.bom(auth.tenantId(), e.getId()));
  }
}
