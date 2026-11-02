package com.windasoft.eam.service.equipment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.windasoft.eam.common.FieldValidationException;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.dto.equipment.EquipmentBomMaterialQuery;
import com.windasoft.eam.dto.equipment.EquipmentBomMaterials;
import com.windasoft.eam.entity.basedata.BaseItem;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.equipment.EquipmentBom;
import com.windasoft.eam.entity.equipment.EquipmentChild;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.repository.equipment.EquipmentBomRepository;
import com.windasoft.eam.repository.equipment.EquipmentChildRepository;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.equipment.EquipmentRelationLegacyRepository;
import com.windasoft.eam.repository.equipment.EquipmentRelationsRepository;
import com.windasoft.eam.repository.inventory.MaterialRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.service.inventory.InventoryMutex;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 设备关联用例：读取并装配子设备、BOM及历史关系，维护时检查关联对象和变更限制。 */
@Service
public class EquipmentRelationsService {
  // 沿用设备列表和导出关联读取的500条IN分批，避免随设备数量增长参数规模。
  private static final int RELATION_READ_BATCH_SIZE = 500;
  private final BaseItemRepository baseItems;
  final EquipmentRelationsRepository repository;
  private final EquipmentChildRepository childRepository;
  private final EquipmentQueryRepository queryRepository;
  final AuthContext auth;
  final PermissionService permissions;
  final ObjectMapper json;
  final InventoryMutex inventoryMutex;
  private final OrgUnitRepository organizations;
  private final EquipmentBomRepository equipmentBomRepository;
  private final EquipmentRelationLegacyRepository relationLegacyRepository;
  private final MaterialRepository materialRepository;

  public EquipmentRelationsService(
      EquipmentRelationsRepository repository,
      EquipmentChildRepository childRepository,
      EquipmentQueryRepository queryRepository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json,
      InventoryMutex inventoryMutex,
      OrgUnitRepository organizations,
      EquipmentBomRepository equipmentBomRepository,
      EquipmentRelationLegacyRepository relationLegacyRepository,
      MaterialRepository materialRepository,
      BaseItemRepository baseItems) {
    this.baseItems = baseItems;
    this.repository = repository;
    this.childRepository = childRepository;
    this.queryRepository = queryRepository;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
    this.inventoryMutex = inventoryMutex;
    this.organizations = organizations;
    this.equipmentBomRepository = equipmentBomRepository;
    this.relationLegacyRepository = relationLegacyRepository;
    this.materialRepository = materialRepository;
  }

  public void enter(Collection<Long> companies) {
    inventoryMutex.enter();
    companies.stream()
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .forEach(
            id -> {
              if (!organizations.lockExisting(auth.tenantId(), id)) {
                throw new LocalizedArgumentException("server.equipment.message0010");
              }
            });
  }

  Equipment device(long id) {
    var e = queryRepository.find(auth.tenantId(), id);
    if (e == null)
      throw new LocalizedArgumentException("server.common.message0064");
    return e;
  }

  /** 变更设备归属前检查现有关联约束，避免移动后留下不一致的业务关系。 */
  public void checkMove(Equipment old, Equipment target) {
    if (!Objects.equals(old.getCompanyOrgId(), target.getCompanyOrgId())
        && childRepository.countRelations(auth.tenantId(), old.getId()) > 0)
      throw new LocalizedArgumentException("server.equipment.message0011");
  }

  /** 删除设备前检查业务关联；不能仅因为设备主体可删除就级联删除其他领域数据。 */
  public void checkDelete(long id) {
    if (childRepository.countRelations(auth.tenantId(), id) > 0)
      throw new LocalizedArgumentException("server.equipment.message0012");
  }

  public void cleanup(long id) {
    equipmentBomRepository.clear(auth.tenantId(), id);
    relationLegacyRepository.clear(auth.tenantId(), id);
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
        var rows = childRepository.parents(auth.tenantId(), next);
        if (rows.isEmpty()) break;
        next = rows.getFirst().getParentId();
        if (!excluded.add(next))
          throw new LocalizedArgumentException(
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

    long total = materialRepository.countEnabledCandidates(auth.tenantId(), query.keyword());
    var rows = materialRepository.enabledCandidates(auth.tenantId(), query.keyword(), query.page(), query.size());
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
      throw new LocalizedArgumentException("server.equipment.message0014");
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
      throw new LocalizedArgumentException("server.inventory.message0024");
    }
  }

