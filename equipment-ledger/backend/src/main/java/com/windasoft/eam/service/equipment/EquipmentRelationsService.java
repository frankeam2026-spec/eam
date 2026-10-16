package com.windasoft.eam.service.equipment;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.equipment.EquipmentRelationsRepository;
import com.windasoft.eam.service.inventory.InventoryMutex;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class EquipmentRelationsService {
  final EquipmentRelationsRepository repository;
  final AuthContext auth;
  final PermissionService permissions;
  final ObjectMapper json;
  final InventoryMutex inventoryMutex;

  public EquipmentRelationsService(
      EquipmentRelationsRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json,
      InventoryMutex inventoryMutex) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
    this.inventoryMutex = inventoryMutex;
  }

  public void enter(Collection<Long> companies) {
    inventoryMutex.enter();
    companies.stream()
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .forEach(
            id -> {
              var rows = repository.lockCompany(auth.tenantId(), id);
              if (rows.isEmpty()) throw new IllegalArgumentException("所属公司不存在");
            });
  }

  Equipment device(long id) {
    var e = repository.find(auth.tenantId(), id);
    if (e == null) throw new IllegalArgumentException("设备不存在");
    return e;
  }

  public void checkMove(Equipment old, Equipment target) {
    if (!Objects.equals(old.getCompanyOrgId(), target.getCompanyOrgId())
        && repository.relationshipCount(auth.tenantId(), old.getId()) > 0)
      throw new IllegalArgumentException("设备存在上下级关联，请先解除关联后变更公司");
  }

  public void checkDelete(long id) {
    if (repository.relationshipCount(auth.tenantId(), id) > 0)
      throw new IllegalArgumentException("设备存在上下级关联，请先移除关联再删除");
  }

  public void cleanup(long id) {
    repository.clearBom(auth.tenantId(), id);
    repository.clearLegacy(auth.tenantId(), id);
  }

  public Object candidates(Long parentId, Long departmentId, String q, int page, int size) {
    Long company;
    if (parentId != null) {
      var parent = device(parentId);
      permissions.checkEquipment(parent, "编辑");
      company = parent.getCompanyOrgId();
      if (departmentId != null) {
        var dept = permissions.requireDepartment(departmentId);
        permissions.checkEquipmentDepartment(dept.getId(), "编辑");
        company = permissions.companyOfOrganization(dept.getId());
        var target = new Equipment();
        target.setCompanyOrgId(company);
        checkMove(parent, target);
      }
    } else {
      permissions.require("equipment", "新增");
      var dept = permissions.requireDepartment(departmentId);
      permissions.checkEquipmentDepartment(dept.getId(), "新增");
      company = permissions.companyOfOrganization(dept.getId());
    }
    var excluded = new HashSet<Long>();
    if (parentId != null) {
      excluded.add(parentId);
      Long next = parentId;
      while (next != null) {
        var rows = repository.parents(auth.tenantId(), next);
        if (rows.isEmpty()) break;
        next = ((Number) rows.getFirst().get("parent_id")).longValue();
        if (!excluded.add(next)) throw new IllegalArgumentException("设备结构异常");
      }
    }
    var result =
        repository.candidates(
            auth.tenantId(),
            company,
            parentId,
            excluded,
            q,
            page,
            size,
            query -> {
              permissions.filterEquipment(query);
              permissions.filterEquipment(query, "编辑");
            });
    return Map.of(
        "data",
        result.getRecords().stream()
            .map(
                e ->
                    Map.of(
                        "id",
                        e.getId().toString(),
                        "equipmentId",
                        e.getId().toString(),
                        "code",
                        Objects.toString(e.getEquipmentCode(), ""),
                        "name",
                        e.getName(),
                        "model",
                        Objects.toString(e.getModel(), ""),
                        "brand",
                        Objects.toString(e.getBrand(), ""),
                        "department",
                        Objects.toString(e.getDepartmentPath(), e.getDepartment())))
            .toList(),
        "page",
        Map.of(
            "total", result.getTotal(), "number", result.getCurrent(), "size", result.getSize()));
  }

  public Object materials(String q, int page, int size) {
    if (!permissions.allowed("equipment", "新增")) permissions.require("equipment", "编辑");
    long total = repository.materialTotal(auth.tenantId(), q);
    var rows = repository.materials(auth.tenantId(), q, page, size);
    return Map.of(
        "data",
        rows.stream()
            .map(
                m -> {
                  m.put("id", m.get("id").toString());
                  m.put("enabled", true);
                  return m;
                })
            .toList(),
        "page",
        Map.of("total", total));
  }

  List<JsonNode> nodes(JsonNode data) {
    if (data == null || data.isNull()) return List.of();
    if (!data.isArray() || data.size() > 1000)
      throw new IllegalArgumentException("关联明细格式不正确，单次最多1000行");
    var out = new ArrayList<JsonNode>();
    data.forEach(out::add);
    return out;
  }

  long id(JsonNode n, String key) {
    try {
      long id = Long.parseLong(n.path(key).asText());
      if (id <= 0) throw new NumberFormatException();
      return id;
    } catch (Exception ex) {
      throw new IllegalArgumentException("请选择有效的关联记录");
    }
  }

  void legacySelection(long equipmentId, String kind, List<JsonNode> nodes) {
    var current = repository.legacy(auth.tenantId(), equipmentId, kind);
    var retained = new HashSet<Integer>();
    for (var n : nodes) {
      if (!n.path("legacy").asBoolean()) continue;
      int index = n.path("legacyIndex").asInt(-1);
      var match =
          current.stream()
              .filter(r -> ((Number) r.get("source_index")).intValue() == index)
              .findFirst();
      if (match.isEmpty()) throw new IllegalArgumentException("历史关联不存在，请刷新");
      try {
        var snapshot = json.readTree(match.get().get("snapshot_json").toString());
        var submitted = ((ObjectNode) n).deepCopy();
        submitted.remove(List.of("legacy", "legacyIndex", "legacyReason"));
        if (!submitted.equals(snapshot)) throw new IllegalArgumentException("历史关联请移除后重新选择，不能手工修改");
      } catch (java.io.IOException ex) {
        throw new IllegalArgumentException("历史关联格式无效");
      }
      retained.add(index);
    }
    for (var r : current)
      if (!retained.contains(((Number) r.get("source_index")).intValue()))
        repository.removeLegacy(auth.tenantId(), equipmentId, kind, r.get("source_index"));
  }

  public void save(Equipment parent, JsonNode children, JsonNode bom) {
    var childNodes = nodes(children);
    var bomNodes = nodes(bom);
    var childIds = new HashSet<Long>();
    var materialIds = new HashSet<Long>();
    var links = new ArrayList<Equipment>();
    for (var n : childNodes) {
      if (n.path("legacy").asBoolean()) continue;
      long id = id(n, "equipmentId");
      if (!childIds.add(id)) throw new IllegalArgumentException("子设备不能重复添加");
      if (id == parent.getId()) throw new IllegalArgumentException("不能将设备自身作为子设备");
      var child = device(id);
      permissions.checkEquipment(child, "查看");
      permissions.checkEquipment(child, "编辑");
      if (!Objects.equals(parent.getCompanyOrgId(), child.getCompanyOrgId()))
        throw new IllegalArgumentException("子设备必须属于同一公司");
      var current = repository.parents(auth.tenantId(), id);
      if (!current.isEmpty()
          && ((Number) current.getFirst().get("parent_id")).longValue() != parent.getId())
        throw new IllegalArgumentException("所选设备已属于其他父设备，请先解除原关联");
      Long ancestor = parent.getId();
      var seen = new HashSet<Long>();
      while (ancestor != null) {
        if (ancestor == id || !seen.add(ancestor)) throw new IllegalArgumentException("不能建立循环设备结构");
        var rows = repository.parents(auth.tenantId(), ancestor);
        ancestor = rows.isEmpty() ? null : ((Number) rows.getFirst().get("parent_id")).longValue();
      }
      links.add(child);
    }
    var materials = new ArrayList<Map<String, Object>>();
    int index = 0;
    for (var n : bomNodes) {
      int lineIndex = index++;
      if (n.path("legacy").asBoolean()) continue;
      long id = id(n, "materialId");
      if (!materialIds.add(id)) throw new IllegalArgumentException("设备BOM不能重复添加同一物料");
      var rows = repository.lockMaterial(auth.tenantId(), id);
      if (rows.isEmpty()) throw new IllegalArgumentException("所选物料不存在");
      var m = rows.getFirst();
      boolean existing = repository.bomCount(auth.tenantId(), parent.getId(), id) > 0;
      if (((Number) m.get("enabled")).intValue() != 1 && !existing)
        throw new IllegalArgumentException("不能添加停用物料");
      BigDecimal qty;
      String field = "relatedSpares." + lineIndex + ".quantity";
      try {
        qty = new BigDecimal(n.path("quantity").asText());
      } catch (Exception ex) {
        throw new com.windasoft.eam.common.FieldValidationException(field, "请输入有效数量");
      }
      if (qty.signum() <= 0)
        throw new com.windasoft.eam.common.FieldValidationException(field, "数量须大于0");
      try {
        qty = qty.setScale(2, RoundingMode.UNNECESSARY);
      } catch (ArithmeticException ex) {
        throw new com.windasoft.eam.common.FieldValidationException(field, "最多两位小数");
      }
      if (qty.precision() > 18)
        throw new com.windasoft.eam.common.FieldValidationException(field, "数量超出范围");
      m.put("quantity", qty);
      materials.add(m);
    }
    legacySelection(parent.getId(), "child", childNodes);
    legacySelection(parent.getId(), "bom", bomNodes);
    repository.clearChildren(auth.tenantId(), parent.getId());
    for (int i = 0; i < links.size(); i++)
      repository.addChild(auth.tenantId(), parent.getId(), links.get(i).getId(), i);
    repository.clearBom(auth.tenantId(), parent.getId());
    for (int i = 0; i < materials.size(); i++) {
      var m = materials.get(i);
      repository.addBom(auth.tenantId(), parent.getId(), m, i);
    }
    // Compatibility snapshots for read-only integrations; canonical relationships live in dedicated
    // tables.
    repository.updateSnapshots(
        auth.tenantId(),
        parent.getId(),
        write(children(parent.getId())),
        write(bom(parent.getId())));
  }

  String write(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalArgumentException("关联数据无法保存");
    }
  }

  public List<Map<String, Object>> children(long id) {
    return repository.children(auth.tenantId(), id);
  }

  public List<Map<String, Object>> bom(long id) {
    return repository.bom(auth.tenantId(), id);
  }
}
