package com.windasoft.eam.service.equipment;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.windasoft.eam.dto.equipment.EquipmentBomMaterialQuery;
import com.windasoft.eam.dto.equipment.EquipmentBomMaterials;
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
              if (rows.isEmpty())
                throw new com.windasoft.eam.common.LocalizedArgumentException(
                    "server.equipment.message0010");
            });
  }

  Equipment device(long id) {
    var e = repository.find(auth.tenantId(), id);
    if (e == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0064");
    return e;
  }

  public void checkMove(Equipment old, Equipment target) {
    if (!Objects.equals(old.getCompanyOrgId(), target.getCompanyOrgId())
        && repository.relationshipCount(auth.tenantId(), old.getId()) > 0)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0011");
  }

  public void checkDelete(long id) {
    if (repository.relationshipCount(auth.tenantId(), id) > 0)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0012");
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
        if (!excluded.add(next))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.equipment.message0013");
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

  public EquipmentBomMaterials materials(EquipmentBomMaterialQuery query) {
    if (!permissions.allowed("equipment", "新增")) {
      permissions.require("equipment", "编辑");
    }
    long total = repository.materialTotal(auth.tenantId(), query);
    var rows = repository.materials(auth.tenantId(), query);
    return new EquipmentBomMaterials(
        rows.stream()
            .map(
                m -> new EquipmentBomMaterials.Material(
                    Long.toString(m.getId()), m.getCode(), m.getName(), m.getModel(),
                    m.getUnit(), m.getCategory(), true))
            .toList(),
        new EquipmentBomMaterials.Page(total));
  }

  List<JsonNode> nodes(JsonNode data) {
    if (data == null || data.isNull()) return List.of();
    if (!data.isArray() || data.size() > 1000)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0014");
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
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.inventory.message0024");
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
      if (match.isEmpty())
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0015");
      try {
        var snapshot = json.readTree(match.get().get("snapshot_json").toString());
        var submitted = ((ObjectNode) n).deepCopy();
        submitted.remove(List.of("legacy", "legacyIndex", "legacyReason"));
        if (!submitted.equals(snapshot))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.equipment.message0016");
      } catch (java.io.IOException ex) {
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0017");
      }
      retained.add(index);
    }
    for (var r : current)
      if (!retained.contains(((Number) r.get("source_index")).intValue()))
        repository.removeLegacy(auth.tenantId(), equipmentId, kind, r.get("source_index"));
  }

  public void save(Equipment parent, JsonNode children, JsonNode bom) {
    save(parent, children, bom, true, true);
  }

  public void saveChildren(Equipment parent, JsonNode children) {
    save(parent, children, null, true, false);
  }

  public void saveBom(Equipment parent, JsonNode bom) {
    save(parent, null, bom, false, true);
  }

  private void save(Equipment parent, JsonNode children, JsonNode bom, boolean replaceChildren, boolean replaceBom) {
    var childNodes = nodes(children);
    var bomNodes = nodes(bom);
    var childIds = new HashSet<Long>();
    var materialIds = new HashSet<Long>();
    var links = new ArrayList<Equipment>();
    for (var n : childNodes) {
      if (n.path("legacy").asBoolean()) continue;
      long id = id(n, "equipmentId");
      if (!childIds.add(id))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0018");
      if (id == parent.getId())
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0019");
      var child = device(id);
      permissions.checkEquipment(child, "查看");
      permissions.checkEquipment(child, "编辑");
      if (!Objects.equals(parent.getCompanyOrgId(), child.getCompanyOrgId()))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0020");
      var current = repository.parents(auth.tenantId(), id);
      if (!current.isEmpty()
          && ((Number) current.getFirst().get("parent_id")).longValue() != parent.getId())
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0021");
      Long ancestor = parent.getId();
      var seen = new HashSet<Long>();
      while (ancestor != null) {
        if (ancestor == id || !seen.add(ancestor))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.equipment.message0022");
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
      if (!materialIds.add(id))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0023");
      var rows = repository.lockMaterial(auth.tenantId(), id);
      if (rows.isEmpty())
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0024");
      var m = rows.getFirst();
      boolean existing = repository.bomCount(auth.tenantId(), parent.getId(), id) > 0;
      if (((Number) m.get("enabled")).intValue() != 1 && !existing)
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0025");
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
    if (replaceChildren) {
      legacySelection(parent.getId(), "child", childNodes);
      repository.clearChildren(auth.tenantId(), parent.getId());
      for (int i = 0; i < links.size(); i++)
        repository.addChild(auth.tenantId(), parent.getId(), links.get(i).getId(), i);
    }
    if (replaceBom) {
      legacySelection(parent.getId(), "bom", bomNodes);
      repository.clearBom(auth.tenantId(), parent.getId());
      for (int i = 0; i < materials.size(); i++) {
        var m = materials.get(i);
        repository.addBom(auth.tenantId(), parent.getId(), m, i);
      }
    }
  }

  /** Import-only append: all child IDs are newly inserted in the same locked transaction. */
  public void appendImportedChildren(Map<Equipment, List<Equipment>> groups) {
    if (!org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive()) throw new IllegalStateException("设备关系必须在事务中执行");
    for (var entry : groups.entrySet()) {
      var parent = entry.getKey();
      int sort = repository.children(auth.tenantId(), parent.getId()).size();
      for (var child : entry.getValue()) {
        if (parent.getId().equals(child.getId())
            || !Objects.equals(parent.getCompanyOrgId(), child.getCompanyOrgId()))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.equipment.message0026");
        if (!repository.parents(auth.tenantId(), child.getId()).isEmpty())
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.equipment.message0027");
        repository.addChild(auth.tenantId(), parent.getId(), child.getId(), sort++);
      }
    }
  }

  public List<Map<String, Object>> children(long id) {
    return repository.children(auth.tenantId(), id);
  }

  public List<Map<String, Object>> bom(long id) {
    return repository.bom(auth.tenantId(), id);
  }
}