  void legacySelection(long equipmentId, String kind, List<JsonNode> nodes) {
    var current = relationLegacyRepository.list(auth.tenantId(), equipmentId, kind);
    var retained = new HashSet<Integer>();
    for (var n : nodes) {
      if (!n.path("legacy").asBoolean()) continue;
      int index = n.path("legacyIndex").asInt(-1);
      var match =
          current.stream()
              .filter(r -> r.getSourceIndex() == index)
              .findFirst();
      if (match.isEmpty())
        throw new LocalizedArgumentException(
            "server.equipment.message0015");
      try {
        var snapshot = json.readTree(match.get().getSnapshotJson());
        var submitted = ((ObjectNode) n).deepCopy();
        submitted.remove(List.of("legacy", "legacyIndex", "legacyReason"));
        if (!submitted.equals(snapshot))
          throw new LocalizedArgumentException(
              "server.equipment.message0016");
      } catch (IOException ex) {
        throw new LocalizedArgumentException(
            "server.equipment.message0017");
      }
      retained.add(index);
    }
    for (var r : current)
      if (!retained.contains(r.getSourceIndex()))
        relationLegacyRepository.remove(auth.tenantId(), equipmentId, kind, r.getSourceIndex());
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
        throw new LocalizedArgumentException(
            "server.equipment.message0018");
      if (id == parent.getId())
        throw new LocalizedArgumentException(
            "server.equipment.message0019");
      var child = device(id);
      permissions.checkEquipment(child, "查看");
      permissions.checkEquipment(child, "编辑");
      if (!Objects.equals(parent.getCompanyOrgId(), child.getCompanyOrgId()))
        throw new LocalizedArgumentException(
            "server.equipment.message0020");
      var current = childRepository.parents(auth.tenantId(), id);
      if (!current.isEmpty()
          && !Objects.equals(current.getFirst().getParentId(), parent.getId()))
        throw new LocalizedArgumentException(
            "server.equipment.message0021");
      Long ancestor = parent.getId();
      var seen = new HashSet<Long>();
      while (ancestor != null) {
        if (ancestor == id || !seen.add(ancestor))
          throw new LocalizedArgumentException(
              "server.equipment.message0022");
        var rows = childRepository.parents(auth.tenantId(), ancestor);
        ancestor = rows.isEmpty() ? null : rows.getFirst().getParentId();
      }
      links.add(child);
    }
    var materials = new ArrayList<EquipmentBom>();
    int index = 0;
    for (var n : bomNodes) {
      int lineIndex = index++;
      if (n.path("legacy").asBoolean()) continue;
      long id = id(n, "materialId");
      if (!materialIds.add(id))
        throw new LocalizedArgumentException(
            "server.equipment.message0023");
      var rows = materialRepository.lockMaterial(auth.tenantId(), id);
      if (rows.isEmpty())
        throw new LocalizedArgumentException(
            "server.equipment.message0024");
      var m = rows.getFirst();
      boolean existing =equipmentBomRepository.count(auth.tenantId(), parent.getId(), id) > 0;
      if (((Number) m.get("enabled")).intValue() != 1 && !existing)
        throw new LocalizedArgumentException(
            "server.equipment.message0025");
      BigDecimal qty;
      String field = "relatedSpares." + lineIndex + ".quantity";
      try {
        qty = new BigDecimal(n.path("quantity").asText());
      } catch (Exception ex) {
        throw new FieldValidationException(field, "请输入有效数量");
      }
      if (qty.signum() <= 0)
        throw new FieldValidationException(field, "数量须大于0");
      try {
        qty = qty.setScale(2, RoundingMode.UNNECESSARY);
      } catch (ArithmeticException ex) {
        throw new FieldValidationException(field, "最多两位小数");
      }
      if (qty.precision() > 18)
        throw new FieldValidationException(field, "数量超出范围");
      materials.add(
          new EquipmentBom(
              auth.tenantId(),
              parent.getId(),
              id,
              qty,
              (String) m.get("code"),
              (String) m.get("name"),
              (String) m.get("model"),
              (String) m.get("unit"),
              materials.size()));
    }
    if (replaceChildren) {
      legacySelection(parent.getId(), "child", childNodes);
      childRepository.clear(auth.tenantId(), parent.getId());
      var childRelations = new ArrayList<EquipmentChild>();
      for (int i = 0; i < links.size(); i++) {
        childRelations.add(
            new EquipmentChild(
                auth.tenantId(), parent.getId(), links.get(i).getId(), i));
      }
      childRepository.addBatch(childRelations);
    }
    if (replaceBom) {
      legacySelection(parent.getId(), "bom", bomNodes);
      equipmentBomRepository.clear(auth.tenantId(), parent.getId());
      equipmentBomRepository.addBatch(materials);
    }
  }

  /**
   * 导入追加的子设备已在同一持锁事务内创建。排序从现有新旧关系总数之后延续，不能只按本次新增数量起算；
   * 归属一致性及已有父设备仍需复核，避免追加后形成跨公司关系或重复父级。
   */
  public void appendImportedChildren(Map<Equipment, List<Equipment>> groups) {
    if (!TransactionSynchronizationManager
        .isActualTransactionActive()) throw new IllegalStateException("设备关系必须在事务中执行");
    var additions = new ArrayList<EquipmentChild>();
    for (var entry : groups.entrySet()) {
      var parent = entry.getKey();
      int sort = children(parent.getId()).size();
      for (var child : entry.getValue()) {
        if (parent.getId().equals(child.getId())
            || !Objects.equals(parent.getCompanyOrgId(), child.getCompanyOrgId()))
          throw new LocalizedArgumentException(
              "server.equipment.message0026");
        if (!childRepository.parents(auth.tenantId(), child.getId()).isEmpty())
          throw new LocalizedArgumentException(
              "server.equipment.message0027");
        additions.add(
            new EquipmentChild(
                auth.tenantId(), parent.getId(), child.getId(), sort++));
      }
    }
    childRepository.addBatch(additions);
  }

  public record PageRelations(
      Map<Long, BaseItem> dictionary,
      Map<Long, List<Map<String, Object>>> children,
      Map<Long, List<Map<String, Object>>> bom) {}

  /**
   * 为已授权的设备页一次加载字典、子设备和BOM，不在逐行映射时重复读取。 新关系在前、历史快照在后，保留历史未知字段和原列表顺序；这里只装配，不授予设备权限。
   * 列表、导出及详情的对象授权仍由各调用入口负责。
   */
  public PageRelations pageRelations(List<Equipment> rows) {
    long tenant = auth.tenantId();
    Map<Long, BaseItem> dictionary = new HashMap<>();
    var children = new HashMap<Long, List<Map<String, Object>>>();
    var bom = new HashMap<Long, List<Map<String, Object>>>();
    if (rows.isEmpty()) return new PageRelations(dictionary, children, bom);
    var ids = rows.stream().map(Equipment::getId).toList();
    var pending = new HashSet<Long>();
    for (var row : rows)
      for (Long id :
          Arrays.asList(
              row.getCategoryId(),
              row.getUseStatusId(),
              row.getUnitId(),
              row.getSourceId(),
              row.getFunctionLocationId())) if (id != null) pending.add(id);
    dictionary = baseItems.findWithAncestors(tenant, pending);
    for (int start = 0; start < ids.size(); start += RELATION_READ_BATCH_SIZE) {
      var batch = ids.subList(start, Math.min(start + RELATION_READ_BATCH_SIZE, ids.size()));
      group(children, childRepository.viewRows(tenant, batch));
      group(bom, equipmentBomRepository.viewRows(tenant, batch));
      for (var row : relationLegacyRepository.list(tenant, batch)) {
        var target = row.getKind().equals("child") ? children : bom;
        target
            .computeIfAbsent(row.getEquipmentId(), key -> new ArrayList<>())
            .add(relationLegacyRepository.view(row));
      }
    }
    return new PageRelations(dictionary, children, bom);
  }

  // __equipment_id仅是Repository行投影的分组键，响应中移除；历史快照的未知字段原样保留。
  private static void group(
      Map<Long, List<Map<String, Object>>> target, List<Map<String, Object>> rows) {
    for (var row : rows) {
      var value = new LinkedHashMap<>(row);
      long id = ((Number) value.remove("__equipment_id")).longValue();
      target.computeIfAbsent(id, key -> new ArrayList<>()).add(value);
    }
  }

  public List<Map<String, Object>> children(long id) {
    long tenant = auth.tenantId();
    var grouped = new HashMap<Long, List<Map<String, Object>>>();
    group(grouped, childRepository.viewRows(tenant, List.of(id)));
    var result = new ArrayList<>(grouped.getOrDefault(id, List.of()));
    appendLegacy(result, tenant, id, "child");
    return result;
  }

  public List<Map<String, Object>> bom(long id) {
    long tenant = auth.tenantId();
    var grouped = new HashMap<Long, List<Map<String, Object>>>();
    group(grouped, equipmentBomRepository.viewRows(tenant, List.of(id)));
    var result = new ArrayList<>(grouped.getOrDefault(id, List.of()));
    appendLegacy(result, tenant, id, "bom");
    return result;
  }

  private void appendLegacy(List<Map<String, Object>> result, long tenant, long id, String kind) {
    for (var row : relationLegacyRepository.list(tenant, id, kind))
      result.add(relationLegacyRepository.view(row));
  }
}
